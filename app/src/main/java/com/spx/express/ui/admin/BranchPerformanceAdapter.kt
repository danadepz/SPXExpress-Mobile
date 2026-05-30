package com.spx.express.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.spx.express.data.model.Branch
import com.spx.express.data.model.Parcel
import com.spx.express.data.model.Staff
import com.spx.express.databinding.ItemBranchPerformanceBinding

class BranchPerformanceAdapter : RecyclerView.Adapter<BranchPerformanceAdapter.PerformanceViewHolder>() {

    private var branches = listOf<Branch>()
    private var staffList = listOf<Staff>()
    private var parcelsList = listOf<Parcel>()

    fun submitData(
        newBranches: List<Branch>,
        newStaff: List<Staff>,
        newParcels: List<Parcel>
    ) {
        branches = newBranches
        staffList = newStaff
        parcelsList = newParcels
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PerformanceViewHolder {
        val binding = ItemBranchPerformanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PerformanceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PerformanceViewHolder, position: Int) {
        holder.bind(branches[position], staffList, parcelsList)
    }

    override fun getItemCount(): Int = branches.size

    class PerformanceViewHolder(private val binding: ItemBranchPerformanceBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            branch: Branch,
            staffs: List<Staff>,
            parcels: List<Parcel>
        ) {
            binding.tvPerfBranchName.text = branch.brnchName
            binding.tvPerfBranchType.text = branch.brnchType?.uppercase() ?: "HUB"
            binding.tvPerfLocation.text = "📍 ${branch.brnchCity ?: "Location Pending"}"

            // Dynamically count staff assigned to this branch
            val staffStrength = staffs.count { it.stfBrchId == branch.brnchId }
            binding.tvPerfStaffStrength.text = if (staffStrength == 1) "1 Member" else "$staffStrength Members"

            // Dynamically count parcels currently active or routed through this branch
            val shipmentsCount = parcels.count { 
                it.parclOrigBrchId == branch.brnchId || 
                it.parclDestBrchId == branch.brnchId || 
                it.parclNextHopBrchId == branch.brnchId 
            }
            binding.tvPerfShipmentsCount.text = if (shipmentsCount == 1) "1 Shipment" else "$shipmentsCount Shipments"

            // Branch status remains active as long as retrieved from Firebase
            binding.tvPerfStatus.text = "Active"
        }
    }
}
