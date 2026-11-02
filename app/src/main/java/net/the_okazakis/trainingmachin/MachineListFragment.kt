package net.the_okazakis.trainingmachin

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.the_okazakis.trainingmachin.databinding.FragmentMachineListBinding
import net.the_okazakis.trainingmachin.databinding.ItemMachineBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MachineListFragment : Fragment() {

    private var _binding: FragmentMachineListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GymViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMachineListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = MachineAdapter { machine ->
            val bundle = Bundle().apply { putInt("machineId", machine.id) }
            findNavController().navigate(R.id.action_machineListFragment_to_machineDetailFragment, bundle)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.machines.collectLatest {
                adapter.submitList(it)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class MachineAdapter(private val onClick: (Machine) -> Unit) : RecyclerView.Adapter<MachineAdapter.ViewHolder>() {
        private var items = listOf<Machine>()

        fun submitList(newItems: List<Machine>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class ViewHolder(val binding: ItemMachineBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemMachineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val machine = items[position]
            holder.binding.textViewName.text = machine.name
            holder.binding.root.setOnClickListener { onClick(machine) }
            holder.binding.root.setOnLongClickListener {
                showMachineOptionsDialog(machine)
                true
            }
        }

        override fun getItemCount() = items.size
    }

    private fun showMachineOptionsDialog(machine: Machine) {
        val options = arrayOf("上へ移動", "下へ移動", "削除")
        AlertDialog.Builder(requireContext())
            .setTitle(machine.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.moveMachineUp(machine)
                    1 -> viewModel.moveMachineDown(machine)
                    2 -> showDeleteConfirmDialog(machine)
                }
            }
            .show()
    }

    private fun showDeleteConfirmDialog(machine: Machine) {
        AlertDialog.Builder(requireContext())
            .setTitle("マシンの削除")
            .setMessage("「${machine.name}」を一覧から削除します。よろしいですか？")
            .setPositiveButton("はい") { _, _ ->
                viewModel.deleteMachine(machine)
                Toast.makeText(context, "マシンを削除しました", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("いいえ", null)
            .show()
    }
}
