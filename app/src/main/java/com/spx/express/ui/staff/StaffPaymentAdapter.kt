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
import java.util.Locale

class StaffPaymentAdapter(
    private val onConfirmClicked: (PaymentWithDetails) -> Unit
) : ListAdapter<PaymentWithDetails, StaffPaymentAdapter.PaymentViewHolder>(PaymentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pending_payment, parent, false)
        return PaymentViewHolder(view)
    }

    override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
        holder.bind(getItem(position), onConfirmClicked)
    }

    class PaymentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTracking: TextView = itemView.findViewById(R.id.tvPaymentTracking)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvPaymentAmount)
        private val tvCustomer: TextView = itemView.findViewById(R.id.tvPaymentCustomer)
        private val tvMethod: TextView = itemView.findViewById(R.id.tvPaymentMethod)
        private val tvRef: TextView = itemView.findViewById(R.id.tvPaymentRef)
        private val btnConfirm: Button = itemView.findViewById(R.id.btnConfirmPayment)

        fun bind(item: PaymentWithDetails, onConfirmClicked: (PaymentWithDetails) -> Unit) {
            tvTracking.text = item.parcel.parclTrackingNumber
            tvAmount.text = String.format(Locale.US, "₱%.2f", item.payment.pymtAmount)
            tvCustomer.text = "Customer: ${item.customer.custFname} ${item.customer.custLname}"
            tvMethod.text = "Method: ${item.payment.pymtPaymentMethod} (${item.payment.pymtPaymentStatus})"

            if (item.payment.pymtPaymentMethod == "Cash") {
                tvRef.text = "Direct Counter Cash"
                tvRef.visibility = View.VISIBLE
            } else {
                val refStr = item.payment.pymtPaymentReference
                if (!refStr.isNullOrBlank()) {
                    tvRef.text = "Ref: $refStr"
                    tvRef.visibility = View.VISIBLE
                } else {
                    tvRef.text = "No Reference Code"
                    tvRef.visibility = View.VISIBLE
                }
            }

            btnConfirm.setOnClickListener {
                onConfirmClicked(item)
            }
        }
    }

    class PaymentDiffCallback : DiffUtil.ItemCallback<PaymentWithDetails>() {
        override fun areItemsTheSame(oldItem: PaymentWithDetails, newItem: PaymentWithDetails): Boolean {
            return oldItem.payment.pymtId == newItem.payment.pymtId
        }

        override fun areContentsTheSame(oldItem: PaymentWithDetails, newItem: PaymentWithDetails): Boolean {
            return oldItem.payment == newItem.payment && oldItem.parcel == newItem.parcel
        }
    }
}
