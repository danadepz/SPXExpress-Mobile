package com.spx.express.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.spx.express.R
import com.spx.express.databinding.ActivityLandingBinding
import com.spx.express.ui.admin.AdminDashboardActivity
import com.spx.express.ui.customer.BookParcelActivity
import com.spx.express.ui.customer.tracking.TrackingActivity
import com.spx.express.ui.rider.RiderDashboardActivity
import com.spx.express.ui.staff.StaffDashboardActivity
import com.spx.express.ui.branch_manager.BranchManagerDashboardActivity
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.snackbar.Snackbar

/**
 * LandingActivity – native Android counterpart of the web `index.php`.
 * Mirrors the design language of the web landing page while staying lightweight.
 *
 * Features:
 *  • Top app bar with logo + Login / Get Started actions.
 *  • Hero carousel (ViewPager2) with dot indicators.
 *  • Elevated "Track Your Parcel" card with a simple input + button.
 *  • Three horizontal service cards (Express Booking, Branch Drop‑off, Pickup Service).
 *  • Inline expandable branch list populated from a mock data source (can be replaced by a real API).
 *  • Dark footer with static company info.
 */
class LandingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLandingBinding
    private val heroImages = listOf(
        R.drawable.spx_express, // placeholder – replace with generated hero assets
        R.drawable.spx_express,
        R.drawable.spx_express
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLandingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTopBar()
        setupHeroCarousel()
        setupTrackingCard()
        setupServiceCards()
        setupBranchList()
    }

    // ---------------------------------------------------------------------
    // 1️⃣ Top Bar – Login & Get Started actions
    // ---------------------------------------------------------------------
    private fun setupTopBar() {
        binding.btnLogin.setOnClickListener {
            // Direct to login screen (existing LoginActivity)
            startActivity(Intent(this, LoginActivity::class.java))
        }
        binding.btnGetStarted.setOnClickListener {
            // Direct to sign up screen
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    // ---------------------------------------------------------------------
    // 2️⃣ Hero Carousel with dot indicators
    // ---------------------------------------------------------------------
    private fun setupHeroCarousel() {
        binding.heroViewPager.adapter = HeroAdapter(heroImages)
        createDots(heroImages.size)
        binding.heroViewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                highlightDot(position)
            }
        })
    }

    private fun createDots(count: Int) {
        binding.dotsLayout.removeAllViews()
        val density = resources.displayMetrics.density
        val size = (8 * density).toInt()
        val margin = (4 * density).toInt()
        repeat(count) { index ->
            val dot = ImageView(this).apply {
                setImageDrawable(
                    ContextCompat.getDrawable(
                        this@LandingActivity,
                        if (index == 0) R.drawable.bg_status_badge else R.drawable.bg_status_chip
                    )
                )
                layoutParams = ViewGroup.MarginLayoutParams(size, size).apply {
                    marginEnd = margin
                }
                alpha = if (index == 0) 1f else 0.4f
                isClickable = true
                setOnClickListener { binding.heroViewPager.setCurrentItem(index, true) }
            }
            binding.dotsLayout.addView(dot)
        }
    }

    private fun highlightDot(position: Int) {
        for (i in 0 until binding.dotsLayout.childCount) {
            val dot = binding.dotsLayout.getChildAt(i) as ImageView
            dot.alpha = if (i == position) 1f else 0.4f
        }
    }

    // ---------------------------------------------------------------------
    // 3️⃣ Tracking Card – simple validation & navigation to TrackingActivity
    // ---------------------------------------------------------------------
    private fun setupTrackingCard() {
        binding.btnTrack.setOnClickListener {
            val input = binding.etTrackingInput.text?.toString()?.trim()
            if (input.isNullOrEmpty()) {
                Snackbar.make(it, "Please enter a tracking code.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Pass the tracking code to TrackingActivity (implementation already exists)
            val intent = Intent(this, TrackingActivity::class.java).apply {
                putExtra("TRACKING_CODE", input)
            }
            startActivity(intent)
        }
    }

    // ---------------------------------------------------------------------
    // 4️⃣ Service Cards – navigation placeholders
    // ---------------------------------------------------------------------
    private fun setupServiceCards() {
        binding.btnBookNow.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        binding.btnFindBranches.setOnClickListener {
            // Show/hide the inline branch list
            if (binding.branchListContainer.visibility == View.GONE) {
                binding.branchListContainer.visibility = View.VISIBLE
            } else {
                binding.branchListContainer.visibility = View.GONE
            }
        }
        // Pickup Service is “Coming Soon” – no action needed.
    }

    // ---------------------------------------------------------------------
    // 5️⃣ Branch List – mock data, RecyclerView with ItemDecoration
    // ---------------------------------------------------------------------
    private fun setupBranchList() {
        // Mock data – replace with real API call if needed.
        val branches = listOf(
            Branch("Ortigas Hub", "Mandaluyong", "Hub"),
            Branch("Alabang Hub", "Muntinlupa", "Hub"),
            Branch("Cebu Hub", "Cebu City", "Hub")
        )
        binding.rvBranches.layoutManager = LinearLayoutManager(this)
        binding.rvBranches.adapter = BranchAdapter(branches)
    }

    // ---------------------------------------------------------------------
    // Simple data class for branch information
    // ---------------------------------------------------------------------
    data class Branch(val name: String, val city: String, val type: String)

    // ---------------------------------------------------------------------
    // ViewPager2 Adapter for hero images (uses simple ImageView binding)
    // ---------------------------------------------------------------------
    private inner class HeroAdapter(private val images: List<Int>) :
        RecyclerView.Adapter<HeroAdapter.HeroViewHolder>() {

        inner class HeroViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val img: ImageView = itemView.findViewById(R.id.imgBanner)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeroViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_hero_banner, parent, false)
            return HeroViewHolder(view)
        }

        override fun onBindViewHolder(holder: HeroViewHolder, position: Int) {
            holder.img.setImageResource(images[position])
        }

        override fun getItemCount(): Int = images.size
    }

    // ---------------------------------------------------------------------
    // RecyclerView Adapter for branch list items
    // ---------------------------------------------------------------------
    private inner class BranchAdapter(private val items: List<Branch>) :
        RecyclerView.Adapter<BranchAdapter.BranchViewHolder>() {

        inner class BranchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name = view.findViewById<android.widget.TextView>(R.id.tvBranchName)
            val city = view.findViewById<android.widget.TextView>(R.id.tvBranchCity)
            val type = view.findViewById<android.widget.TextView>(R.id.tvBranchType)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BranchViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_branch, parent, false)
            return BranchViewHolder(view)
        }

        override fun onBindViewHolder(holder: BranchViewHolder, position: Int) {
            val branch = items[position]
            holder.name.text = branch.name
            holder.city.text = branch.city
            holder.type.text = branch.type
        }

        override fun getItemCount(): Int = items.size
    }
}
