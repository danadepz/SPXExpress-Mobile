package com.spx.express.ui.rider

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.spx.express.data.model.Parcel
import com.spx.express.data.storage.SessionManager
import com.spx.express.databinding.FragmentRiderRemittanceBinding
import com.spx.express.databinding.ItemRemittanceParcelBinding

class RiderRemittanceFragment : Fragment() {

    private var _binding: FragmentRiderRemittanceBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: RiderRemittanceViewModel
    private lateinit var adapter: RemittanceParcelAdapter
    private var riderId: Int = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRiderRemittanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[RiderRemittanceViewModel::class.java]

        val sessionManager = SessionManager(requireContext())
        riderId = sessionManager.getUserId()

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        loadData()
    }

    private fun setupRecyclerView() {
        adapter = RemittanceParcelAdapter()
        binding.rvRemittanceParcels.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRemittanceParcels.adapter = adapter
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            loadData()
        }

        binding.btnSubmitRemittance.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Confirm Remittance")
                .setMessage("Are you sure you want to submit your remittance request for verification?")
                .setPositiveButton("Submit") { _, _ ->
                    viewModel.submitRemittanceRequest()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun loadData() {
        if (riderId != -1) {
            viewModel.loadRemittanceData(riderId)
        }
    }

    private fun observeViewModel() {
        viewModel.remittanceState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is RemittanceState.Loading -> {
                    binding.swipeRefresh.isRefreshing = true
                }
                is RemittanceState.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    adapter.submitList(state.pendingParcels)
                    
                    val totalFormatted = String.format("₱%,.2f", state.totalCash)
                    binding.tvPendingTotal.text = totalFormatted

                    if (state.totalCash > 0) {
                        binding.btnSubmitRemittance.isEnabled = true
                        binding.btnSubmitRemittance.text = "Submit Remittance Request"
                    } else {
                        binding.btnSubmitRemittance.isEnabled = false
                        binding.btnSubmitRemittance.text = "All Settled"
                    }
                }
                is RemittanceState.SubmitComplete -> {
                    binding.swipeRefresh.isRefreshing = false
                    Toast.makeText(context, "✅ Remittance request successfully sent!", Toast.LENGTH_LONG).show()
                    loadData()
                }
                is RemittanceState.Error -> {
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

    // Compact nested adapter for the breakdown items list
    inner class RemittanceParcelAdapter : RecyclerView.Adapter<RemittanceParcelAdapter.ViewHolder>() {

        private var items = listOf<Parcel>()

        fun submitList(newItems: List<Parcel>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemRemittanceParcelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.tvTrackingNo.text = item.parclTrackingNumber
            holder.binding.tvCodAmount.text = String.format("₱%,.2f", item.parclCodAmount)
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(val binding: ItemRemittanceParcelBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
