package com.spx.express.ui.customer.tracking

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.spx.express.databinding.ActivityTrackingBinding

class TrackingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrackingBinding
    private lateinit var viewModel: TrackingViewModel
    private lateinit var adapter: TrackingTimelineAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel = ViewModelProvider(this)[TrackingViewModel::class.java]
        setupRecyclerView()

        val trackingNumber = intent.getStringExtra("TRACKING_NUMBER") ?: ""
        binding.tvTrackingNoHeader.text = trackingNumber

        // Pull tracking data dynamically from Firebase REST APIs
        val parcelId = intent.getIntExtra("PARCEL_ID", -1)
        if (parcelId != -1) {
            viewModel.fetchTrackingHistory(parcelId)
        } else {
            Toast.makeText(this, "Invalid Shipment Selected", Toast.LENGTH_SHORT).show()
            finish()
        }

        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = TrackingTimelineAdapter()
        binding.rvTimeline.layoutManager = LinearLayoutManager(this)
        binding.rvTimeline.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.timelineState.observe(this) { state ->
            when (state) {
                is TrackingState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.rvTimeline.visibility = View.GONE
                }
                is TrackingState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.rvTimeline.visibility = View.VISIBLE
                    adapter.submitList(state.milestones)
                }
                is TrackingState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, state.errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
