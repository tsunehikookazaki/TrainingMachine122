package net.the_okazakis.trainingmachin

import android.app.Application
import android.os.Bundle
import android.content.Context
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import androidx.core.net.toUri
import androidx.lifecycle.*
import net.the_okazakis.trainingmachin.databinding.ActivityMainBinding

// DataStore
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val MEMO_KEY = stringPreferencesKey("user_memo")

// ==========================================
// 1. データベース設計
// ==========================================

@Entity(tableName = "machines")
data class Machine(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String,
    val settingLabel1: String? = null,
    val settingLabel2: String? = null,
    val settingLabel3: String? = null,
    val settingLabel4: String? = null,
    val settingLabel5: String? = null,

    val lastSetting1: String? = null,
    val lastSetting2: String? = null,
    val lastSetting3: String? = null,
    val lastSetting4: String? = null,
    val lastSetting5: String? = null,

    val displayOrder: Int = 0
)

@Entity(
    tableName = "training_records",
    foreignKeys = [
        ForeignKey(
            entity = Machine::class,
            parentColumns = ["id"],
            childColumns = ["machineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("machineId")]
)
data class TrainingRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val machineId: Int,
    val timestamp: Long
)

@Dao
interface GymDao {
    @Query("SELECT * FROM machines ORDER BY displayOrder ASC, id ASC")
    fun getAllMachines(): Flow<List<Machine>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMachines(machines: List<Machine>)

    @Query("SELECT * FROM machines WHERE id = :id LIMIT 1")
    suspend fun getMachineById(id: Int): Machine?

    @Update
    suspend fun updateMachine(machine: Machine)

    @Delete
    suspend fun deleteMachine(machine: Machine)

    @Insert
    suspend fun insertRecord(record: TrainingRecord)

    @Query("SELECT * FROM training_records WHERE machineId = :machineId ORDER BY timestamp DESC")
    fun getRecordsForMachine(machineId: Int): Flow<List<TrainingRecord>>

    @Delete
    suspend fun deleteRecord(record: TrainingRecord)

    @Query("DELETE FROM training_records WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOldRecords(cutoffTimestamp: Long)

    @Query("SELECT COUNT(*) FROM training_records WHERE date(timestamp / 1000, 'unixepoch', 'localtime') = :date")
    suspend fun getRecordCountForDate(date: String): Int

    @Query("SELECT COUNT(*) FROM machines")
    suspend fun getMachineCount(): Int

    @Query("DELETE FROM training_records")
    suspend fun deleteAllRecords()
}

@Database(entities = [Machine::class, TrainingRecord::class], version = 2, exportSchema = false)
abstract class GymDatabase : RoomDatabase() {
    abstract fun gymDao(): GymDao
    companion object {
        @Volatile private var INSTANCE: GymDatabase? = null
        fun getDatabase(context: Context): GymDatabase {
            return INSTANCE ?: synchronized(GymDatabase::class) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GymDatabase::class.java,
                    "gym_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// ==========================================
// 2. ViewModel
// ==========================================

class GymViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = GymDatabase.getDatabase(application).gymDao()
    val machines: Flow<List<Machine>> = dao.getAllMachines()

    init {
        cleanUpOldRecords()
        viewModelScope.launch {
            val defaultMachines = listOf(
                Machine(name = "リカンベントウオームアップ", description = "ウオームアップ", settingLabel1 = "負荷", settingLabel2 = "時間", settingLabel3 = "椅子", settingLabel4 = "回転数", lastSetting1 = "10", lastSetting2 = "10",lastSetting3 = "2",lastSetting4 = "60" ),
                Machine(name = "レッグプレス", description = "足、おしり", settingLabel1 = "負荷", settingLabel2 = "椅子位置", settingLabel3 = "回数", settingLabel4 = "セット数",lastSetting1 = "140", lastSetting2 = "ヒザが90度になる位置",lastSetting3 = "15",lastSetting4 = "1"),
                Machine(name = "カーフレイズ", description = "ふくらはぎ", settingLabel1 = "負荷", settingLabel2 = "椅子位置", settingLabel3 = "回数", settingLabel4 = "セット数",lastSetting1 = "140", lastSetting2 = "ヒザが90度になる位置",lastSetting3 = "15",lastSetting4 = "1"),
                Machine(name = "レッグカール", description = "ももうしろ", settingLabel1 = "負荷", settingLabel2 = "椅子位置", settingLabel3 = "ピン位置",settingLabel4 = "回数", settingLabel5 = "セット数",lastSetting1 = "100", lastSetting2 = "2",lastSetting3 = "上から3",lastSetting4 = "15",lastSetting5 = "1"),
                Machine(name = "レッグエクステンション", description = "ももまえ", settingLabel1 = "負荷", settingLabel2 = "椅子位置", settingLabel3 = "ピン位置", settingLabel4 = "回数", settingLabel5 = "セット数",lastSetting1 = "100", lastSetting2 = "2",lastSetting3 = "右から3",lastSetting4 = "15",lastSetting5 = "1"),
                Machine(name = "ラットプルダウン", description = "上半身", settingLabel1 = "負荷", settingLabel2 = "椅子位置", settingLabel3 = "ピン位置", settingLabel4 = "回数", settingLabel5 = "セット数",lastSetting1 = "130", lastSetting2 = "2",lastSetting3 = "上から3",lastSetting4 = "15",lastSetting5 = "1"),
                Machine(name = "ヒップアダクション(インナー)", description = "内もも", settingLabel1 = "負荷", settingLabel2 = "回数", settingLabel3 = "セット数",lastSetting1 = "100",lastSetting2 = "15",lastSetting3 = "1"),
                Machine(name = "ヒップアダクション(アウター)", description = "外もも", settingLabel1 = "負荷",  settingLabel2 = "回数", settingLabel3 = "セット数",lastSetting1 = "100",lastSetting2 = "15",lastSetting3 = "1"),
                Machine(name = "ロワーバック", description = "背筋を鍛える", settingLabel1 = "負荷", settingLabel2 = "椅子位置", settingLabel3 = "回数", settingLabel4 = "セット数",lastSetting1 = "120",lastSetting2 = "2",lastSetting3 = "15",lastSetting4 = "1"),
                Machine(name = "アブドミル", description = "腹筋、腸腰筋", settingLabel1 = "負荷", settingLabel2 = "椅子位置", settingLabel3 = "回数", settingLabel4 = "セット数",lastSetting1 = "60",lastSetting2 = "2",lastSetting3 = "15",lastSetting4 = "1"),
                Machine(name = "バーティカルロー", description = "上半身を鍛える", settingLabel1 = "負荷", settingLabel2 = "椅子位置", settingLabel3 = "ピン位置", settingLabel4 = "回数",settingLabel5 = "セット数",lastSetting1 = "115", lastSetting2 = "6",lastSetting3 = "5",lastSetting4 = "15",lastSetting5 = "1"),
                Machine(name = "バックエクステンション", description = "側体を鍛える\n腰にマット", settingLabel1 = "ピン位置", settingLabel2 = "回数", settingLabel3 = "セット数",lastSetting1 = "3",lastSetting2 = "15",lastSetting3 = "1"),
                Machine(name = "リカンベントトレーニング", description = "脚持久力アップ", settingLabel1 = "負荷", settingLabel2 = "時間", settingLabel3 = "椅子", settingLabel4 = "回転数", lastSetting1 = "11", lastSetting2 = "20",lastSetting3 = "2",lastSetting4 = "70")
            )

            try {
                val currentMachines = dao.getAllMachines().first()
                
                for ((index, default) in defaultMachines.withIndex()) {
                    val existing = currentMachines.find { it.name == default.name }
                    if (existing == null) {
                        // 新規追加
                        dao.insertMachines(listOf(default.copy(displayOrder = index)))
                    } else {
                        // 既存マシンの表示順をコードの順番通りに強制更新
                        // ついでに値が空ならデフォルト値をセット
                        dao.updateMachine(existing.copy(
                            displayOrder = index,
                            lastSetting1 = existing.lastSetting1 ?: default.lastSetting1,
                            lastSetting2 = existing.lastSetting2 ?: default.lastSetting2,
                            lastSetting3 = existing.lastSetting3 ?: default.lastSetting3,
                            lastSetting4 = existing.lastSetting4 ?: default.lastSetting4,
                            lastSetting5 = existing.lastSetting5 ?: default.lastSetting5
                        ))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun moveMachineUp(machine: Machine) {
        viewModelScope.launch {
            val all = dao.getAllMachines().first()
            val index = all.indexOfFirst { it.id == machine.id }
            if (index > 0) {
                val prev = all[index - 1]
                dao.updateMachine(machine.copy(displayOrder = prev.displayOrder))
                dao.updateMachine(prev.copy(displayOrder = machine.displayOrder))
            }
        }
    }

    fun moveMachineDown(machine: Machine) {
        viewModelScope.launch {
            val all = dao.getAllMachines().first()
            val index = all.indexOfFirst { it.id == machine.id }
            if (index != -1 && index < all.size - 1) {
                val next = all[index + 1]
                dao.updateMachine(machine.copy(displayOrder = next.displayOrder))
                dao.updateMachine(next.copy(displayOrder = machine.displayOrder))
            }
        }
    }

    fun addNewMachine(name: String, label1: String, label2: String, label3: String, label4: String, label5: String) {
        viewModelScope.launch {
            dao.insertMachines(listOf(
                Machine(
                    name = name,
                    description = "マシントレーニング",
                    settingLabel1 = label1.ifEmpty { null },
                    settingLabel2 = label2.ifEmpty { null },
                    settingLabel3 = label3.ifEmpty { null },
                    settingLabel4 = label4.ifEmpty { null },
                    settingLabel5 = label5.ifEmpty { null }
                )
            ))
        }
    }

    fun deleteMachine(machine: Machine) {
        viewModelScope.launch {
            dao.deleteMachine(machine)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            updateLetsDoitStatus(today)
        }
    }

    fun deleteRecord(record: TrainingRecord) {
        viewModelScope.launch {
            dao.deleteRecord(record)
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(record.timestamp))
            updateLetsDoitStatus(dateStr)
        }
    }

    private suspend fun updateLetsDoitStatus(dateStr: String) {
        val count = dao.getRecordCountForDate(dateStr)
        val status = if (count > 0) "実施済み" else "未実施"
        withContext(Dispatchers.IO) {
            syncWithLetsDoit(dateStr, status)
        }
    }

    private fun syncWithLetsDoit(dateStr: String, status: String) {
        val appKey = "machine"
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (dateStr == today) {
            val contentUri = "content://net.the_okazakis.letsdoit.stats/stats".toUri()
            try {
                val contentValues = android.content.ContentValues().apply {
                    put("app_key", appKey)
                    put("value", status)
                }
                getApplication<Application>().contentResolver.insert(contentUri, contentValues)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            val dir = File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS),
                "LetsDoit"
            )
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "stats.json")

            val json = if (file.exists()) {
                try { org.json.JSONObject(file.readText()) } catch (_: Exception) { org.json.JSONObject() }
            } else { org.json.JSONObject() }

            val dayData = json.optJSONObject(dateStr) ?: org.json.JSONObject()
            dayData.put(appKey, status)
            if (status == "実施済み") {
                dayData.put("last_update", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))
            }
            json.put(dateStr, dayData)
            file.writeText(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resetAllTrainingData() {
        viewModelScope.launch {
            try {
                dao.deleteAllRecords()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val appKey = "machine"
            val statusValue = "未実施"
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            syncWithLetsDoit(today, statusValue)

            try {
                val dir = File(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS),
                    "LetsDoit"
                )
                val file = File(dir, "stats.json")
                if (file.exists()) {
                    val json = org.json.JSONObject(file.readText())
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val dateKey = keys.next()
                        val dayData = json.optJSONObject(dateKey)
                        dayData?.put(appKey, statusValue)
                    }
                    file.writeText(json.toString())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun cleanUpOldRecords() {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -7)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            dao.deleteOldRecords(calendar.timeInMillis)
        }
    }

    fun saveMachineSettings(machineId: Int, values: List<String>) {
        viewModelScope.launch {
            val currentMachine = getMachine(machineId)
            currentMachine?.let { machine ->
                val updatedMachine = machine.copy(
                    lastSetting1 = values.getOrNull(0) ?: "",
                    lastSetting2 = values.getOrNull(1) ?: "",
                    lastSetting3 = values.getOrNull(2) ?: "",
                    lastSetting4 = values.getOrNull(3) ?: "",
                    lastSetting5 = values.getOrNull(4) ?: ""
                )
                dao.updateMachine(updatedMachine)
            }
        }
    }

    fun saveRecord(machineId: Int) {
        viewModelScope.launch {
            cleanUpOldRecords()
            dao.insertRecord(
                TrainingRecord(
                    machineId = machineId,
                    timestamp = System.currentTimeMillis()
                )
            )

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            withContext(Dispatchers.IO) {
                syncWithLetsDoit(today, "実施済み")
            }
        }
    }

    fun updateMachineDescription(machine: Machine, newDescription: String) {
        viewModelScope.launch { dao.updateMachine(machine.copy(description = newDescription)) }
    }

    suspend fun getMachine(id: Int): Machine? = dao.getMachineById(id)
    fun getRecords(machineId: Int): Flow<List<TrainingRecord>> {
        cleanUpOldRecords()
        return dao.getRecordsForMachine(machineId)
    }
}

// ==========================================
// 3. UI（MainActivity）
// ==========================================

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: GymViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val appBarConfiguration = AppBarConfiguration(navController.graph)
        binding.toolbar.setupWithNavController(navController, appBarConfiguration)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            invalidateOptionsMenu()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val currentDestination = navHostFragment.navController.currentDestination
        if (currentDestination?.id == R.id.machineListFragment) {
            menuInflater.inflate(R.menu.main_menu, menu)
            return true
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add -> {
                showAddMachineDialog()
                true
            }
            R.id.action_delete_all -> {
                showDeleteAllConfirmDialog()
                true
            }
            R.id.action_notes -> {
                findNavController(R.id.nav_host_fragment).navigate(R.id.action_machineListFragment_to_noteListFragment)
                true
            }
            R.id.action_usage -> {
                showUsageDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showUsageDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.usage_title)
            .setMessage(R.string.usage_content)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun showAddMachineDialog() {
        val context = this
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }
        val nameInput = EditText(context).apply { hint = "マシン名" }
        val l1Input = EditText(context).apply { hint = "項目1"; setText("負荷") }
        val l2Input = EditText(context).apply { hint = "項目2" }
        val l3Input = EditText(context).apply { hint = "項目3" }
        val l4Input = EditText(context).apply { hint = "項目4" }
        val l5Input = EditText(context).apply { hint = "項目5" }

        layout.addView(nameInput)
        layout.addView(l1Input)
        layout.addView(l2Input)
        layout.addView(l3Input)
        layout.addView(l4Input)
        layout.addView(l5Input)

        AlertDialog.Builder(context)
            .setTitle("新しいマシンの追加")
            .setView(layout)
            .setPositiveButton("追加") { _, _ ->
                val name = nameInput.text.toString()
                if (name.isNotEmpty()) {
                    viewModel.addNewMachine(
                        name,
                        l1Input.text.toString(),
                        l2Input.text.toString(),
                        l3Input.text.toString(),
                        l4Input.text.toString(),
                        l5Input.text.toString()
                    )
                    Toast.makeText(context, "マシンを追加しました", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showDeleteAllConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("データの完全削除")
            .setMessage("過去の記録も含め、すべてのマシントレーニング「実施記録」を削除します。マシンの項目（負荷・椅子位置など）は消えずに残ります。本当によろしいですか？")
            .setPositiveButton("はい") { _, _ ->
                viewModel.resetAllTrainingData()
                Toast.makeText(this, "すべての実施記録を削除しました", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("いいえ", null)
            .show()
    }
}
