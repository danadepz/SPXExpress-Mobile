package com.spx.express.ui.customer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.spx.express.R
import com.spx.express.data.api.RetrofitClient
import com.spx.express.data.model.Parcel
import com.spx.express.data.storage.SessionManager
import com.spx.express.ui.rider.route.ActiveRouteAdapter
import com.spx.express.ui.customer.tracking.TrackingActivity
import kotlinx.coroutines.launch
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.spx.express.data.model.Customer
import com.spx.express.data.model.Booking
import com.spx.express.databinding.DialogEditCustomerProfileBinding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import android.widget.LinearLayout
import android.widget.Button
import com.spx.express.ui.LoginActivity
import java.util.Locale

class CustomerDashboardActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var rvCustomerParcels: RecyclerView
    private lateinit var adapter: ActiveRouteAdapter
    
    private lateinit var tvStatPending: TextView
    private lateinit var tvStatTransit: TextView
    private lateinit var tvStatDelivered: TextView
    private lateinit var fabBookParcel: ExtendedFloatingActionButton

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var rightDrawer: LinearLayout

    // Sidebar items
    private lateinit var navOverview: TextView
    private lateinit var navBookShipment: TextView
    private lateinit var navEditProfile: TextView
    private lateinit var btnDrawerSignOut: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_dashboard)

        sessionManager = SessionManager(this)

        // Setup Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Populate Custom Welcome Header
        findViewById<TextView>(R.id.tvToolbarGreeting).text = "Hello Customer,"
        findViewById<TextView>(R.id.tvToolbarName).text = sessionManager.getUserName() ?: "Valued Customer"
        findViewById<TextView>(R.id.tvToolbarRole).apply {
            text = "Customer"
            setTextColor(android.graphics.Color.parseColor("#DB2777")) // Elegant Pink/Magenta
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cvToolbarBadge).apply {
            setCardBackgroundColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FDF2F8")))
        }

        // Initialize UI Views
        swipeRefresh = findViewById(R.id.swipeRefresh)
        rvCustomerParcels = findViewById(R.id.rvCustomerParcels)
        
        tvStatPending = findViewById(R.id.tvStatPending)
        tvStatTransit = findViewById(R.id.tvStatTransit)
        tvStatDelivered = findViewById(R.id.tvStatDelivered)
        fabBookParcel = findViewById(R.id.fabBookParcel)

        // Initialize Drawer UI
        drawerLayout = findViewById(R.id.drawerLayout)
        rightDrawer = findViewById(R.id.rightDrawer)

        // Drawer profile values
        findViewById<TextView>(R.id.tvDrawerName).text = sessionManager.getUserName() ?: "Customer User"
        findViewById<TextView>(R.id.tvDrawerEmail).text = (sessionManager.getUserName() ?: "Customer")
            .replace(" ", "").lowercase(Locale.US) + "@spx.com"

        // Initialize side items
        navOverview = findViewById(R.id.navOverview)
        navBookShipment = findViewById(R.id.navBookShipment)
        navEditProfile = findViewById(R.id.navEditProfile)
        btnDrawerSignOut = findViewById(R.id.btnDrawerSignOut)

        setupRecyclerView()
        setupDrawerListeners()

        swipeRefresh.setOnRefreshListener {
            loadCustomerParcels()
        }

        fabBookParcel.setOnClickListener {
            checkProfileAndNavigate()
        }

        loadCustomerParcels()

        // Prompt for logout on back pressed instead of closing directly
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showLogoutConfirmationDialog()
            }
        })
    }

    private fun showLogoutConfirmationDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Confirm Logout")
            .setMessage("Are you sure you want to log out of your SPX Express account?")
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

    private fun setupDrawerListeners() {
        navOverview.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
        }

        navBookShipment.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
            checkProfileAndNavigate()
        }

        navEditProfile.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
            fetchCustomerAndShowProfileDialog()
        }

        btnDrawerSignOut.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun fetchCustomerAndShowProfileDialog() {
        val userId = sessionManager.getUserId()
        if (userId == -1) return

        swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getAllCustomers()
                swipeRefresh.isRefreshing = false
                if (response.isSuccessful) {
                    val customersMap = response.body()
                    val customer = customersMap?.values?.find { it.custId == userId }
                    if (customer != null) {
                        showEditProfileDialog(customer)
                    } else {
                        Toast.makeText(this@CustomerDashboardActivity, "Customer profile not found.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@CustomerDashboardActivity, "Failed to load customers: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                swipeRefresh.isRefreshing = false
                Toast.makeText(this@CustomerDashboardActivity, "Error loading profile: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkProfileAndNavigate() {
        val userId = sessionManager.getUserId()
        if (userId == -1) {
            Toast.makeText(this, "Session error, please sign in again.", Toast.LENGTH_SHORT).show()
            return
        }

        swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getAllCustomers()
                swipeRefresh.isRefreshing = false
                if (response.isSuccessful) {
                    val customersMap = response.body()
                    if (customersMap != null) {
                        val customer = customersMap.values.find { it.custId == userId }
                        if (customer != null) {
                             val isAddressIncomplete = customer.custStreet.isNullOrBlank() ||
                                     customer.custCity.isNullOrBlank() ||
                                     customer.custProvince.isNullOrBlank() ||
                                     customer.custPostalCode.isNullOrBlank() ||
                                     !customer.custStreet.contains(", Brgy. ")

                            if (isAddressIncomplete) {
                                MaterialAlertDialogBuilder(this@CustomerDashboardActivity)
                                    .setTitle("Complete Your Saved Address")
                                    .setMessage("To book shipments natively on SPX Express, a valid origin address profile is required. Please update your saved profile address.")
                                    .setPositiveButton("Update Profile") { _, _ ->
                                        showEditProfileDialog(customer)
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            } else {
                                // Address complete: navigate
                                val intent = Intent(this@CustomerDashboardActivity, BookParcelActivity::class.java)
                                startActivity(intent)
                            }
                        } else {
                            Toast.makeText(this@CustomerDashboardActivity, "Profile details not found.", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(this@CustomerDashboardActivity, "Connection failed: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                swipeRefresh.isRefreshing = false
                Toast.makeText(this@CustomerDashboardActivity, "Connection error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showEditProfileDialog(customer: Customer) {
        val dialogBinding = DialogEditCustomerProfileBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        // Populate existing details
        var streetVal = ""
        var barangayVal = ""
        val dbStreet = customer.custStreet ?: ""
        if (dbStreet.contains(", Brgy. ")) {
            val parts = dbStreet.split(", Brgy. ", limit = 2)
            streetVal = parts[0]
            barangayVal = parts[1]
        } else {
            streetVal = dbStreet
        }

        dialogBinding.etProfileFname.setText(customer.custFname ?: "")
        dialogBinding.etProfileLname.setText(customer.custLname ?: "")
        dialogBinding.etProfileStreet.setText(streetVal)
        dialogBinding.etProfilePhone.setText(customer.custContactNumber ?: "")

        // Provinces list dropdown
        val provinces = LocationHelper.locationsMap.keys.toList()
        val provAdapter = NoFilterArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, provinces)
        dialogBinding.spProfileProvince.setAdapter(provAdapter)

        if (!customer.custProvince.isNullOrBlank()) {
            dialogBinding.spProfileProvince.setText(customer.custProvince, false)
        }

        val setupBarangaySpinner = { province: String, city: String ->
            val barangays = LocationHelper.locationsMap[province]?.get(city)?.barangays ?: emptyList()
            val barangayAdapter = NoFilterArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, barangays)
            dialogBinding.spProfileBarangay.setAdapter(barangayAdapter)
            if (barangays.contains(barangayVal)) {
                dialogBinding.spProfileBarangay.setText(barangayVal, false)
            } else {
                dialogBinding.spProfileBarangay.setText("")
            }
        }

        val setupCitySpinner = { province: String ->
            val cities = LocationHelper.locationsMap[province]?.keys?.toList() ?: emptyList()
            val cityAdapter = NoFilterArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, cities)
            dialogBinding.spProfileCity.setAdapter(cityAdapter)
            if (cities.contains(customer.custCity)) {
                dialogBinding.spProfileCity.setText(customer.custCity, false)
                customer.custCity?.let { setupBarangaySpinner(province, it) }
            } else {
                dialogBinding.spProfileCity.setText("")
                dialogBinding.spProfileBarangay.setText("")
                dialogBinding.spProfileBarangay.setAdapter(null)
            }
        }

        customer.custProvince?.let { setupCitySpinner(it) }

        dialogBinding.spProfileProvince.setOnItemClickListener { _, _, position, _ ->
            if (position < provinces.size) {
                val selected = provinces[position]
                dialogBinding.spProfileCity.setText("")
                dialogBinding.spProfileBarangay.setText("")
                dialogBinding.etProfilePostal.setText("")
                dialogBinding.spProfileCity.setAdapter(null)
                dialogBinding.spProfileBarangay.setAdapter(null)
                setupCitySpinner(selected)
            }
        }

        val updatePostal = { city: String ->
            val selectedProv = dialogBinding.spProfileProvince.text.toString()
            val postal = LocationHelper.locationsMap[selectedProv]?.get(city)?.postal ?: "1000"
            dialogBinding.etProfilePostal.setText(postal)
        }

        if (!customer.custCity.isNullOrBlank()) {
            updatePostal(customer.custCity)
        }

        dialogBinding.spProfileCity.setOnItemClickListener { _, _, position, _ ->
            val selectedProv = dialogBinding.spProfileProvince.text.toString()
            val cities = LocationHelper.locationsMap[selectedProv]?.keys?.toList() ?: emptyList()
            if (position < cities.size) {
                val selectedCity = cities[position]
                updatePostal(selectedCity)
                dialogBinding.spProfileBarangay.setText("")
                setupBarangaySpinner(selectedProv, selectedCity)
            }
        }

        dialogBinding.btnCancelProfile.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveProfile.setOnClickListener {
            val fname = dialogBinding.etProfileFname.text?.toString()?.trim() ?: ""
            val lname = dialogBinding.etProfileLname.text?.toString()?.trim() ?: ""
            val street = dialogBinding.etProfileStreet.text?.toString()?.trim() ?: ""
            val province = dialogBinding.spProfileProvince.text?.toString() ?: ""
            val city = dialogBinding.spProfileCity.text?.toString() ?: ""
            val barangay = dialogBinding.spProfileBarangay.text?.toString() ?: ""
            val postal = dialogBinding.etProfilePostal.text?.toString() ?: ""
            val phone = dialogBinding.etProfilePhone.text?.toString()?.trim() ?: ""

            if (fname.isEmpty() || lname.isEmpty() || street.isEmpty() || province.isEmpty() || city.isEmpty() || barangay.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Please fill in all profile fields!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (phone.length != 11 || !phone.startsWith("09")) {
                dialogBinding.etProfilePhone.error = "Must be a valid 11-digit mobile (09xxxxxxxxx)"
                return@setOnClickListener
            }

            dialog.dismiss()
            swipeRefresh.isRefreshing = true

            val combinedStreet = "$street, Brgy. $barangay"

            val updates = mapOf<String, Any>(
                "Cust_Fname" to fname,
                "Cust_Lname" to lname,
                "Cust_Street" to combinedStreet,
                "Cust_Province" to province,
                "Cust_City" to city,
                "Cust_Postal_Code" to postal,
                "Cust_Contact_Number" to phone
            )

            lifecycleScope.launch {
                try {
                    // Match key dynamically from database
                    val customersResponse = RetrofitClient.instance.getAllCustomers()
                    if (customersResponse.isSuccessful) {
                        val body = customersResponse.body()
                        val firebaseKey = body?.entries?.find { it.value.custId == customer.custId }?.key
                        if (firebaseKey != null) {
                            val patchRes = RetrofitClient.instance.updateCustomerProfile(firebaseKey, updates)
                            if (patchRes.isSuccessful) {
                                Toast.makeText(this@CustomerDashboardActivity, "Profile details successfully updated!", Toast.LENGTH_LONG).show()
                                
                                // Refresh local session and drawer UI
                                sessionManager.createLoginSession(customer.custId, customer.custEmail, "customer", "$fname $lname")
                                findViewById<TextView>(R.id.tvDrawerName).text = "$fname $lname"
                                findViewById<TextView>(R.id.tvDrawerEmail).text = "$fname$lname".replace(" ", "").lowercase(Locale.US) + "@spx.com"

                                // Navigate to wizard directly
                                val intent = Intent(this@CustomerDashboardActivity, BookParcelActivity::class.java)
                                startActivity(intent)
                            } else {
                                Toast.makeText(this@CustomerDashboardActivity, "Failed to patch profile: ${patchRes.code()}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@CustomerDashboardActivity, "Error saving: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    swipeRefresh.isRefreshing = false
                }
            }
        }

        dialog.show()
    }

    private fun setupRecyclerView() {
        // Recycle the active parcel adapter style for customer list view
        adapter = ActiveRouteAdapter(
            onTrackClicked = { parcel ->
                // Direct deep-link click to native Milestone Tracking
                val intent = Intent(this, TrackingActivity::class.java).apply {
                    putExtra("TRACKING_NUMBER", parcel.parclTrackingNumber)
                    putExtra("PARCEL_ID", parcel.parclId)
                }
                startActivity(intent)
            }
        )
        rvCustomerParcels.layoutManager = LinearLayoutManager(this)
        rvCustomerParcels.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadCustomerParcels()
    }

    private fun loadCustomerParcels() {
        swipeRefresh.isRefreshing = true
        val loggedInCustomerId = sessionManager.getUserId()
        
        lifecycleScope.launch {
            try {
                // 1. Fetch all bookings to extract customer's specific booked IDs
                val bookingsResponse = RetrofitClient.instance.getAllBookings()
                if (bookingsResponse.isSuccessful) {
                    val bookingsMap = bookingsResponse.body() ?: emptyMap()
                    val customerBookingIds = bookingsMap.values.filterNotNull()
                        .filter { it.bkngCustomerId == loggedInCustomerId }
                        .map { it.bkngId }
                        .toSet()

                    // 2. Fetch all parcels in the Firebase database and filter relationally
                    val response = RetrofitClient.instance.getAllParcels()
                    if (response.isSuccessful) {
                        val parcelsMap = response.body()
                        if (!parcelsMap.isNullOrEmpty()) {
                            val allParcels = parcelsMap.values.toList()
                            
                            // Dynamically filter parcels for this specific customer bookings
                            val customerParcels = allParcels.filter {
                                customerBookingIds.contains(it.parclBkngId)
                            }.sortedByDescending { it.parclId }
                            
                            // Calculate stats
                            val pendingCount = customerParcels.count { it.parclDeliveryStatus == "Pending Drop-off" }
                            val transitCount = customerParcels.count { it.parclDeliveryStatus == "In Transit" || it.parclDeliveryStatus == "Out for Delivery" }
                            val deliveredCount = customerParcels.count { it.parclDeliveryStatus == "Delivered" }

                            tvStatPending.text = pendingCount.toString()
                            tvStatTransit.text = transitCount.toString()
                            tvStatDelivered.text = deliveredCount.toString()

                            adapter.submitList(customerParcels)
                        } else {
                            adapter.submitList(emptyList())
                        }
                    } else {
                        Toast.makeText(this@CustomerDashboardActivity, "Parcel fetch failed: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@CustomerDashboardActivity, "Booking fetch failed: ${bookingsResponse.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CustomerDashboardActivity, "Connection Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                swipeRefresh.isRefreshing = false
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
