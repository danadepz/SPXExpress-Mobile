package com.spx.express.ui.staff

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.spx.express.R
import com.spx.express.data.model.Parcel
import java.util.Locale

class StaffCodRemittanceAdapter(
    private val onConfirmClicked: (Parcel) -> Unit
) : ListAdapter<Parcel, StaffCodRemittanceAdapter.RemittanceViewHolder>(RemittanceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RemittanceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cod_remittance, parent, false)
        return RemittanceViewHolder(view)
    }

    override fun onBindViewHolder(holder: RemittanceViewHolder, position: Int) {
        holder.bind(getItem(position), onConfirmClicked)
    }

    class RemittanceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTracking: TextView = itemView.findViewById(R.id.tvRemitTracking)
        private val tvReceiver: TextView = itemView.findViewById(R.id.tvRemitReceiver)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvRemitAmount)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvRemitStatus)
        private val btnConfirm: Button = itemView.findViewById(R.id.btnConfirmRemittance)

        fun bind(parcel: Parcel, onConfirmClicked: (Parcel) -> Unit) {
            tvTracking.text = parcel.parclTrackingNumber
            tvReceiver.text = "Receiver: ${parcel.parclReceiverName}"
            tvAmount.text = String.format(Locale.US, "₱%.2f COD", parcel.parclCodAmount)
            tvStatus.text = "Rider submitted remittance – awaiting your confirmation"
            btnConfirm.setOnClickListener { onConfirmClicked(parcel) }
        }
    }

    class RemittanceDiffCallback : DiffUtil.ItemCallback<Parcel>() {
        override fun areItemsTheSame(oldItem: Parcel, newItem: Parcel): Boolean =
            oldItem.parclId == newItem.parclId

        override fun areContentsTheSame(oldItem: Parcel, newItem: Parcel): Boolean =
            oldItem == newItem
    }
}
