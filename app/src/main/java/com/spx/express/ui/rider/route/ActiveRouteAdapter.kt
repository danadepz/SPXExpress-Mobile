package com.spx.express.ui.rider.route

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.spx.express.data.model.Parcel
import com.spx.express.databinding.ItemActiveParcelBinding
import java.util.Locale

class ActiveRouteAdapter(
    private val onTrackClicked: (Parcel) -> Unit,
    private val onDeliveredClicked: ((Parcel) -> Unit)? = null,
    private val onFailedClicked: ((Parcel) -> Unit)? = null,
    private val onHubHandoffClicked: ((Parcel) -> Unit)? = null
) : RecyclerView.Adapter<ActiveRouteAdapter.ParcelViewHolder>() {

    private var items = listOf<Parcel>()
    private var branchesMap = mapOf<Int, String>()

    fun submitList(newItems: List<Parcel>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun setBranchesMap(branches: Map<Int, String>) {
        branchesMap = branches
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParcelViewHolder {
        val binding = ItemActiveParcelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ParcelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ParcelViewHolder, position: Int) {
        holder.bind(items[position], onTrackClicked, onDeliveredClicked, onFailedClicked, onHubHandoffClicked, branchesMap)
    }

    override fun getItemCount(): Int = items.size

    class ParcelViewHolder(private val binding: ItemActiveParcelBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: Parcel,
            onTrackClicked: (Parcel) -> Unit,
            onDeliveredClicked: ((Parcel) -> Unit)?,
            onFailedClicked: ((Parcel) -> Unit)?,
            onHubHandoffClicked: ((Parcel) -> Unit)?,
            branchesMap: Map<Int, String>
        ) {
            binding.tvTrackingNo.text = item.parclTrackingNumber
            binding.tvReceiverName.text = "To: ${item.parclReceiverName}"
            binding.tvReceiverPhone.text = "📞 ${item.parclReceiverPhone}"

            val addressParts = listOfNotNull(
                item.parclReceiverStreet?.takeIf { it.isNotEmpty() },
                item.parclReceiverCity?.takeIf { it.isNotEmpty() },
                item.parclReceiverProvince?.takeIf { it.isNotEmpty() },
                item.parclReceiverPostalCode?.takeIf { it.isNotEmpty() }
            )
            binding.tvAddress.text = if (addressParts.isNotEmpty()) {
                addressParts.joinToString(", ")
            } else {
                item.parclReceiverAddress ?: "Address details pending"
            }

            // COD or shipping cost label
            val codAmt = item.parclCodAmount ?: 0.0
            if (codAmt > 0.0) {
                binding.tvCodAmount.text = "COD: ₱${String.format(Locale.US, "%.2f", codAmt)}"
            } else {
                binding.tvCodAmount.text = "No COD"
            }

            // Weight label
            val weight = item.parclWeightKg ?: 0.0
            binding.tvWeightLabel.text = "Weight: ${String.format(Locale.US, "%.1f", weight)} kg"

            // --- Status chip coloring (read-only label, not a button) ---
            val status = item.parclDeliveryStatus ?: "Unknown"
            binding.tvParcelStatus.text = status

            val statusColor = when (status.lowercase(Locale.US)) {
                "delivered" -> "#10B981"               // green
                "failed delivery", "delivery failed",
                "failed" -> "#EF4444"                  // red
                "out for delivery" -> "#F59E0B"        // amber
                "in transit", "in-transit" -> "#3B82F6" // blue
                "pending drop-off", "pending" -> "#EE4D2D" // spx red
                "payment confirmed" -> "#8B5CF6"       // violet
                else -> "#64748B"                      // slate gray
            }

            val chipDrawable = binding.tvParcelStatus.background as? GradientDrawable
            chipDrawable?.setColor(Color.parseColor(statusColor))

            // Action Buttons — reset Track button appearance each bind to guard against RecyclerView recycling
            binding.btnTrackShipment.backgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor("#64748B"))
            binding.btnTrackShipment.setTextColor(Color.WHITE)
            binding.btnTrackShipment.text = "Track"
            binding.btnTrackShipment.setOnClickListener {
                onTrackClicked(item)
            }

            val isTransitRun = status.equals("In Transit", ignoreCase = true)

            // Dynamic Next Hop Hub Name resolution for transit drivers
            if (isTransitRun) {
                val nextHopId = item.parclNextHopBrchId ?: 0
                val nextHopName = branchesMap[nextHopId] ?: "Branch #$nextHopId"
                binding.tvTransitNextHop.text = "🚚 Next Hub: $nextHopName"
                binding.tvTransitNextHop.visibility = android.view.View.VISIBLE
            } else {
                binding.tvTransitNextHop.visibility = android.view.View.GONE
            }

            if (isTransitRun && onHubHandoffClicked != null) {
                // ── Transit Driver Card: show only the Hub Handoff button on the lower right ──
                binding.llRiderActionsContainer.visibility = android.view.View.VISIBLE
                binding.btnMarkFailed.visibility = android.view.View.GONE
                
                // Align container to the right
                binding.llRiderActionsContainer.gravity = android.view.Gravity.END
                
                binding.btnMarkDelivered.visibility = android.view.View.VISIBLE
                binding.btnMarkDelivered.text = "Confirm Hub Handoff"
                
                // Remove weight and wrap content width
                val params = binding.btnMarkDelivered.layoutParams as android.widget.LinearLayout.LayoutParams
                params.width = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                params.weight = 0f
                binding.btnMarkDelivered.layoutParams = params

                // Amber / orange to distinguish from final-delivery green
                binding.btnMarkDelivered.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#F59E0B"))
                binding.btnMarkDelivered.setTextColor(Color.WHITE)
                binding.btnMarkDelivered.setOnClickListener {
                    onHubHandoffClicked(item)
                }
            } else if (!isTransitRun && (onDeliveredClicked != null || onFailedClicked != null)) {
                // ── Last-Mile Rider Card: keep existing Delivered + Mark Failed ──
                binding.llRiderActionsContainer.visibility = android.view.View.VISIBLE
                
                // Reset container gravity
                binding.llRiderActionsContainer.gravity = android.view.Gravity.CENTER_VERTICAL
                
                // Reset weight and width to match parent weight
                val params = binding.btnMarkDelivered.layoutParams as android.widget.LinearLayout.LayoutParams
                params.width = 0
                params.weight = 1f
                binding.btnMarkDelivered.layoutParams = params

                // Reset button appearance in case view was recycled from a transit card
                binding.btnMarkDelivered.text = "Delivered"
                binding.btnMarkDelivered.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#10B981"))
                binding.btnMarkDelivered.setTextColor(Color.WHITE)

                if (onDeliveredClicked != null) {
                    binding.btnMarkDelivered.visibility = android.view.View.VISIBLE
                    binding.btnMarkDelivered.setOnClickListener { onDeliveredClicked(item) }
                } else {
                    binding.btnMarkDelivered.visibility = android.view.View.GONE
                }

                if (onFailedClicked != null) {
                    binding.btnMarkFailed.visibility = android.view.View.VISIBLE
                    binding.btnMarkFailed.setOnClickListener { onFailedClicked(item) }
                } else {
                    binding.btnMarkFailed.visibility = android.view.View.GONE
                }
            } else {
                // ── No action callbacks (customer / manager view) ──
                binding.llRiderActionsContainer.visibility = android.view.View.GONE
                binding.btnMarkDelivered.visibility = android.view.View.GONE
                binding.btnMarkFailed.visibility = android.view.View.GONE
            }
        }
    }
}
