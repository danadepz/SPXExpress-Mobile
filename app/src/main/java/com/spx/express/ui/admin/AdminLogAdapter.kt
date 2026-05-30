package com.spx.express.ui.admin

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.spx.express.data.model.Branch
import com.spx.express.data.model.Parcel
import com.spx.express.data.model.ParcelStatusHistory
import com.spx.express.data.model.Staff
import com.spx.express.databinding.ItemAdminLogBinding
import java.util.Locale

class AdminLogAdapter(
    private val onDetailsClicked: (ParcelStatusHistory, Parcel?, Staff?, Branch?) -> Unit
) : RecyclerView.Adapter<AdminLogAdapter.LogViewHolder>() {

    private var items = listOf<ParcelStatusHistory>()
    private var parcelsList = listOf<Parcel>()
    private var staffList = listOf<Staff>()
    private var branchesList = listOf<Branch>()

    fun submitData(
        newItems: List<ParcelStatusHistory>,
        newParcels: List<Parcel>,
        newStaff: List<Staff>,
        newBranches: List<Branch>
    ) {
        items = newItems
        parcelsList = newParcels
        staffList = newStaff
        branchesList = newBranches
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemAdminLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(items[position], parcelsList, staffList, branchesList, onDetailsClicked)
    }

    override fun getItemCount(): Int = items.size

    class LogViewHolder(private val binding: ItemAdminLogBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: ParcelStatusHistory,
            parcels: List<Parcel>,
            staffs: List<Staff>,
            branches: List<Branch>,
            onDetailsClicked: (ParcelStatusHistory, Parcel?, Staff?, Branch?) -> Unit
        ) {
            // Relational lookup: 1. Tracking Number
            val parcel = parcels.find { it.parclId == item.histParclId }
            binding.tvLogTrackingNo.text = parcel?.parclTrackingNumber ?: "DDX-${item.histParclId}"

            // 2. Timestamp
            binding.tvLogTimestamp.text = item.histTimestamp

            // 3. Status Badge Text
            binding.tvLogStatusBadge.text = item.histStatus

            // 4. Remarks snippet
            binding.tvLogRemarkSnippet.text = item.histRemark ?: item.histLocationNote ?: "Status updated"

            // 5. Personnel Info Join
            val staff = staffs.find { it.stfId == item.histStfId }
            binding.tvLogPersonnel.text = if (staff != null) {
                "${staff.stfFname} ${staff.stfLname} (${staff.stfRole})"
            } else {
                "System Auto"
            }

            // 6. Source Branch Join
            val branchId = staff?.stfBrchId ?: parcel?.parclOrigBrchId
            val branch = branches.find { it.brnchId == branchId }
            binding.tvLogBranch.text = branch?.brnchName ?: "Automated Hub"

            // Status chip coloring logic
            val status = item.histStatus.lowercase(Locale.US)
            val statusColor = when {
                status.contains("delivered") -> "#10B981"               // green
                status.contains("failed") -> "#EF4444"                  // red
                status.contains("out for delivery") -> "#F59E0B"        // amber
                status.contains("transit") -> "#3B82F6"                 // blue
                status.contains("pending") -> "#EE4D2D"                 // spx red
                status.contains("paid") || status.contains("remit") || status.contains("cash") || status.contains("payment") -> "#8B5CF6" // violet
                else -> "#64748B"                                       // slate gray
            }

            val chipDrawable = binding.tvLogStatusBadge.background as? GradientDrawable
            chipDrawable?.setColor(Color.parseColor(statusColor))

            // Action Details callback
            binding.btnViewLogDetails.setOnClickListener {
                onDetailsClicked(item, parcel, staff, branch)
            }
        }
    }
}
