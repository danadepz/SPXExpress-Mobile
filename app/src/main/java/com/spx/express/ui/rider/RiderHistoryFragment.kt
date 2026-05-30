package com.spx.express.ui.rider

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.spx.express.data.model.Parcel
import com.spx.express.data.storage.SessionManager
import com.spx.express.databinding.FragmentRiderHistoryBinding
import com.spx.express.databinding.ItemHistoryParcelBinding

class RiderHistoryFragment : Fragment() {

    private var _binding: FragmentRiderHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: RiderHistoryViewModel
    private lateinit var adapter: HistoryParcelAdapter
    private var riderId: Int = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRiderHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[RiderHistoryViewModel::class.java]

        val sessionManager = SessionManager(requireContext())
        riderId = sessionManager.getUserId()

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        loadData()
    }

    private fun setupRecyclerView() {
        adapter = HistoryParcelAdapter()
        binding.rvHistoryParcels.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistoryParcels.adapter = adapter
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            loadData()
        }
    }

    private fun loadData() {
        if (riderId != -1) {
            viewModel.loadHistoryData(riderId)
        }
    }

    private fun observeViewModel() {
        viewModel.historyState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is HistoryState.Loading -> {
                    binding.swipeRefresh.isRefreshing = true
                }
                is HistoryState.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    adapter.submitList(state.completedParcels)
                    
                    binding.tvHistoryTotalCount.text = "${state.completedParcels.size} Completed Deliveries"
                }
                is HistoryState.Error -> {
                    binding.swipeRefresh.isRefreshing = false
                    Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class HistoryParcelAdapter : RecyclerView.Adapter<HistoryParcelAdapter.ViewHolder>() {

        private var items = listOf<Parcel>()

        fun submitList(newItems: List<Parcel>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemHistoryParcelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            
            holder.binding.tvTrackingNo.text = item.parclTrackingNumber
            holder.binding.tvReceiverName.text = "To: ${item.parclReceiverName}"
            
            // Format dynamic combined address
            val addressParts = listOfNotNull(
                item.parclReceiverStreet?.takeIf { it.isNotEmpty() },
                item.parclReceiverCity?.takeIf { it.isNotEmpty() },
                item.parclReceiverProvince?.takeIf { it.isNotEmpty() },
                item.parclReceiverPostalCode?.takeIf { it.isNotEmpty() }
            )
            holder.binding.tvAddress.text = if (addressParts.isNotEmpty()) {
                addressParts.joinToString(", ")
            } else {
                item.parclReceiverAddress ?: "Address details pending"
            }

            // Set delivery timestamp text
            val timestamp = "Delivered today" // Fallback label
            holder.binding.tvTime.text = timestamp

            // Set Prepaid / COD status color badges
            if (item.parclCodAmount == 0.0) {
                holder.binding.tvPaymentStatus.text = "Prepaid"
                holder.binding.tvPaymentStatus.setBackgroundColor(Color.parseColor("#E6F4EA")) // Light green
                holder.binding.tvPaymentStatus.setTextColor(Color.parseColor("#137333")) // Dark green
            } else {
                holder.binding.tvPaymentStatus.text = String.format("COD Collected: ₱%,.2f", item.parclCodAmount)
                holder.binding.tvPaymentStatus.setBackgroundColor(Color.parseColor("#FCE8E6")) // Light red
                holder.binding.tvPaymentStatus.setTextColor(Color.parseColor("#C5221F")) // Dark red
            }
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(val binding: ItemHistoryParcelBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
