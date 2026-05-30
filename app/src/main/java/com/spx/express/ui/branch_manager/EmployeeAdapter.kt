package com.spx.express.ui.branch_manager

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.spx.express.data.model.Staff
import com.spx.express.databinding.ItemEmployeeBinding

class EmployeeAdapter(
    private val onEditClicked: (Staff) -> Unit
) : RecyclerView.Adapter<EmployeeAdapter.EmployeeViewHolder>() {

    private var items = listOf<Staff>()

    fun submitList(newItems: List<Staff>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmployeeViewHolder {
        val binding = ItemEmployeeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EmployeeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EmployeeViewHolder, position: Int) {
        holder.bind(items[position], onEditClicked)
    }

    override fun getItemCount(): Int = items.size

    class EmployeeViewHolder(private val binding: ItemEmployeeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Staff, onEditClicked: (Staff) -> Unit) {
            val initial = item.stfFname.take(1).uppercase()
            binding.tvEmpInitial.text = initial
            binding.tvEmpName.text = "${item.stfFname} ${item.stfLname}"
            binding.tvEmpEmail.text = item.stfEmail
            binding.tvEmpContact.text = item.stfContactNumber ?: "No contact info registered"
            binding.tvEmpRole.text = item.stfRole.replaceFirstChar { it.uppercase() }

            // Set different badge colors depending on employee role
            if (item.stfRole == "rider") {
                binding.tvEmpRole.setBackgroundColor(android.graphics.Color.parseColor("#FFF1F2"))
                binding.tvEmpRole.setTextColor(android.graphics.Color.parseColor("#E11D48"))
            } else {
                binding.tvEmpRole.setBackgroundColor(android.graphics.Color.parseColor("#EFF6FF"))
                binding.tvEmpRole.setTextColor(android.graphics.Color.parseColor("#2563EB"))
            }

            binding.root.setOnClickListener {
                onEditClicked(item)
            }
        }
    }
}
