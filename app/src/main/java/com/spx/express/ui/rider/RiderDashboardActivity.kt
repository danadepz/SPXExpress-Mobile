package com.spx.express.ui.rider

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.widget.NestedScrollView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.spx.express.R
import com.spx.express.data.api.RetrofitClient
import com.spx.express.data.storage.SessionManager
import com.spx.express.ui.LoginActivity
import com.spx.express.ui.rider.route.ActiveRouteFragment
import kotlinx.coroutines.launch
import org.mindrot.jbcrypt.BCrypt

class RiderDashboardActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var rightDrawer: LinearLayout

    private lateinit var tvActiveRuns: TextView
    private lateinit var tvCompletedRuns: TextView
    private lateinit var tvDrawerName: TextView
    private lateinit var tvDrawerEmail: TextView

    // Sidebar menu items
    private lateinit var navManifest: TextView
    private lateinit var navRemit: TextView
    private lateinit var navHistory: TextView
    private lateinit var navEditProfile: TextView
    private lateinit var btnDrawerSignOut: Button

    // Profile Settings Form
    private lateinit var settingsFormContainer: NestedScrollView
    private lateinit var riderStatsLayout: LinearLayout
    private lateinit var fragmentContainer: View
    private lateinit var etRiderFname: TextInputEditText
    private lateinit var etRiderLname: TextInputEditText
    private lateinit var etRiderEmail: TextInputEditText
    private lateinit var etRiderPhone: TextInputEditText
    private lateinit var etRiderPassword: TextInputEditText
    private lateinit var btnSaveRiderProfile: Button

    private enum class ViewMode {
        MANIFEST, REMITTANCE, HISTORY, SETTINGS
    }
    private var currentViewMode = ViewMode.MANIFEST

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rider_dashboard)

        sessionManager = SessionManager(this)

        // Setup Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Populate Custom Welcome Header
        findViewById<TextView>(R.id.tvToolbarGreeting).text = "Hello Rider,"
        findViewById<TextView>(R.id.tvToolbarName).text = sessionManager.getUserName() ?: "Delivery Rider"
        findViewById<TextView>(R.id.tvToolbarRole).apply {
            text = "Delivery Rider"
            setTextColor(android.graphics.Color.parseColor("#EA580C")) // Elegant Amber/Orange
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cvToolbarBadge).apply {
            setCardBackgroundColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFF7ED")))
        }

        // Initialize views
        drawerLayout = findViewById(R.id.drawerLayout)
        rightDrawer = findViewById(R.id.rightDrawer)
        tvActiveRuns = findViewById(R.id.tvActiveRuns)
        tvCompletedRuns = findViewById(R.id.tvCompletedRuns)
        tvDrawerName = findViewById(R.id.tvDrawerName)
        tvDrawerEmail = findViewById(R.id.tvDrawerEmail)

        navManifest = findViewById(R.id.navManifest)
        navRemit = findViewById(R.id.navRemit)
        navHistory = findViewById(R.id.navHistory)
        navEditProfile = findViewById(R.id.navEditProfile)
        btnDrawerSignOut = findViewById(R.id.btnDrawerSignOut)

        settingsFormContainer = findViewById(R.id.settingsFormContainer)
        riderStatsLayout = findViewById(R.id.riderStatsLayout)
        fragmentContainer = findViewById(R.id.fragmentContainer)
        etRiderFname = findViewById(R.id.etRiderFname)
        etRiderLname = findViewById(R.id.etRiderLname)
        etRiderEmail = findViewById(R.id.etRiderEmail)
        etRiderPhone = findViewById(R.id.etRiderPhone)
        etRiderPassword = findViewById(R.id.etRiderPassword)
        btnSaveRiderProfile = findViewById(R.id.btnSaveRiderProfile)

        // Bind profile details
        tvDrawerName.text = sessionManager.getUserName() ?: "Delivery Rider"
        tvDrawerEmail.text = sessionManager.getUserEmail() ?: "rider@spx.com"

        setupListeners()
        loadCurrentViewData()
        loadRiderStats()

        // Prompt for logout on back pressed instead of closing directly
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showLogoutConfirmationDialog()
            }
        })
    }

    private fun setupListeners() {
        navManifest.setOnClickListener {
            currentViewMode = ViewMode.MANIFEST
            drawerLayout.closeDrawer(GravityCompat.END)
            loadCurrentViewData()
        }

        navRemit.setOnClickListener {
            currentViewMode = ViewMode.REMITTANCE
            drawerLayout.closeDrawer(GravityCompat.END)
            loadCurrentViewData()
        }

        navHistory.setOnClickListener {
            currentViewMode = ViewMode.HISTORY
            drawerLayout.closeDrawer(GravityCompat.END)
            loadCurrentViewData()
        }

        navEditProfile.setOnClickListener {
            currentViewMode = ViewMode.SETTINGS
            drawerLayout.closeDrawer(GravityCompat.END)
            loadCurrentViewData()
        }

        btnDrawerSignOut.setOnClickListener {
            showLogoutConfirmationDialog()
        }

        btnSaveRiderProfile.setOnClickListener {
            saveRiderProfileChanges()
        }
    }

    override fun onResume() {
        super.onResume()
        loadRiderStats()
    }

    private fun loadCurrentViewData() {
        val tvGreeting = findViewById<TextView>(R.id.tvToolbarGreeting)
        val tvName = findViewById<TextView>(R.id.tvToolbarName)

        if (currentViewMode == ViewMode.SETTINGS) {
            riderStatsLayout.visibility = View.GONE
            fragmentContainer.visibility = View.GONE
            settingsFormContainer.visibility = View.VISIBLE
            tvGreeting?.text = "Rider Settings"
            tvName?.text = "Edit Profile"
            loadRiderProfileDetails()
            return
        }

        riderStatsLayout.visibility = View.VISIBLE
        fragmentContainer.visibility = View.VISIBLE
        settingsFormContainer.visibility = View.GONE

        val fragment = when (currentViewMode) {
            ViewMode.MANIFEST -> {
                tvGreeting?.text = "Hello Rider,"
                tvName?.text = sessionManager.getUserName() ?: "Delivery Rider"
                ActiveRouteFragment()
            }
            ViewMode.HISTORY -> {
                tvGreeting?.text = "Rider History"
                tvName?.text = "Delivery History"
                RiderHistoryFragment()
            }
            else -> {
                tvGreeting?.text = "Rider Remittance"
                tvName?.text = "Cash Remittance"
                RiderRemittanceFragment()
            }
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun loadRiderProfileDetails() {
        val riderEmail = sessionManager.getUserEmail() ?: "rider@spx.com"

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getStaffByEmail(email = "\"$riderEmail\"")
                if (response.isSuccessful) {
                    val staffMap = response.body()
                    if (!staffMap.isNullOrEmpty()) {
                        val rider = staffMap.values.first()
                        etRiderFname.setText(rider.stfFname)
                        etRiderLname.setText(rider.stfLname)
                        etRiderEmail.setText(rider.stfEmail)
                        etRiderPhone.setText(rider.stfContactNumber ?: "")
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@RiderDashboardActivity, "Could not load profile details", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveRiderProfileChanges() {
        val fname = etRiderFname.text?.toString()?.trim() ?: ""
        val lname = etRiderLname.text?.toString()?.trim() ?: ""
        val email = etRiderEmail.text?.toString()?.trim() ?: ""
        val phone = etRiderPhone.text?.toString()?.trim() ?: ""
        val password = etRiderPassword.text?.toString()?.trim() ?: ""

        if (fname.isEmpty() || lname.isEmpty() || email.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (phone.length != 11) {
            etRiderPhone.error = "Contact number must be exactly 11 digits"
            return
        }

        val riderEmail = sessionManager.getUserEmail() ?: email

        lifecycleScope.launch {
            try {
                btnSaveRiderProfile.isEnabled = false
                btnSaveRiderProfile.text = "Saving Changes..."

                val staffSearch = RetrofitClient.instance.getStaffByEmail(email = "\"$riderEmail\"")
                if (staffSearch.isSuccessful) {
                    val staffMap = staffSearch.body()
                    if (!staffMap.isNullOrEmpty()) {
                        val targetKey = staffMap.keys.first()
                        val rider = staffMap.values.first()

                        val updates = mutableMapOf<String, Any>(
                            "Stf_Fname" to fname,
                            "Stf_Lname" to lname,
                            "Stf_Email" to email,
                            "Stf_Contact_Number" to phone
                        )

                        if (password.isNotEmpty()) {
                            val passwordHash = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                BCrypt.hashpw(password, BCrypt.gensalt(10)).replaceFirst("$2a$", "$2y$")
                            }
                            updates["Stf_Password_Hash"] = passwordHash
                        }

                        val updateResponse = RetrofitClient.instance.updateStaffStatus(targetKey, updates)
                        if (updateResponse.isSuccessful) {
                            sessionManager.createLoginSession(
                                userId = rider.stfId,
                                email = email,
                                role = "rider",
                                displayName = "$fname $lname"
                            )

                            findViewById<TextView>(R.id.tvToolbarName)?.text = "$fname $lname"
                            tvDrawerName.text = "$fname $lname"
                            tvDrawerEmail.text = email

                            Toast.makeText(this@RiderDashboardActivity, "✅ Profile updated successfully!", Toast.LENGTH_SHORT).show()

                            currentViewMode = ViewMode.MANIFEST
                            loadCurrentViewData()
                        } else {
                            Toast.makeText(this@RiderDashboardActivity, "Error saving updates: ${updateResponse.code()}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@RiderDashboardActivity, "Connection Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                btnSaveRiderProfile.isEnabled = true
                btnSaveRiderProfile.text = "Save Profile Changes"
            }
        }
    }

    private fun showLogoutConfirmationDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Confirm Logout")
            .setMessage("Are you sure you want to log out of your rider terminal?")
            .setPositiveButton("Logout") { _, _ ->
                sessionManager.clearSession()
                val intent = Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadRiderStats() {
        val riderId = sessionManager.getUserId()
        if (riderId == -1) return

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getAllParcels()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        val parcels = body.values.filterNotNull().filter { it.parclRiderId == riderId }
                        val activeCount = parcels.count { 
                            it.parclDeliveryStatus == "In Transit" || it.parclDeliveryStatus == "Out for Delivery"
                        }
                        val completedCount = parcels.count { 
                            it.parclDeliveryStatus == "Delivered"
                        }

                        tvActiveRuns.text = activeCount.toString()
                        tvCompletedRuns.text = completedCount.toString()
                    } else {
                        tvActiveRuns.text = "0"
                        tvCompletedRuns.text = "0"
                    }
                } else {
                    Toast.makeText(this@RiderDashboardActivity, "Failed to load stats: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@RiderDashboardActivity, "Connection Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        val item = menu.add(0, 100, 0, "Menu")
        item.setIcon(R.drawable.ic_hamburger)
        item.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == 100) {
            if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                drawerLayout.closeDrawer(GravityCompat.END)
            } else {
                drawerLayout.openDrawer(GravityCompat.END)
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
