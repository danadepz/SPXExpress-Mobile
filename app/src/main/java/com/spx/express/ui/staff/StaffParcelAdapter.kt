package com.spx.express.ui.staff

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.spx.express.data.model.Parcel
import com.spx.express.databinding.ItemStaffParcelBinding

class StaffParcelAdapter(
    private val onAcceptClicked: (Parcel) -> Unit,
    private val onDeclineClicked: (Parcel) -> Unit,
    private val onDispatchClicked: (Parcel) -> Unit,
    private val onDetailsClicked: (Parcel) -> Unit
) : RecyclerView.Adapter<StaffParcelAdapter.ParcelViewHolder>() {

    private var items = listOf<Parcel>()
    private var isInventoryMode = false

    fun submitList(newItems: List<Parcel>, inventoryMode: Boolean) {
        items = newItems
        isInventoryMode = inventoryMode
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParcelViewHolder {
        val binding = ItemStaffParcelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ParcelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ParcelViewHolder, position: Int) {
        holder.bind(items[position], isInventoryMode, onAcceptClicked, onDeclineClicked, onDispatchClicked, onDetailsClicked)
    }

    override fun getItemCount(): Int = items.size

    class ParcelViewHolder(private val binding: ItemStaffParcelBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: Parcel,
            isInventoryMode: Boolean,
            onAcceptClicked: (Parcel) -> Unit,
            onDeclineClicked: (Parcel) -> Unit,
            onDispatchClicked: (Parcel) -> Unit,
            onDetailsClicked: (Parcel) -> Unit
        ) {
            binding.tvTrackingNo.text = item.parclTrackingNumber
            binding.tvWeight.text = "⚖️ Weight: ${item.parclWeightKg} kg"
            binding.tvReceiver.text = "👤 Recipient: ${item.parclReceiverName}"

            // Address format
            val addressParts = listOfNotNull(
                item.parclReceiverStreet,
                item.parclReceiverCity,
                item.parclReceiverProvince,
                item.parclReceiverPostalCode
            )
            val fullAddress = if (addressParts.isNotEmpty()) {
                addressParts.joinToString(", ")
            } else {
                item.parclReceiverAddress ?: "Address details pending"
            }
            binding.tvDestination.text = "📍 To: $fullAddress"

            binding.btnDetails.setOnClickListener { onDetailsClicked(item) }

            // Status badge customization
            binding.tvStatusBadge.text = item.parclDeliveryStatus
            val badgeColor = when (item.parclDeliveryStatus) {
                "Pending Drop-off" -> "#3B82F6" // Blue
                "Received at Branch" -> "#10B981" // Green
                "In Transit" -> "#F59E0B" // Orange / Yellow
                "Out for Delivery" -> "#8B5CF6" // Purple
                "Delivered" -> "#059669" // Dark Green
                "Delivery Failed" -> "#DC2626" // Dark Red
                "Rejected" -> "#94A3B8" // Muted Gray
                else -> "#718096" // Default Gray
            }
            
            val drawable = binding.tvStatusBadge.background as? GradientDrawable
            drawable?.setColor(Color.parseColor(badgeColor))

            // Action section visibility based on tab/mode
            if (isInventoryMode) {
                binding.actionDivider.visibility = View.VISIBLE
                binding.rlActions.visibility = View.VISIBLE
                binding.llQueueActions.visibility = View.GONE
                binding.llInventoryActions.visibility = View.VISIBLE
                
                binding.btnDispatch.setOnClickListener { onDispatchClicked(item) }
            } else {
                // Incoming Queue: only show actions if parcel is still waiting to be accepted/rejected
                val isPending = item.parclDeliveryStatus == "Pending Drop-off" || item.parclDeliveryStatus == "In Transit"
                if (isPending) {
                    binding.actionDivider.visibility = View.VISIBLE
                    binding.rlActions.visibility = View.VISIBLE
                    binding.llQueueActions.visibility = View.VISIBLE
                    binding.llInventoryActions.visibility = View.GONE

                    binding.btnAccept.setOnClickListener { onAcceptClicked(item) }
                    binding.btnDecline.setOnClickListener { onDeclineClicked(item) }
                } else {
                    binding.actionDivider.visibility = View.GONE
                    binding.rlActions.visibility = View.GONE
                }
            }
        }
    }
}
