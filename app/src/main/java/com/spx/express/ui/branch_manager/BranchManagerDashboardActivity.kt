package com.spx.express.ui.branch_manager

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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.textfield.TextInputEditText
import com.spx.express.R
import com.spx.express.data.api.RetrofitClient
import com.spx.express.data.model.Parcel
import com.spx.express.data.model.ParcelStatusHistory
import com.spx.express.data.model.Staff
import com.spx.express.data.storage.SessionManager
import com.spx.express.ui.LoginActivity
import com.spx.express.ui.customer.tracking.TrackingTimelineAdapter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.Spinner
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import org.mindrot.jbcrypt.BCrypt


class BranchManagerDashboardActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var rightDrawer: LinearLayout

    private lateinit var tvInventoryCount: TextView
    private lateinit var tvRemittanceCount: TextView
    private lateinit var tvCurrentViewTitle: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var rvManagerParcels: RecyclerView
    private lateinit var btnAddEmployee: com.google.android.material.button.MaterialButton

    // Sidebar items
    private lateinit var navOverview: TextView
    private lateinit var navEmployees: TextView
    private lateinit var navVerifyCash: TextView
    private lateinit var navBranchLog: TextView
    private lateinit var navEditProfile: TextView
    private lateinit var btnDrawerSignOut: Button

    // Profile Settings Form
    private lateinit var settingsFormContainer: NestedScrollView
    private lateinit var etMgrFname: TextInputEditText
    private lateinit var etMgrLname: TextInputEditText
    private lateinit var etMgrEmail: TextInputEditText
    private lateinit var etMgrPhone: TextInputEditText
    private lateinit var etMgrPassword: TextInputEditText
    private lateinit var btnSaveProfile: Button
    private lateinit var inputLayoutEmployeeSearch: com.google.android.material.textfield.TextInputLayout
    private lateinit var etEmployeeSearch: TextInputEditText

    // Adapters
    private lateinit var parcelAdapter: ManagerParcelAdapter
    private lateinit var employeeAdapter: EmployeeAdapter
    private lateinit var branchLogAdapter: BranchLogAdapter

    private var allParcelsList = listOf<Parcel>()
    private var allStaffList = listOf<Staff>()
    private var allHistoryList = listOf<ParcelStatusHistory>()
    
    private var managerBranchId: Int? = null
    private var globalMaxStaffId = 0

    private enum class ViewMode {
        OVERVIEW, EMPLOYEES, VERIFY_CASH, BRANCH_LOG, SETTINGS
    }
    private var currentViewMode = ViewMode.OVERVIEW

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_branch_manager_dashboard)

        sessionManager = SessionManager(this)

        // Setup Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Populate Custom Welcome Header
        findViewById<TextView>(R.id.tvToolbarGreeting).text = "Hello Manager,"
        findViewById<TextView>(R.id.tvToolbarName).text = sessionManager.getUserName() ?: "Branch Manager"
        findViewById<TextView>(R.id.tvToolbarRole).apply {
            text = "Branch Manager"
            setTextColor(android.graphics.Color.parseColor("#059669")) // Elegant Emerald Green
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cvToolbarBadge).apply {
            setCardBackgroundColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#ECFDF5")))
        }

        // Initialize UI components
        drawerLayout = findViewById(R.id.drawerLayout)
        rightDrawer = findViewById(R.id.rightDrawer)
        tvInventoryCount = findViewById(R.id.tvInventoryCount)
        tvRemittanceCount = findViewById(R.id.tvRemittanceCount)
        tvCurrentViewTitle = findViewById(R.id.tvCurrentViewTitle)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        rvManagerParcels = findViewById(R.id.rvManagerParcels)
        btnAddEmployee = findViewById(R.id.btnAddEmployee)

        // Profile Form UI components
        settingsFormContainer = findViewById(R.id.settingsFormContainer)
        etMgrFname = findViewById(R.id.etMgrFname)
        etMgrLname = findViewById(R.id.etMgrLname)
        etMgrEmail = findViewById(R.id.etMgrEmail)
        etMgrPhone = findViewById(R.id.etMgrPhone)
        etMgrPassword = findViewById(R.id.etMgrPassword)
        btnSaveProfile = findViewById(R.id.btnSaveProfile)
        
        inputLayoutEmployeeSearch = findViewById(R.id.inputLayoutEmployeeSearch)
        etEmployeeSearch = findViewById(R.id.etEmployeeSearch)

        // Drawer profile values
        findViewById<TextView>(R.id.tvDrawerName).text = sessionManager.getUserName() ?: "Branch Manager"
        findViewById<TextView>(R.id.tvDrawerEmail).text = sessionManager.getUserName()?.replace(" ", "")?.lowercase() + "@spx.com"

        // Initialize side items
        navOverview = findViewById(R.id.navOverview)
        navEmployees = findViewById(R.id.navEmployees)
        navVerifyCash = findViewById(R.id.navVerifyCash)
        navBranchLog = findViewById(R.id.navBranchLog)
        navEditProfile = findViewById(R.id.navEditProfile)
        btnDrawerSignOut = findViewById(R.id.btnDrawerSignOut)

        setupRecyclerViews()
        setupListeners()
        loadManagerProfileAndData()

        // Prompt for logout on back pressed instead of closing directly
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showLogoutConfirmationDialog()
            }
        })
    }

    private fun setupRecyclerViews() {
        parcelAdapter = ManagerParcelAdapter(
            onVerifyClicked = { parcel -> verifyRiderRemittance(parcel) }
        )
        employeeAdapter = EmployeeAdapter(
            onEditClicked = { employee -> showEditEmployeeDialog(employee) }
        )
        branchLogAdapter = BranchLogAdapter()

        rvManagerParcels.layoutManager = LinearLayoutManager(this)
        rvManagerParcels.adapter = parcelAdapter
    }

    private fun setupListeners() {
        swipeRefresh.setOnRefreshListener {
            loadDashboardStats()
            loadCurrentViewData()
        }

        // Drawer Menu Click Items
        navOverview.setOnClickListener {
            currentViewMode = ViewMode.OVERVIEW
            drawerLayout.closeDrawer(GravityCompat.END)
            loadCurrentViewData()
        }

        navEmployees.setOnClickListener {
            currentViewMode = ViewMode.EMPLOYEES
            drawerLayout.closeDrawer(GravityCompat.END)
            loadCurrentViewData()
        }

        navVerifyCash.setOnClickListener {
            currentViewMode = ViewMode.VERIFY_CASH
            drawerLayout.closeDrawer(GravityCompat.END)
            loadCurrentViewData()
        }

        navBranchLog.setOnClickListener {
            currentViewMode = ViewMode.BRANCH_LOG
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

        btnSaveProfile.setOnClickListener {
            saveManagerProfileChanges()
        }

        btnAddEmployee.setOnClickListener {
            showAddEmployeeDialog()
        }

        etEmployeeSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterEmployeeList(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        loadManagerProfileAndData()
    }

    private fun filterEmployeeList(query: String) {
        if (currentViewMode != ViewMode.EMPLOYEES) return
        val filteredList = if (query.trim().isEmpty()) {
            allStaffList
        } else {
            allStaffList.filter { employee ->
                val fullName = "${employee.stfFname} ${employee.stfLname}".lowercase()
                fullName.contains(query.lowercase()) || employee.stfEmail.lowercase().contains(query.lowercase())
            }
        }
        employeeAdapter.submitList(filteredList)
    }

    private fun loadManagerProfileAndData() {
        val managerEmail = sessionManager.getUserEmail() ?: "manager@spx.com"
        
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getAllStaff()
                if (response.isSuccessful) {
                    val staffMap = response.body()
                    if (!staffMap.isNullOrEmpty()) {
                        val manager = staffMap.values.find { it?.stfEmail?.equals(managerEmail, ignoreCase = true) == true }
                        if (manager != null) {
                            managerBranchId = manager.stfBrchId
                        }
                    }
                }
            } catch (e: Exception) {
                // Fail silently, fallback to global hub view
            } finally {
                loadDashboardStats()
                loadCurrentViewData()
            }
        }
    }

    private fun loadDashboardStats() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getAllParcels()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        val parcels = body.values.filterNotNull().filter {
                            managerBranchId == null || it.parclOrigBrchId == managerBranchId || it.parclDestBrchId == managerBranchId
                        }
                        val activeInventoryCount = parcels.count { 
                            it.parclDeliveryStatus != "Delivered" && it.parclDeliveryStatus != "Delivery Failed"
                        }
                        val remittanceCount = parcels.count { 
                            it.parclRemitStatus == "Remitted" || it.parclRemitStatus == "Pending"
                        }

                        tvInventoryCount.text = activeInventoryCount.toString()
                        tvRemittanceCount.text = remittanceCount.toString()
                    }
                }
            } catch (e: Exception) {
                // Fail silently for background stat updates
            }
        }
    }

    private fun loadCurrentViewData() {
        val tvGreeting = findViewById<TextView>(R.id.tvToolbarGreeting)
        val tvName = findViewById<TextView>(R.id.tvToolbarName)

        when (currentViewMode) {
            ViewMode.OVERVIEW -> {
                tvGreeting?.text = "Hello Manager,"
                tvName?.text = sessionManager.getUserName() ?: "Branch Manager"
            }
            ViewMode.EMPLOYEES -> {
                tvGreeting?.text = "Manager Operations"
                tvName?.text = "Personnel Directory"
            }
            ViewMode.VERIFY_CASH -> {
                tvGreeting?.text = "Manager Accounts"
                tvName?.text = "Verify Remittances"
            }
            ViewMode.BRANCH_LOG -> {
                tvGreeting?.text = "Manager Audit Logs"
                tvName?.text = "Station Audit Logs"
            }
            ViewMode.SETTINGS -> {
                tvGreeting?.text = "Manager Settings"
                tvName?.text = "Edit Profile"
            }
        }

        // Toggle view containers
        if (currentViewMode == ViewMode.SETTINGS) {
            swipeRefresh.visibility = View.GONE
            settingsFormContainer.visibility = View.VISIBLE
            tvCurrentViewTitle.text = "⚙️ Edit Profile"
            btnAddEmployee.visibility = View.GONE
            inputLayoutEmployeeSearch.visibility = View.GONE
            etEmployeeSearch.setText("")
            loadProfileDetailsIntoForm()
            return
        }

        swipeRefresh.visibility = View.VISIBLE
        settingsFormContainer.visibility = View.GONE
        inputLayoutEmployeeSearch.visibility = View.GONE
        etEmployeeSearch.setText("")
        swipeRefresh.isRefreshing = true

        lifecycleScope.launch {
            try {
                when (currentViewMode) {
                    ViewMode.OVERVIEW -> {
                        tvCurrentViewTitle.text = "📦 Branch Station Inventory"
                        btnAddEmployee.visibility = View.GONE
                        rvManagerParcels.adapter = parcelAdapter

                        val response = RetrofitClient.instance.getAllParcels()
                        if (response.isSuccessful) {
                            val body = response.body()
                            if (body != null) {
                                allParcelsList = body.filterValues { it != null }.map { (key, parcel) ->
                                    parcel!!.copy().apply { firebaseKey = key }
                                }.filter { 
                                    it.parclDeliveryStatus != "Delivered" && it.parclDeliveryStatus != "Delivery Failed"
                                }.filter {
                                    managerBranchId == null || it.parclOrigBrchId == managerBranchId || it.parclDestBrchId == managerBranchId
                                }.sortedByDescending { it.parclId }

                                parcelAdapter.submitList(allParcelsList, showRemittance = false)
                            } else {
                                parcelAdapter.submitList(emptyList(), showRemittance = false)
                            }
                        }
                    }

                    ViewMode.VERIFY_CASH -> {
                        tvCurrentViewTitle.text = "💰 COD Cash Remittances Queue"
                        btnAddEmployee.visibility = View.GONE
                        rvManagerParcels.adapter = parcelAdapter

                        val response = RetrofitClient.instance.getAllParcels()
                        if (response.isSuccessful) {
                            val body = response.body()
                            if (body != null) {
                                allParcelsList = body.filterValues { it != null }.map { (key, parcel) ->
                                    parcel!!.copy().apply { firebaseKey = key }
                                }.filter { 
                                    it.parclRemitStatus == "Remitted" || it.parclRemitStatus == "Pending"
                                }.filter {
                                    managerBranchId == null || it.parclOrigBrchId == managerBranchId || it.parclDestBrchId == managerBranchId
                                }.sortedByDescending { it.parclId }

                                parcelAdapter.submitList(allParcelsList, showRemittance = true)
                            } else {
                                parcelAdapter.submitList(emptyList(), showRemittance = true)
                            }
                        }
                    }

                    ViewMode.EMPLOYEES -> {
                        tvCurrentViewTitle.text = "👥 Branch Employee Roster"
                        btnAddEmployee.visibility = View.VISIBLE
                        inputLayoutEmployeeSearch.visibility = View.VISIBLE
                        rvManagerParcels.adapter = employeeAdapter

                        android.util.Log.d("SPX_DEBUG", "EMPLOYEES: managerBranchId = $managerBranchId")
                        val response = RetrofitClient.instance.getAllStaff()
                        android.util.Log.d("SPX_DEBUG", "EMPLOYEES: response.isSuccessful = ${response.isSuccessful}")
                        if (response.isSuccessful) {
                            val body = response.body()
                            android.util.Log.d("SPX_DEBUG", "EMPLOYEES: body size = ${body?.size}")
                            if (body != null) {
                                globalMaxStaffId = body.values.filterNotNull().maxOfOrNull { it.stfId } ?: 0
                                allStaffList = body.values.filterNotNull().filter {
                                    managerBranchId == null || it.stfBrchId == managerBranchId
                                }.sortedBy { it.stfFname }
                                android.util.Log.d("SPX_DEBUG", "EMPLOYEES: filtered staff count = ${allStaffList.size}")
                                for (s in allStaffList) {
                                    android.util.Log.d("SPX_DEBUG", "  - Staff: ${s.stfEmail}, Branch: ${s.stfBrchId}, Name: ${s.stfFname}")
                                }
                                employeeAdapter.submitList(allStaffList)
                            } else {
                                employeeAdapter.submitList(emptyList())
                            }
                        } else {
                            android.util.Log.d("SPX_DEBUG", "EMPLOYEES: response error code = ${response.code()}")
                            android.util.Log.d("SPX_DEBUG", "EMPLOYEES: response error body = ${response.errorBody()?.string()}")
                        }
                    }

                    ViewMode.BRANCH_LOG -> {
                        tvCurrentViewTitle.text = "📋 Branch Terminal Log Audit Feed"
                        btnAddEmployee.visibility = View.GONE
                        rvManagerParcels.adapter = branchLogAdapter

                        val response = RetrofitClient.instance.getAllHistoryLogs()
                        val parcelsRes = RetrofitClient.instance.getAllParcels()
                        val staffRes = RetrofitClient.instance.getAllStaff()

                        if (response.isSuccessful && parcelsRes.isSuccessful && staffRes.isSuccessful) {
                            val allParcels = parcelsRes.body()?.values?.filterNotNull() ?: emptyList()
                            val allStaff = staffRes.body()?.values?.filterNotNull() ?: emptyList()
                            
                            val body = response.body()
                            if (body != null) {
                                allHistoryList = body.values.filterNotNull().filter { log ->
                                    managerBranchId == null || 
                                    log.histLocationNote?.contains("Branch ID: $managerBranchId") == true ||
                                    log.histLocationNote?.contains("Hub by Manager") == true ||
                                    log.histRemark?.contains("turnover of") == true
                                }.sortedByDescending { log -> log.histTimestamp }
                                branchLogAdapter.submitList(allHistoryList, allParcels, allStaff)
                            } else {
                                branchLogAdapter.submitList(emptyList(), emptyList(), emptyList())
                            }
                        }
                    }
                    ViewMode.SETTINGS -> {}
                }
            } catch (e: Exception) {
                Toast.makeText(this@BranchManagerDashboardActivity, "Connection Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun loadProfileDetailsIntoForm() {
        val managerEmail = sessionManager.getUserEmail() ?: "manager@spx.com"
        
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getStaffByEmail(email = "\"$managerEmail\"")
                if (response.isSuccessful) {
                    val staffMap = response.body()
                    if (!staffMap.isNullOrEmpty()) {
                        val manager = staffMap.values.first()
                        etMgrFname.setText(manager.stfFname)
                        etMgrLname.setText(manager.stfLname)
                        etMgrEmail.setText(manager.stfEmail)
                        etMgrPhone.setText(manager.stfContactNumber ?: "")
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@BranchManagerDashboardActivity, "Could not load profile details", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveManagerProfileChanges() {
        val fname = etMgrFname.text?.toString()?.trim() ?: ""
        val lname = etMgrLname.text?.toString()?.trim() ?: ""
        val email = etMgrEmail.text?.toString()?.trim() ?: ""
        val phone = etMgrPhone.text?.toString()?.trim() ?: ""
        val password = etMgrPassword.text?.toString()?.trim() ?: ""

        if (fname.isEmpty() || lname.isEmpty() || email.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (phone.length != 11) {
            etMgrPhone.error = "Contact number must be exactly 11 digits"
            return
        }

        val managerEmail = sessionManager.getUserEmail() ?: email

        lifecycleScope.launch {
            try {
                btnSaveProfile.isEnabled = false
                btnSaveProfile.text = "Saving Changes..."

                // Retrieve target staff node key
                val staffSearch = RetrofitClient.instance.getStaffByEmail(email = "\"$managerEmail\"")
                if (staffSearch.isSuccessful) {
                    val staffMap = staffSearch.body()
                    if (!staffMap.isNullOrEmpty()) {
                        val targetKey = staffMap.keys.first()
                        val manager = staffMap.values.first()

                        val updates = mutableMapOf<String, Any>(
                            "Stf_Fname" to fname,
                            "Stf_Lname" to lname,
                            "Stf_Email" to email,
                            "Stf_Contact_Number" to phone
                        )
                        if (password.isNotEmpty()) {
                            updates["Stf_Password_Hash"] = password
                        }

                        val updateResponse = RetrofitClient.instance.updateStaffStatus(targetKey, updates)
                        if (updateResponse.isSuccessful) {
                            // Update secure mobile session cache
                            sessionManager.createLoginSession(
                                userId = manager.stfId,
                                email = email,
                                role = "branch_manager",
                                displayName = "$fname $lname"
                            )

                            // Update Toolbar Title
                            findViewById<TextView>(R.id.tvToolbarName)?.text = "$fname $lname"
                            findViewById<TextView>(R.id.tvDrawerName).text = "$fname $lname"
                            findViewById<TextView>(R.id.tvDrawerEmail).text = email

                            Toast.makeText(this@BranchManagerDashboardActivity, "✅ Profile updated successfully!", Toast.LENGTH_SHORT).show()
                            
                            // Return to main inventory overview
                            currentViewMode = ViewMode.OVERVIEW
                            loadCurrentViewData()
                        } else {
                            Toast.makeText(this@BranchManagerDashboardActivity, "Error saving updates: ${updateResponse.code()}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@BranchManagerDashboardActivity, "Connection Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                btnSaveProfile.isEnabled = true
                btnSaveProfile.text = "Save Profile Changes"
            }
        }
    }

    private fun verifyRiderRemittance(parcel: Parcel) {
        val codAmt = parcel.parclCodAmount ?: 0.0
        val codFormatted = "₱${String.format(Locale.US, "%.2f", codAmt)}"

        // Gate behind a confirmation dialog before committing any financial write
        AlertDialog.Builder(this)
            .setTitle("💵 Confirm Cash Remittance")
            .setMessage(
                "Please confirm you have physically received the COD cash turnover:\n\n" +
                "Tracking #: ${parcel.parclTrackingNumber}\n" +
                "Recipient: ${parcel.parclReceiverName}\n" +
                "COD Amount: $codFormatted\n" +
                "Current Status: ${parcel.parclRemitStatus ?: "Pending"}\n\n" +
                "Tapping \"Verify\" will mark this remittance as confirmed and cannot be undone."
            )
            .setPositiveButton("Verify") { dialog, _ ->
                dialog.dismiss()
                commitVerifyRemittance(parcel)
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    private fun commitVerifyRemittance(parcel: Parcel) {
        val targetKey = parcel.firebaseKey ?: parcel.parclId.toString()
        val managerId = sessionManager.getUserId()

        swipeRefresh.isRefreshing = true

        lifecycleScope.launch {
            try {
                val updates = mapOf<String, Any>(
                    "Parcl_Remit_Status" to "Verified"
                )
                val response = RetrofitClient.instance.updateParcelStatus(targetKey, updates)

                if (response.isSuccessful) {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val currentTimestamp = dateFormat.format(Date())

                    val milestoneLog = ParcelStatusHistory(
                        histParclId = parcel.parclId,
                        histStatus = "Cash Verified",
                        histLocationNote = "Physical COD turnover confirmed at Hub by Manager",
                        histTimestamp = currentTimestamp,
                        histStfId = if (managerId != -1) managerId else 2,
                        histRemark = "Verified turnover of ₱${parcel.parclCodAmount} (Tracking: ${parcel.parclTrackingNumber})"
                    )

                    RetrofitClient.instance.addHistoryLog(milestoneLog)
                    Toast.makeText(this@BranchManagerDashboardActivity, "✅ Remittance of ₱${parcel.parclCodAmount} verified!", Toast.LENGTH_SHORT).show()
                    loadDashboardStats()
                    loadCurrentViewData()
                } else {
                    Toast.makeText(this@BranchManagerDashboardActivity, "Failed to verify remittance: ${response.code()}", Toast.LENGTH_SHORT).show()
                    swipeRefresh.isRefreshing = false
                }
            } catch (e: Exception) {
                Toast.makeText(this@BranchManagerDashboardActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun showAddEmployeeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_employee, null)
        val etFname = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etAddFname)
        val etLname = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etAddLname)
        val etEmail = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etAddEmail)
        val etPhone = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etAddPhone)
        val etPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etAddPassword)
        val spnRole = dialogView.findViewById<Spinner>(R.id.spnAddRole)

        val roles = arrayOf("Counter Staff", "Delivery Rider")
        spnRole.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Confirm", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val fname = etFname.text?.toString()?.trim() ?: ""
            val lname = etLname.text?.toString()?.trim() ?: ""
            val email = etEmail.text?.toString()?.trim() ?: ""
            val phone = etPhone.text?.toString()?.trim() ?: ""
            val password = etPassword.text?.toString()?.trim() ?: ""
            val role = if (spnRole.selectedItemPosition == 0) "staff" else "rider"

            if (fname.isEmpty() || lname.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (phone.length != 11) {
                Toast.makeText(this, "Contact number must be exactly 11 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialog.dismiss()
            swipeRefresh.isRefreshing = true

            lifecycleScope.launch {
                try {
                    val passwordHash = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        BCrypt.hashpw(password, BCrypt.gensalt(10)).replaceFirst("$2a$", "$2y$")
                    }

                    val newId = (globalMaxStaffId).coerceAtLeast(allStaffList.maxOfOrNull { it.stfId } ?: 0) + 1
                    val branchId = managerBranchId ?: 1
                    val currentTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                    val newStaffMap = mapOf<String, Any>(
                        "Stf_Id" to newId,
                        "Stf_Fname" to fname,
                        "Stf_Lname" to lname,
                        "Stf_Email" to email,
                        "Stf_Contact_Number" to phone,
                        "Stf_Password_Hash" to passwordHash,
                        "Stf_Role" to role,
                        "Stf_Brch_Id" to branchId,
                        "Stf_Is_Active" to 1,
                        "Stf_Created_At" to currentTimestamp
                    )

                    val response = RetrofitClient.instance.updateStaffStatus(newId.toString(), newStaffMap)
                    if (response.isSuccessful) {
                        Toast.makeText(this@BranchManagerDashboardActivity, "✅ $fname has been hired!", Toast.LENGTH_SHORT).show()
                        loadCurrentViewData()
                    } else {
                        Toast.makeText(this@BranchManagerDashboardActivity, "Failed to hire staff: ${response.code()}", Toast.LENGTH_SHORT).show()
                        swipeRefresh.isRefreshing = false
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@BranchManagerDashboardActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private fun showEditEmployeeDialog(employee: Staff) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_employee, null)
        val etFname = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditFname)
        val etLname = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditLname)
        val etEmail = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditEmail)
        val etPhone = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditPhone)
        val etPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditPassword)
        val spnRole = dialogView.findViewById<Spinner>(R.id.spnEditRole)
        val spnStatus = dialogView.findViewById<Spinner>(R.id.spnEditStatus)

        etFname.setText(employee.stfFname)
        etLname.setText(employee.stfLname)
        etEmail.setText(employee.stfEmail)
        etPhone.setText(employee.stfContactNumber ?: "")

        val roles = arrayOf("Counter Staff", "Delivery Rider")
        spnRole.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
        spnRole.setSelection(if (employee.stfRole == "staff") 0 else 1)

        val statuses = arrayOf("Active", "Inactive")
        spnStatus.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, statuses)
        spnStatus.setSelection(if (employee.stfIsActive == 1) 0 else 1)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.show()

        val isSelf = (employee.stfId == sessionManager.getUserId())
        if (isSelf) {
            dialogView.findViewById<TextView>(R.id.tvDialogTitle)?.text = "Employee Record (Self - Read Only)"
            etFname.isEnabled = false
            etLname.isEnabled = false
            etEmail.isEnabled = false
            etPhone.isEnabled = false
            etPassword.isEnabled = false
            spnRole.isEnabled = false
            spnStatus.isEnabled = false
            
            dialogView.findViewById<View>(R.id.btnSave)?.visibility = View.GONE
            dialogView.findViewById<View>(R.id.btnDelete)?.visibility = View.GONE
            dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)?.text = "Close"
            
            Toast.makeText(this, "ℹ️ You cannot modify your own profile here. Use Settings to edit profile.", Toast.LENGTH_LONG).show()
        }

        // Cancel button click
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        // Delete button click
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDelete).setOnClickListener {
            if (employee.stfId == sessionManager.getUserId()) {
                Toast.makeText(this@BranchManagerDashboardActivity, "❌ Error: You cannot delete your own account.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            androidx.appcompat.app.AlertDialog.Builder(this@BranchManagerDashboardActivity)
                .setTitle("Delete Employee")
                .setMessage("Are you sure you want to delete this employee account?")
                .setPositiveButton("Delete") { _, _ ->
                    dialog.dismiss()
                    swipeRefresh.isRefreshing = true
                    lifecycleScope.launch {
                        try {
                            val deleteResponse = RetrofitClient.instance.deleteStaff(employee.stfId)
                            if (deleteResponse.isSuccessful) {
                                Toast.makeText(this@BranchManagerDashboardActivity, "✅ Staff successfully deleted!", Toast.LENGTH_SHORT).show()
                                loadCurrentViewData()
                            } else {
                                Toast.makeText(this@BranchManagerDashboardActivity, "Error deleting staff: ${deleteResponse.code()}", Toast.LENGTH_SHORT).show()
                                swipeRefresh.isRefreshing = false
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@BranchManagerDashboardActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            swipeRefresh.isRefreshing = false
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Save changes click
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave).setOnClickListener {
            val fname = etFname.text?.toString()?.trim() ?: ""
            val lname = etLname.text?.toString()?.trim() ?: ""
            val email = etEmail.text?.toString()?.trim() ?: ""
            val phone = etPhone.text?.toString()?.trim() ?: ""
            val password = etPassword.text?.toString()?.trim() ?: ""
            val role = if (spnRole.selectedItemPosition == 0) "staff" else "rider"
            val isActive = if (spnStatus.selectedItemPosition == 0) 1 else 0

            if (fname.isEmpty() || lname.isEmpty() || email.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (phone.length != 11) {
                Toast.makeText(this, "Phone must be exactly 11 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialog.dismiss()
            swipeRefresh.isRefreshing = true

            lifecycleScope.launch {
                try {
                    val updates = mutableMapOf<String, Any>(
                        "Stf_Fname" to fname,
                        "Stf_Lname" to lname,
                        "Stf_Email" to email,
                        "Stf_Contact_Number" to phone,
                        "Stf_Role" to role,
                        "Stf_Is_Active" to isActive
                    )

                    if (password.isNotEmpty()) {
                        val passwordHash = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            BCrypt.hashpw(password, BCrypt.gensalt(10)).replaceFirst("$2a$", "$2y$")
                        }
                        updates["Stf_Password_Hash"] = passwordHash
                    }

                    val response = RetrofitClient.instance.updateStaffStatus(employee.stfId.toString(), updates)
                    if (response.isSuccessful) {
                        Toast.makeText(this@BranchManagerDashboardActivity, "✅ Updated personnel record!", Toast.LENGTH_SHORT).show()
                        loadCurrentViewData()
                    } else {
                        Toast.makeText(this@BranchManagerDashboardActivity, "Update failed: ${response.code()}", Toast.LENGTH_SHORT).show()
                        swipeRefresh.isRefreshing = false
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@BranchManagerDashboardActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    swipeRefresh.isRefreshing = false
                }
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

    private fun showLogoutConfirmationDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Confirm Logout")
            .setMessage("Are you sure you want to log out of the management terminal?")
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
}
