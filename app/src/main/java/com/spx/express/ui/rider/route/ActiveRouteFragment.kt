package com.spx.express.ui.rider.route

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.spx.express.data.model.Parcel
import com.spx.express.data.storage.SessionManager
import com.spx.express.databinding.FragmentActiveRouteBinding
import com.spx.express.ui.customer.tracking.TrackingActivity

class ActiveRouteFragment : Fragment() {

    private var _binding: FragmentActiveRouteBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: ActiveRouteViewModel
    private lateinit var adapter: ActiveRouteAdapter
    
    private var selectedParcel: Parcel? = null
    private var selectedStatus: String? = null

    // Register hardware camera/gallery photo pickers
    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = result.data?.data
            if (imageUri != null && selectedParcel != null && selectedStatus != null) {
                // Convert URI to base64 or upload path, then trigger dynamic PATCH updates
                viewModel.submitDeliveryUpdate(selectedParcel!!, selectedStatus!!, imageUri)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentActiveRouteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[ActiveRouteViewModel::class.java]

        setupRecyclerView()
        observeViewModel()

        val sessionManager = SessionManager(requireContext())
        val riderId = sessionManager.getUserId()
        viewModel.loadActiveRoutes(if (riderId != -1) riderId else 1)
    }

    private fun setupRecyclerView() {
        adapter = ActiveRouteAdapter(
            onTrackClicked = { parcel ->
                val intent = Intent(requireContext(), TrackingActivity::class.java).apply {
                    putExtra("TRACKING_NUMBER", parcel.parclTrackingNumber)
                    putExtra("PARCEL_ID", parcel.parclId)
                }
                startActivity(intent)
            },
            onDeliveredClicked  = { parcel -> openPhotoPicker(parcel, "Delivered") },
            onFailedClicked     = { parcel -> showFailureRemarkDialog(parcel) },
            onHubHandoffClicked = { parcel -> showHubHandoffConfirmDialog(parcel) }
        )
        binding.rvActiveParcels.layoutManager = LinearLayoutManager(requireContext())
        binding.rvActiveParcels.adapter = adapter
    }

    private fun showHubHandoffConfirmDialog(parcel: com.spx.express.data.model.Parcel) {
        val nextHopId = parcel.parclNextHopBrchId ?: 0
        val nextHopName = viewModel.branchesMap.value?.get(nextHopId) ?: "Branch #$nextHopId"

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("🔄 Confirm Hub Handoff")
            .setMessage(
                "Confirm that you have physically handed off parcel\n\n" +
                "Tracking #: ${parcel.parclTrackingNumber}\n\n" +
                "at the hub **$nextHopName**? This will mark it as received at this branch and remove it from your active route."
            )
            .setPositiveButton("Confirm Handoff") { dialog, _ ->
                viewModel.submitHubHandoff(parcel)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    private fun openPhotoPicker(parcel: Parcel, status: String) {
        selectedParcel = parcel
        selectedStatus = status
        
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        selectImageLauncher.launch(intent)
    }

    private fun showFailureRemarkDialog(parcel: Parcel) {
        val reasons = arrayOf(
            "Recipient refused to accept package",
            "Recipient uncontactable via phone or text message",
            "Incorrect recipient name provided",
            "Courier vehicle breakdown",
            "Extreme weather conditions or natural disasters",
            "Rider ran out of delivery shift time",
            "Package damaged in transit"
        )
        
        val spinner = android.widget.Spinner(requireContext()).apply {
            adapter = android.widget.ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                reasons
            )
            setPadding(16, 16, 16, 16)
        }
        
        val layout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
            
            val label = android.widget.TextView(requireContext()).apply {
                text = "Select a reason for delivery failure:"
                textSize = 14f
                setTextColor(android.graphics.Color.GRAY)
                setPadding(0, 0, 0, 16)
            }
            addView(label)
            addView(spinner)
        }
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("🚫 Delivery Failed")
            .setView(layout)
            .setPositiveButton("Confirm") { dialogInterface, _ ->
                val selectedReason = spinner.selectedItem?.toString() ?: reasons[0]
                viewModel.submitDeliveryFailure(parcel, selectedReason)
                dialogInterface.dismiss()
            }
            .setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun observeViewModel() {
        viewModel.branchesMap.observe(viewLifecycleOwner) { branches ->
            if (branches != null) {
                adapter.setBranchesMap(branches)
            }
        }

        viewModel.routeState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is RouteState.Loading -> binding.swipeRefresh.isRefreshing = true
                is RouteState.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    adapter.submitList(state.activeParcels)
                }
                is RouteState.UpdateComplete -> {
                    Toast.makeText(context, "✅ Update successfully pushed!", Toast.LENGTH_SHORT).show()
                    val sessionManager = SessionManager(requireContext())
                    val riderId = sessionManager.getUserId()
                    viewModel.loadActiveRoutes(if (riderId != -1) riderId else 1)
                }
                is RouteState.Error -> {
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
}
