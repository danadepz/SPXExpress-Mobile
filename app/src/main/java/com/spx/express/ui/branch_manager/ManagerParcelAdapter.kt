package com.spx.express.ui.branch_manager

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.spx.express.data.model.Parcel
import com.spx.express.databinding.ItemActiveParcelBinding
import java.util.Locale

class ManagerParcelAdapter(
    private val onVerifyClicked: (Parcel) -> Unit
) : RecyclerView.Adapter<ManagerParcelAdapter.ParcelViewHolder>() {

    private var items = listOf<Parcel>()
    private var isRemittanceTab = false

    fun submitList(newItems: List<Parcel>, showRemittance: Boolean) {
        items = newItems
        isRemittanceTab = showRemittance
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParcelViewHolder {
        val binding = ItemActiveParcelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ParcelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ParcelViewHolder, position: Int) {
        holder.bind(items[position], isRemittanceTab, onVerifyClicked)
    }

    override fun getItemCount(): Int = items.size

    class ParcelViewHolder(private val binding: ItemActiveParcelBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: Parcel,
            isRemittanceTab: Boolean,
            onVerifyClicked: (Parcel) -> Unit
        ) {
            // Shipment Details
            binding.tvTrackingNo.text = "Tracking #: ${item.parclTrackingNumber}"
            val weight = item.parclWeightKg ?: 0.0
            binding.tvWeightLabel.text = "Details: Weight ${String.format(Locale.US, "%.1f", weight)} kg"

            // Recipient Information
            binding.tvReceiverName.text = "Recipient: ${item.parclReceiverName}"
            binding.tvReceiverPhone.text = "Phone: ${item.parclReceiverPhone}"
            binding.tvAddress.text = "Address: ${item.parclReceiverAddress ?: "Address details pending"}"

            // COD Balance
            val codAmt = item.parclCodAmount ?: 0.0
            binding.tvCodAmount.text = if (codAmt > 0.0) {
                "COD Balance: ₱${String.format(Locale.US, "%.2f", codAmt)}"
            } else {
                "COD Balance: No COD"
            }

            // Current Status
            val status = item.parclDeliveryStatus ?: "Unknown"
            binding.tvParcelStatus.text = status
            val statusColor = when (status.lowercase(Locale.US)) {
                "delivered" -> "#10B981"
                "failed delivery", "failed" -> "#EF4444"
                "out for delivery" -> "#F59E0B"
                "in transit", "in-transit" -> "#3B82F6"
                "pending drop-off", "pending" -> "#EE4D2D"
                "payment confirmed" -> "#8B5CF6"
                else -> "#64748B"
            }
            val chipDrawable = binding.tvParcelStatus.background as? GradientDrawable
            chipDrawable?.setColor(Color.parseColor(statusColor))

            binding.btnMarkFailed.visibility = View.GONE
            binding.btnMarkDelivered.visibility = View.GONE
            binding.llRiderActionsContainer.visibility = View.GONE

            if (isRemittanceTab) {
                // Remittance tab: style as a distinct financial-action button
                binding.btnTrackShipment.text = "💵 Verify Cash"
                binding.btnTrackShipment.visibility = View.VISIBLE
                binding.btnTrackShipment.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#059669")) // emerald green
                binding.btnTrackShipment.setTextColor(Color.WHITE)
                binding.btnTrackShipment.setOnClickListener { onVerifyClicked(item) }
            } else {
                // Inventory tab: hide the action button — display only is needed
                binding.btnTrackShipment.visibility = View.GONE
                // Reset tint in case view was recycled from remittance tab
                binding.btnTrackShipment.backgroundTintList = null
            }
        }
    }
}
