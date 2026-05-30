package com.spx.express.ui.branch_manager

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.spx.express.data.model.Parcel
import com.spx.express.data.model.ParcelStatusHistory
import com.spx.express.data.model.Staff
import com.spx.express.databinding.ItemBranchLogBinding
import java.util.Locale

class BranchLogAdapter : RecyclerView.Adapter<BranchLogAdapter.BranchLogViewHolder>() {

    private var items = listOf<ParcelStatusHistory>()
    private var parcelsList = listOf<Parcel>()
    private var staffList = listOf<Staff>()

    fun submitList(
        newItems: List<ParcelStatusHistory>,
        newParcels: List<Parcel> = emptyList(),
        newStaff: List<Staff> = emptyList()
    ) {
        items = newItems
        parcelsList = newParcels
        staffList = newStaff
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BranchLogViewHolder {
        val binding = ItemBranchLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BranchLogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BranchLogViewHolder, position: Int) =
        holder.bind(items[position], parcelsList, staffList)

    override fun getItemCount(): Int = items.size

    class BranchLogViewHolder(private val binding: ItemBranchLogBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(
            item: ParcelStatusHistory,
            parcels: List<Parcel>,
            staffs: List<Staff>
        ) {
            val parcel = parcels.find { it.parclId == item.histParclId }
            val staff = staffs.find { it.stfId == item.histStfId }

            // Timestamp
            binding.tvLogTimestamp.text = "Timestamp: ${item.histTimestamp}"

            // Tracking # (Badge)
            binding.tvLogParcelId.text = parcel?.parclTrackingNumber ?: "DDX-${item.histParclId}"

            // Status Update
            binding.tvLogStatus.text = "Status: ${item.histStatus}"

            // Processed By
            val staffName = if (staff != null) {
                "${staff.stfFname} ${staff.stfLname} (${staff.stfRole})"
            } else {
                "System Auto"
            }
            binding.tvLogLocation.text = "Processed By: $staffName"

            // Remarks
            if (!item.histRemark.isNullOrBlank()) {
                binding.tvLogRemark.text = "Remarks: ${item.histRemark}"
                binding.tvLogRemark.visibility = View.VISIBLE
            } else {
                binding.tvLogRemark.visibility = View.GONE
            }

            // Stylize icons and colors dynamically depending on status
            val status = item.histStatus.lowercase(Locale.US)
            val bgTint: String
            val borderStrokeColor: String
            val emoji: String

            when {
                status.contains("arrived") || status.contains("received") || status.contains("hub") -> {
                    emoji = "📥"
                    bgTint = "#FFF5F3"
                    borderStrokeColor = "#FEE2E2"
                }
                status.contains("delivered") -> {
                    emoji = "✅"
                    bgTint = "#F0FDF4"
                    borderStrokeColor = "#DCFCE7"
                }
                status.contains("out for delivery") || status.contains("rider") -> {
                    emoji = "🚚"
                    bgTint = "#FEF3C7"
                    borderStrokeColor = "#FEF08A"
                }
                status.contains("in transit") || status.contains("transit") -> {
                    emoji = "🛣️"
                    bgTint = "#EFF6FF"
                    borderStrokeColor = "#DBEAFE"
                }
                status.contains("failed") -> {
                    emoji = "❌"
                    bgTint = "#FEF2F2"
                    borderStrokeColor = "#FEE2E2"
                }
                status.contains("payment") || status.contains("paid") || status.contains("cash") || status.contains("remit") -> {
                    emoji = "💵"
                    bgTint = "#F5F3FF"
                    borderStrokeColor = "#E9D5FF"
                }
                else -> {
                    emoji = "📝"
                    bgTint = "#F8FAFC"
                    borderStrokeColor = "#E2E8F0"
                }
            }

            binding.tvStatusIcon.text = emoji
            binding.cardStatusIconBg.setCardBackgroundColor(ColorStateList.valueOf(android.graphics.Color.parseColor(bgTint)))
            
            // Stylize left border stroke of card for higher design fidelity
            val parentCard = binding.root as? com.google.android.material.card.MaterialCardView
            parentCard?.strokeColor = android.graphics.Color.parseColor(borderStrokeColor)
        }
    }
}
