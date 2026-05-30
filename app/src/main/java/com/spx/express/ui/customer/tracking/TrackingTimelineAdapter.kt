package com.spx.express.ui.customer.tracking

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.spx.express.R
import com.spx.express.data.model.ParcelStatusHistory
import com.spx.express.databinding.ItemTimelineBinding

class TrackingTimelineAdapter : RecyclerView.Adapter<TrackingTimelineAdapter.TimelineViewHolder>() {

    private var items = listOf<ParcelStatusHistory>()

    fun submitList(newItems: List<ParcelStatusHistory>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimelineViewHolder {
        val binding = ItemTimelineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TimelineViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TimelineViewHolder, position: Int) {
        holder.bind(items[position], position == 0, position == items.size - 1)
    }

    override fun getItemCount(): Int = items.size

    class TimelineViewHolder(private val binding: ItemTimelineBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ParcelStatusHistory, isFirst: Boolean, isLast: Boolean) {
            binding.tvStatusTitle.text = item.histStatus
            binding.tvLocationNote.text = item.histLocationNote ?: "Status updated at terminal"
            binding.tvTimestamp.text = item.histTimestamp

            // Stylize nodes dynamically (highlighting the latest node in active SPX Orange)
            if (isFirst) {
                binding.timelineDot.backgroundTintList = ContextCompat.getColorStateList(itemView.context, R.color.spx_primary)
                binding.tvStatusTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.spx_primary))
                binding.timelineLineTop.visibility = View.INVISIBLE
            } else {
                binding.timelineDot.backgroundTintList = ContextCompat.getColorStateList(itemView.context, R.color.slate_400)
                binding.tvStatusTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.slate_800))
                binding.timelineLineTop.visibility = View.VISIBLE
            }

            if (isLast) {
                binding.timelineLineBottom.visibility = View.INVISIBLE
            } else {
                binding.timelineLineBottom.visibility = View.VISIBLE
            }
        }
    }
}
