package net.the_okazakis.trainingmachin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import net.the_okazakis.trainingmachin.databinding.FragmentNoteListBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class NoteListFragment : Fragment() {

    private var _binding: FragmentNoteListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNoteListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val defaultSketch = """
 シニア（65歳以上）メニュー
ウォーミングアップ（※）
有酸素運動 5分〜10分
ウェイトマシン
レッグプレス（脚・おしり）①
レッグエクステンション（太もも前）③
レッグカール（太もも裏）②
インナーサイ／アウターサイ⑦（内もも・外もも）
ラットプルダウン（背中）⑤
アブドミナル（お腹）⑧
各12〜15回 3set インターバル目安2分

有酸素運動
ストレッチ（※）約20分
（※）は必ず行ってください。慣れてきたら追加してレベルアップしていきましょう☆
※マシン名の横にある番号はマシンに貼っている番号になります
  """.trimIndent() // Shortened for brevity

        viewLifecycleOwner.lifecycleScope.launch {
            val memo = requireContext().dataStore.data.map { it[MEMO_KEY] ?: "" }.first()
            binding.editTextMemo.setText(memo.ifEmpty { defaultSketch })
        }

        binding.buttonSaveMemo.setOnClickListener {
            val memoText = binding.editTextMemo.text.toString()
            viewLifecycleOwner.lifecycleScope.launch {
                requireContext().dataStore.edit { prefs ->
                    prefs[MEMO_KEY] = memoText
                }
                Toast.makeText(context, "保存しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
