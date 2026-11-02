package net.the_okazakis.trainingmachin

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.the_okazakis.trainingmachin.databinding.FragmentMachineDetailBinding
import net.the_okazakis.trainingmachin.databinding.ItemRecordBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MachineDetailFragment : Fragment() {

    private var _binding: FragmentMachineDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GymViewModel by activityViewModels()
    private var machineId: Int = 0
    private var currentMachine: Machine? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMachineDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        machineId = arguments?.getInt("machineId") ?: 0

        val adapter = RecordAdapter()
        binding.recyclerViewRecords.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewRecords.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            val machine = viewModel.getMachine(machineId)
            currentMachine = machine
            machine?.let {
                updateUI(it)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getRecords(machineId).collectLatest {
                adapter.submitList(it)
            }
        }

        binding.buttonEditDescription.setOnClickListener {
            if (binding.editTextDescription.isEnabled) {
                // Save
                val newDesc = binding.editTextDescription.text.toString()
                currentMachine?.let { viewModel.updateMachineDescription(it, newDesc) }
                binding.editTextDescription.isEnabled = false
                binding.buttonEditDescription.text = "編集"
            } else {
                // Edit
                binding.editTextDescription.isEnabled = true
                binding.buttonEditDescription.text = "保存"
            }
        }

        binding.buttonSaveSettings.setOnClickListener {
            val values = listOf(
                binding.editTextVal1.text.toString(),
                binding.editTextVal2.text.toString(),
                binding.editTextVal3.text.toString(),
                binding.editTextVal4.text.toString(),
                binding.editTextVal5.text.toString()
            )
            viewModel.saveMachineSettings(machineId, values)
            Toast.makeText(context, "設定値を保存しました", Toast.LENGTH_SHORT).show()
        }

        binding.buttonRecordTraining.setOnClickListener {
            viewModel.saveRecord(machineId)
            Toast.makeText(context, "汗を拭いて、ウエイトを戻してください！", Toast.LENGTH_SHORT).show()
            view.postDelayed({
                if (isAdded) {
                    findNavController().popBackStack()
                }
            }, 2000)
        }
    }

    private fun updateUI(machine: Machine) {
        (activity as? AppCompatActivity)?.supportActionBar?.title = machine.name
        binding.textViewMachineName.text = machine.name
        binding.editTextDescription.setText(machine.description)
        
        binding.layoutVal1.isVisible = machine.settingLabel1 != null
        binding.layoutVal1.hint = machine.settingLabel1
        binding.editTextVal1.setText(machine.lastSetting1)

        binding.layoutVal2.isVisible = machine.settingLabel2 != null
        binding.layoutVal2.hint = machine.settingLabel2
        binding.editTextVal2.setText(machine.lastSetting2)

        binding.layoutVal3.isVisible = machine.settingLabel3 != null
        binding.layoutVal3.hint = machine.settingLabel3
        binding.editTextVal3.setText(machine.lastSetting3)

        binding.layoutVal4.isVisible = machine.settingLabel4 != null
        binding.layoutVal4.hint = machine.settingLabel4
        binding.editTextVal4.setText(machine.lastSetting4)

        binding.layoutVal5.isVisible = machine.settingLabel5 != null
        binding.layoutVal5.hint = machine.settingLabel5
        binding.editTextVal5.setText(machine.lastSetting5)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class RecordAdapter : RecyclerView.Adapter<RecordAdapter.ViewHolder>() {
        private var items = listOf<TrainingRecord>()
        private val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

        fun submitList(newItems: List<TrainingRecord>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class ViewHolder(val binding: ItemRecordBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val record = items[position]
            holder.binding.textViewTimestamp.text = "実施日時: ${dateFormat.format(Date(record.timestamp))}"

            holder.itemView.setOnLongClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("記録の削除")
                    .setMessage("この実施記録を削除しますか？")
                    .setPositiveButton("削除") { _, _ ->
                        viewModel.deleteRecord(record)
                        Toast.makeText(context, "記録を削除しました", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
                true
            }
        }

        override fun getItemCount() = items.size
    }
}
