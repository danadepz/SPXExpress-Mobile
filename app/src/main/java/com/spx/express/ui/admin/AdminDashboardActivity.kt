package com.spx.express.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.widget.NestedScrollView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.spx.express.R
import com.spx.express.data.api.RetrofitClient
import com.spx.express.data.model.Branch
import com.spx.express.data.model.Customer
import com.spx.express.data.model.Parcel
import com.spx.express.data.model.ParcelStatusHistory
import com.spx.express.data.model.Staff
import com.spx.express.data.storage.SessionManager
import com.spx.express.databinding.ActivityAdminDashboardBinding
import com.spx.express.databinding.DialogAddEditBranchBinding
import com.spx.express.databinding.DialogAddEditStaffBinding
import com.spx.express.databinding.ItemAdminBranchBinding
import com.spx.express.databinding.ItemAdminStaffBinding
import com.spx.express.ui.LoginActivity
import com.spx.express.ui.branch_manager.BranchLogAdapter
import com.spx.express.ui.customer.NoFilterArrayAdapter
import kotlinx.coroutines.launch
import org.mindrot.jbcrypt.BCrypt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var rightDrawer: LinearLayout

    // Adapters
    private lateinit var branchAdapter: BranchAdapter
    private lateinit var staffAdapter: StaffAdapter
    private lateinit var branchLogAdapter: BranchLogAdapter
    private lateinit var adminLogAdapter: AdminLogAdapter
    private lateinit var performanceAdapter: BranchPerformanceAdapter

    private var allBranchesList = listOf<Branch>()
    private var allStaffList = listOf<Staff>()
    private var allParcelsList = listOf<Parcel>()
    private var allHistoryList = listOf<ParcelStatusHistory>()

    private var globalMaxBranchId = 0
    private var globalMaxStaffId = 0
    private var selectedFilterBranchId: Int? = null

    private enum class ViewMode {
        OVERVIEW, BRANCHES, STAFF, AUDIT_FEED, SETTINGS
    }
    private var currentViewMode = ViewMode.OVERVIEW

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        // Session check
        if (!sessionManager.isLoggedIn() || sessionManager.getUserRole()?.lowercase() != "admin") {
            redirectToLogin()
            return
        }

        // Setup Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Populate Custom Welcome Header
        findViewById<TextView>(R.id.tvToolbarGreeting).text = "Hello Administrator,"
        findViewById<TextView>(R.id.tvToolbarName).text = sessionManager.getUserName() ?: "System Admin"
        findViewById<TextView>(R.id.tvToolbarRole).apply {
            text = "System Admin"
            setTextColor(android.graphics.Color.parseColor("#475569")) // Elegant Slate Gray
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cvToolbarBadge).apply {
            setCardBackgroundColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F1F5F9")))
        }

        drawerLayout = binding.drawerLayout
        rightDrawer = binding.rightDrawer

        // Drawer values
        binding.tvDrawerName.text = sessionManager.getUserName() ?: "System Admin"
        binding.tvDrawerEmail.text = sessionManager.getUserEmail() ?: "admin@spx.com"

        setupRecyclerViews()
        setupListeners()
        loadDashboardStats()
        loadCurrentViewData()

        // Prompt for logout on back pressed
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showLogoutConfirmationDialog()
            }
        })
    }

    private fun setupRecyclerViews() {
        branchAdapter = BranchAdapter(
            onEditClicked = { branch -> showAddEditBranchDialog(branch) }
        )
        staffAdapter = StaffAdapter(
            onEditClicked = { staff -> showAddEditStaffDialog(staff) }
        )
        branchLogAdapter = BranchLogAdapter()
        adminLogAdapter = AdminLogAdapter(
            onDetailsClicked = { log, parcel, staff, branch -> showLogDetailsDialog(log, parcel, staff, branch) }
        )
        performanceAdapter = BranchPerformanceAdapter()

        binding.rvAdminContent.layoutManager = LinearLayoutManager(this)
        binding.rvAdminContent.adapter = performanceAdapter
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            loadDashboardStats()
            loadCurrentViewData()
        }

        // Action button (+ Add)
        binding.btnAdminAction.setOnClickListener {
            if (currentViewMode == ViewMode.BRANCHES) {
                showAddEditBranchDialog(null)
            } else if (currentViewMode == ViewMode.STAFF) {
                showAddEditStaffDialog(null)
            }
        }

        // Sidebar Navigation
        binding.navOverview.setOnClickListener {
            currentViewMode = ViewMode.OVERVIEW
            drawerLayout.closeDrawer(GravityCompat.END)
            loadCurrentViewData()
        }

        binding.navBranches.setOnClickListener {
            currentViewMode = ViewMode.BRANCHES
            drawerLayout.closeDrawer(GravityCompat.END)
            loadCurrentViewData()
        }

        binding.navStaff.setOnClickListener {
            currentViewMode = ViewMode.STAFF
            drawerLayout.closeDrawer(GravityCompat.END)
            loadCurrentViewData()
        }

        binding.navAuditLog.setOnClickListener {
            currentViewMode = ViewMode.AUDIT_FEED
            drawerLayout.closeDrawer(GravityCompat.END)
            loadCurrentViewData()
        }

        binding.navEditProfile.setOnClickListener {
            currentViewMode = ViewMode.SETTINGS
            drawerLayout.closeDrawer(GravityCompat.END)
            loadCurrentViewData()
        }

        binding.btnDrawerSignOut.setOnClickListener {
            showLogoutConfirmationDialog()
        }

        binding.btnSaveProfile.setOnClickListener {
            saveAdminProfileChanges()
        }

        binding.btnAdminFilter.setOnClickListener {
            showBranchFilterDialog()
        }

        binding.etStaffSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterStaffList(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun redirectToLogin() {
        sessionManager.clearSession()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun showLogoutConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Confirm Logout")
            .setMessage("Are you sure you want to log out of the Admin Console?")
            .setPositiveButton("Logout") { _, _ ->
                redirectToLogin()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadDashboardStats() {
        lifecycleScope.launch {
            try {
                // 1. Fetch Branches count
                val branchRes = RetrofitClient.instance.getAllBranches()
                if (branchRes.isSuccessful) {
                    val branches = branchRes.body() ?: emptyMap()
                    allBranchesList = branches.values.filterNotNull()
                    globalMaxBranchId = allBranchesList.maxOfOrNull { it.brnchId } ?: 0
                    binding.tvStatBranches.text = allBranchesList.size.toString()
                }

                // 2. Fetch Staff count
                val staffRes = RetrofitClient.instance.getAllStaff()
                if (staffRes.isSuccessful) {
                    val staff = staffRes.body() ?: emptyMap()
                    allStaffList = staff.values.filterNotNull()
                    globalMaxStaffId = allStaffList.maxOfOrNull { it.stfId } ?: 0
                    binding.tvStatStaff.text = allStaffList.size.toString()
                }

                // 3. Fetch Parcels count
                val parcelRes = RetrofitClient.instance.getAllParcels()
                if (parcelRes.isSuccessful) {
                    val parcels = parcelRes.body() ?: emptyMap()
                    allParcelsList = parcels.values.filterNotNull()
                    binding.tvStatParcels.text = allParcelsList.size.toString()
                }
            } catch (e: Exception) {
                // Fail silently
            }
        }
    }

    private fun loadCurrentViewData() {
        val tvGreeting = findViewById<TextView>(R.id.tvToolbarGreeting)
        val tvName = findViewById<TextView>(R.id.tvToolbarName)

        when (currentViewMode) {
            ViewMode.OVERVIEW -> {
                tvGreeting?.text = "Hello Administrator,"
                tvName?.text = sessionManager.getUserName() ?: "System Admin"
            }
            ViewMode.BRANCHES -> {
                tvGreeting?.text = "Admin Networks"
                tvName?.text = "Branch Logistics Hubs"
            }
            ViewMode.STAFF -> {
                tvGreeting?.text = "Admin Roster"
                tvName?.text = "Manage Staff Personnel"
            }
            ViewMode.AUDIT_FEED -> {
                tvGreeting?.text = "Admin Logs"
                tvName?.text = "Global Audit Feed"
            }
            ViewMode.SETTINGS -> {
                tvGreeting?.text = "Admin Settings"
                tvName?.text = "Edit Profile"
            }
        }

        binding.swipeRefresh.isRefreshing = true

        // Reset views
        binding.adminStatsLayout.visibility = View.VISIBLE
        binding.rvAdminContent.visibility = View.VISIBLE
        binding.settingsFormContainer.visibility = View.GONE
        binding.btnAdminAction.visibility = View.GONE
        binding.btnAdminFilter.visibility = View.GONE
        binding.inputLayoutStaffSearch.visibility = View.GONE
        binding.etStaffSearch.setText("")

        lifecycleScope.launch {
            try {
                when (currentViewMode) {
                    ViewMode.OVERVIEW -> {
                        binding.tvCurrentViewTitle.text = "📈 Branch Performance Monitor"
                        binding.rvAdminContent.adapter = performanceAdapter

                        val branchesRes = RetrofitClient.instance.getAllBranches()
                        val staffRes = RetrofitClient.instance.getAllStaff()
                        val parcelsRes = RetrofitClient.instance.getAllParcels()

                        if (branchesRes.isSuccessful && staffRes.isSuccessful && parcelsRes.isSuccessful) {
                            allBranchesList = branchesRes.body()?.values?.filterNotNull()?.sortedBy { it.brnchName } ?: emptyList()
                            allStaffList = staffRes.body()?.values?.filterNotNull() ?: emptyList()
                            allParcelsList = parcelsRes.body()?.values?.filterNotNull() ?: emptyList()
                            performanceAdapter.submitData(allBranchesList, allStaffList, allParcelsList)
                        }
                    }
                    ViewMode.BRANCHES -> {
                        binding.tvCurrentViewTitle.text = "🏢 Branch Networks"
                        binding.btnAdminAction.visibility = View.VISIBLE
                        binding.btnAdminAction.text = "+ Add Branch"
                        binding.rvAdminContent.adapter = branchAdapter

                        val response = RetrofitClient.instance.getAllBranches()
                        if (response.isSuccessful) {
                            val body = response.body()
                            if (body != null) {
                                allBranchesList = body.values.filterNotNull().sortedBy { it.brnchName }
                                branchAdapter.submitList(allBranchesList)
                            }
                        }
                    }
                    ViewMode.STAFF -> {
                        binding.tvCurrentViewTitle.text = "👥 Manage Staff Roster"
                        binding.btnAdminAction.visibility = View.VISIBLE
                        binding.btnAdminAction.text = "+ Add Staff"
                        binding.inputLayoutStaffSearch.visibility = View.VISIBLE
                        binding.rvAdminContent.adapter = staffAdapter

                        // Ensure we have loaded both branches and staff
                        val branchesRes = RetrofitClient.instance.getAllBranches()
                        val staffRes = RetrofitClient.instance.getAllStaff()
                        if (branchesRes.isSuccessful && staffRes.isSuccessful) {
                            allBranchesList = branchesRes.body()?.values?.filterNotNull() ?: emptyList()
                            allStaffList = staffRes.body()?.values?.filterNotNull()?.sortedBy { it.stfId } ?: emptyList()
                            staffAdapter.submitList(allStaffList, allBranchesList)
                        }
                    }
                    ViewMode.AUDIT_FEED -> {
                        binding.adminStatsLayout.visibility = View.GONE
                        binding.tvCurrentViewTitle.text = "📋 Global Log Feed"
                        binding.btnAdminFilter.visibility = View.VISIBLE
                        binding.rvAdminContent.adapter = adminLogAdapter

                        val filterName = if (selectedFilterBranchId == null) "All" else allBranchesList.find { it.brnchId == selectedFilterBranchId }?.brnchName ?: "Selected"
                        binding.btnAdminFilter.text = "Filter: $filterName"

                        val branchesRes = RetrofitClient.instance.getAllBranches()
                        val staffRes = RetrofitClient.instance.getAllStaff()
                        val parcelsRes = RetrofitClient.instance.getAllParcels()
                        val historyRes = RetrofitClient.instance.getAllHistoryLogs()

                        if (branchesRes.isSuccessful && staffRes.isSuccessful && parcelsRes.isSuccessful && historyRes.isSuccessful) {
                            allBranchesList = branchesRes.body()?.values?.filterNotNull() ?: emptyList()
                            allStaffList = staffRes.body()?.values?.filterNotNull() ?: emptyList()
                            allParcelsList = parcelsRes.body()?.values?.filterNotNull() ?: emptyList()
                            allHistoryList = historyRes.body()?.values?.filterNotNull()?.sortedByDescending { it.histTimestamp } ?: emptyList()

                            // Apply branch filter dynamically in-memory
                            val filteredLogs = if (selectedFilterBranchId == null) {
                                allHistoryList
                            } else {
                                allHistoryList.filter { log ->
                                    val staff = allStaffList.find { it.stfId == log.histStfId }
                                    val branchId = staff?.stfBrchId ?: allParcelsList.find { it.parclId == log.histParclId }?.parclOrigBrchId
                                    branchId == selectedFilterBranchId
                                }
                            }

                            adminLogAdapter.submitData(filteredLogs, allParcelsList, allStaffList, allBranchesList)
                        }
                    }
                    ViewMode.SETTINGS -> {
                        binding.adminStatsLayout.visibility = View.GONE
                        binding.rvAdminContent.visibility = View.GONE
                        binding.settingsFormContainer.visibility = View.VISIBLE
                        loadAdminProfileForm()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdminDashboardActivity, "Connection Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun loadAdminProfileForm() {
        val adminEmail = sessionManager.getUserEmail() ?: "admin@spx.com"
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getStaffByEmail(email = "\"$adminEmail\"")
                if (response.isSuccessful) {
                    val body = response.body()
                    if (!body.isNullOrEmpty()) {
                        val admin = body.values.first()
                        binding.etAdminFname.setText(admin.stfFname)
                        binding.etAdminLname.setText(admin.stfLname)
                        binding.etAdminEmail.setText(admin.stfEmail)
                        binding.etAdminPhone.setText(admin.stfContactNumber ?: "")
                    }
                }
            } catch (e: Exception) {
                // Fail silently
            }
        }
    }

    private fun saveAdminProfileChanges() {
        val fname = binding.etAdminFname.text?.toString()?.trim() ?: ""
        val lname = binding.etAdminLname.text?.toString()?.trim() ?: ""
        val email = binding.etAdminEmail.text?.toString()?.trim() ?: ""
        val phone = binding.etAdminPhone.text?.toString()?.trim() ?: ""
        val password = binding.etAdminPassword.text?.toString()?.trim() ?: ""

        if (fname.isEmpty() || lname.isEmpty() || email.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        val adminEmail = sessionManager.getUserEmail() ?: "admin@spx.com"

        lifecycleScope.launch {
            try {
                binding.swipeRefresh.isRefreshing = true
                val response = RetrofitClient.instance.getStaffByEmail(email = "\"$adminEmail\"")
                if (response.isSuccessful) {
                    val body = response.body()
                    if (!body.isNullOrEmpty()) {
                        val targetKey = body.keys.first()
                        val admin = body.values.first()

                        val updates = mutableMapOf<String, Any>(
                            "Stf_Fname" to fname,
                            "Stf_Lname" to lname,
                            "Stf_Email" to email,
                            "Stf_Contact_Number" to phone
                        )

                        if (password.isNotEmpty()) {
                            val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(10)).replaceFirst("$2a$", "$2y$")
                            updates["Stf_Password_Hash"] = passwordHash
                        }

                        val updateResponse = RetrofitClient.instance.updateStaffStatus(targetKey, updates)
                        if (updateResponse.isSuccessful) {
                            sessionManager.createLoginSession(
                                userId = admin.stfId,
                                email = email,
                                role = "admin",
                                displayName = "$fname $lname"
                            )
                            binding.tvDrawerName.text = "$fname $lname"
                            binding.tvDrawerEmail.text = email
                            Toast.makeText(this@AdminDashboardActivity, "✅ Profile updated successfully!", Toast.LENGTH_SHORT).show()
                            currentViewMode = ViewMode.OVERVIEW
                            loadCurrentViewData()
                        } else {
                            Toast.makeText(this@AdminDashboardActivity, "Error saving: ${updateResponse.code()}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdminDashboardActivity, "Connection error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun showAddEditBranchDialog(branch: Branch?) {
        val dialogBinding = DialogAddEditBranchBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        val isEdit = branch != null
        dialogBinding.tvDialogTitle.text = if (isEdit) "Edit Branch Details" else "Add New Branch"

        // Setup Spinner for Type
        val types = listOf("HUB", "DROP-OFF")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)
        dialogBinding.spBranchType.adapter = spinnerAdapter

        // Setup Exposed Autocomplete Dropdown for City
        val cities = com.spx.express.ui.customer.LocationHelper.locationsMap.values.flatMap { it.keys }.distinct().sorted()
        val cityAdapter = com.spx.express.ui.customer.NoFilterArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, cities)
        dialogBinding.spBranchCity.setAdapter(cityAdapter)

        // Setup Branch Manager Spinner (Visible in both Add and Edit modes)
        val currentManager = if (isEdit) {
            allStaffList.find { it.stfRole == "branch_manager" && it.stfBrchId == branch!!.brnchId }
        } else null

        val unassignedManagers = allStaffList.filter { it.stfRole == "branch_manager" && it.stfBrchId == null }

        val selectableManagers = mutableListOf<Staff>()
        if (currentManager != null) {
            selectableManagers.add(currentManager)
        }
        selectableManagers.addAll(unassignedManagers)

        dialogBinding.tvBranchManagerLabel.visibility = View.VISIBLE
        dialogBinding.cardBranchManager.visibility = View.VISIBLE

        val managerOptions = mutableListOf("None / Assign Later")
        managerOptions.addAll(selectableManagers.map { "${it.stfFname} ${it.stfLname}" })
        val managerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, managerOptions)
        dialogBinding.spBranchManager.adapter = managerAdapter

        if (isEdit && currentManager != null) {
            dialogBinding.spBranchManager.setSelection(1)
        } else {
            dialogBinding.spBranchManager.setSelection(0)
        }

        if (isEdit) {
            dialogBinding.etBranchName.setText(branch!!.brnchName)
            dialogBinding.spBranchCity.setText(branch.brnchCity ?: "", false)
            val selectedIndex = types.indexOf(branch.brnchType?.uppercase())
            if (selectedIndex != -1) {
                dialogBinding.spBranchType.setSelection(selectedIndex)
            }

            // Setup Delete button
            dialogBinding.btnDelete.visibility = View.VISIBLE
            dialogBinding.btnDelete.setOnClickListener {
                MaterialAlertDialogBuilder(this@AdminDashboardActivity)
                    .setTitle("Delete Branch")
                    .setMessage("Are you sure you want to delete this branch? All registered staff will be unassigned.")
                    .setPositiveButton("Delete") { _, _ ->
                        dialog.dismiss()
                        binding.swipeRefresh.isRefreshing = true
                        lifecycleScope.launch {
                            try {
                                // 1. Unassign staff in Firebase
                                val unassignedStaff = allStaffList.filter { it.stfBrchId == branch.brnchId }
                                for (staffMember in unassignedStaff) {
                                    val staffListRes = RetrofitClient.instance.getAllStaff()
                                    if (staffListRes.isSuccessful) {
                                        val body = staffListRes.body()
                                        val targetKey = body?.entries?.find { it.value.stfId == staffMember.stfId }?.key
                                        if (targetKey != null) {
                                            val updates = mapOf<String, Any?>("Stf_Brch_Id" to null)
                                            RetrofitClient.instance.updateStaffStatus(targetKey, updates as Map<String, Any>)
                                        }
                                    }
                                }

                                // 2. Delete branch
                                val deleteResponse = RetrofitClient.instance.deleteBranch(branch.brnchId)
                                if (deleteResponse.isSuccessful) {
                                    Toast.makeText(this@AdminDashboardActivity, "✅ Branch successfully deleted!", Toast.LENGTH_SHORT).show()
                                    loadDashboardStats()
                                    loadCurrentViewData()
                                } else {
                                    Toast.makeText(this@AdminDashboardActivity, "Error deleting branch: ${deleteResponse.code()}", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(this@AdminDashboardActivity, "Connection error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            } finally {
                                binding.swipeRefresh.isRefreshing = false
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } else {
            dialogBinding.btnDelete.visibility = View.GONE
        }

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnSave.setOnClickListener {
            val name = dialogBinding.etBranchName.text?.toString()?.trim() ?: ""
            val city = dialogBinding.spBranchCity.text?.toString()?.trim() ?: ""
            val type = dialogBinding.spBranchType.selectedItem.toString()
            val managerPosition = dialogBinding.spBranchManager.selectedItemPosition

            if (name.isEmpty() || city.isEmpty()) {
                Toast.makeText(this, "All fields are required!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialog.dismiss()
            binding.swipeRefresh.isRefreshing = true

            lifecycleScope.launch {
                try {
                    if (isEdit) {
                        // Patch
                        val patchResponse = RetrofitClient.instance.addBranch(branch!!.brnchId, Branch(
                            brnchId = branch.brnchId,
                            brnchName = name,
                            brnchCity = city,
                            brnchStreet = branch.brnchStreet,
                            brnchProvince = branch.brnchProvince,
                            brnchPostalCode = branch.brnchPostalCode,
                            brnchContactNumber = branch.brnchContactNumber,
                            brnchOpeningTime = branch.brnchOpeningTime,
                            brnchClosingTime = branch.brnchClosingTime,
                            brnchType = type
                        ))
                        if (patchResponse.isSuccessful) {
                            // Update manager assignment
                            if (managerPosition > 0) {
                                val selectedManager = selectableManagers[managerPosition - 1]
                                if (currentManager == null) {
                                    assignBranchManagerToBranch(selectedManager.stfId, branch.brnchId)
                                } else if (selectedManager.stfId != currentManager.stfId) {
                                    assignBranchManagerToBranch(currentManager.stfId, null)
                                    assignBranchManagerToBranch(selectedManager.stfId, branch.brnchId)
                                }
                            } else {
                                if (currentManager != null) {
                                    assignBranchManagerToBranch(currentManager.stfId, null)
                                }
                            }

                            Toast.makeText(this@AdminDashboardActivity, "Branch successfully updated!", Toast.LENGTH_SHORT).show()
                            loadDashboardStats()
                            loadCurrentViewData()
                        }
                    } else {
                        // Adding
                        val newId = globalMaxBranchId + 1
                        val newBranch = Branch(
                            brnchId = newId,
                            brnchName = name,
                            brnchStreet = "N/A",
                            brnchCity = city,
                            brnchProvince = "N/A",
                            brnchPostalCode = "N/A",
                            brnchContactNumber = "N/A",
                            brnchOpeningTime = "08:00 AM",
                            brnchClosingTime = "08:00 PM",
                            brnchType = type
                        )
                        val addResponse = RetrofitClient.instance.addBranch(newId, newBranch)
                        if (addResponse.isSuccessful) {
                            if (managerPosition > 0) {
                                val selectedManager = selectableManagers[managerPosition - 1]
                                assignBranchManagerToBranch(selectedManager.stfId, newId)
                            }
                            Toast.makeText(this@AdminDashboardActivity, "New branch created successfully!", Toast.LENGTH_SHORT).show()
                            loadDashboardStats()
                            loadCurrentViewData()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@AdminDashboardActivity, "Error saving branch: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }

        dialog.show()
    }

    private fun assignBranchManagerToBranch(staffId: Int, branchId: Int?) {
        lifecycleScope.launch {
            try {
                val staffListRes = RetrofitClient.instance.getAllStaff()
                if (staffListRes.isSuccessful) {
                    val body = staffListRes.body()
                    val targetKey = body?.entries?.find { it.value.stfId == staffId }?.key
                    if (targetKey != null) {
                        val updates = mapOf<String, Any?>(
                            "Stf_Brch_Id" to branchId
                        )
                        RetrofitClient.instance.updateStaffStatus(targetKey, updates as Map<String, Any>)
                    }
                }
            } catch (e: Exception) {
                // Fail silently
            }
        }
    }

    private fun filterStaffList(query: String) {
        if (currentViewMode != ViewMode.STAFF) return
        val filteredList = if (query.trim().isEmpty()) {
            allStaffList
        } else {
            allStaffList.filter { staff ->
                val fullName = "${staff.stfFname ?: ""} ${staff.stfLname ?: ""}".lowercase()
                fullName.contains(query.lowercase()) || (staff.stfEmail ?: "").lowercase().contains(query.lowercase())
            }
        }
        staffAdapter.submitList(filteredList, allBranchesList)
    }

    // ================= STAFF DIALOGS =================
    private fun showAddEditStaffDialog(staff: Staff?) {
        val dialogBinding = DialogAddEditStaffBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        val isEdit = staff != null
        dialogBinding.tvDialogTitle.text = if (isEdit) "Edit Staff Member" else "Add Staff Member"

        // Setup Role Spinner
        val roles = listOf("admin", "branch_manager")
        val roleAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
        dialogBinding.spStaffRole.adapter = roleAdapter

        // Toggle Branch visibility based on role selection
        dialogBinding.spStaffRole.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedRole = roles[position]
                if (selectedRole == "admin") {
                    dialogBinding.tvStaffBranchLabel.visibility = View.GONE
                    dialogBinding.cardStaffBranch.visibility = View.GONE
                } else {
                    dialogBinding.tvStaffBranchLabel.visibility = View.VISIBLE
                    dialogBinding.cardStaffBranch.visibility = View.VISIBLE
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Setup Branches Spinner
        val branchNames = allBranchesList.map { it.brnchName }
        val branchAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, branchNames)
        dialogBinding.spStaffBranch.adapter = branchAdapter

        if (isEdit) {
            dialogBinding.etStaffFname.setText(staff!!.stfFname ?: "")
            dialogBinding.etStaffLname.setText(staff.stfLname ?: "")
            dialogBinding.etStaffEmail.setText(staff.stfEmail ?: "")
            dialogBinding.etStaffPhone.setText(staff.stfContactNumber ?: "")

            val roleIndex = roles.indexOf((staff.stfRole ?: "staff").lowercase())
            if (roleIndex != -1) {
                dialogBinding.spStaffRole.setSelection(roleIndex)
            }

            val matchingBranch = allBranchesList.find { it.brnchId == staff.stfBrchId }
            if (matchingBranch != null) {
                val branchIndex = branchNames.indexOf(matchingBranch.brnchName)
                if (branchIndex != -1) {
                    dialogBinding.spStaffBranch.setSelection(branchIndex)
                }
            }

            val isSelf = (staff.stfId == sessionManager.getUserId())
            if (isSelf) {
                dialogBinding.tvDialogTitle.text = "Staff Member (Self - Read Only)"
                dialogBinding.etStaffFname.isEnabled = false
                dialogBinding.etStaffLname.isEnabled = false
                dialogBinding.etStaffEmail.isEnabled = false
                dialogBinding.etStaffPhone.isEnabled = false
                dialogBinding.etStaffPassword.isEnabled = false
                dialogBinding.spStaffRole.isEnabled = false
                dialogBinding.spStaffBranch.isEnabled = false
                
                dialogBinding.btnSave.visibility = View.GONE
                dialogBinding.btnDelete.visibility = View.GONE
                dialogBinding.btnCancel.text = "Close"
                
                Toast.makeText(this@AdminDashboardActivity, "ℹ️ You cannot modify your own profile here. Use Settings to edit profile.", Toast.LENGTH_LONG).show()
            } else {
                // Setup Delete button
                dialogBinding.btnDelete.visibility = View.VISIBLE
                dialogBinding.btnDelete.setOnClickListener {
                    if (staff.stfId == sessionManager.getUserId()) {
                        Toast.makeText(this@AdminDashboardActivity, "❌ Error: You cannot delete your own account.", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }

                MaterialAlertDialogBuilder(this@AdminDashboardActivity)
                    .setTitle("Delete Staff Member")
                    .setMessage("Are you sure you want to delete this employee account?")
                    .setPositiveButton("Delete") { _, _ ->
                        dialog.dismiss()
                        binding.swipeRefresh.isRefreshing = true
                        lifecycleScope.launch {
                            try {
                                val deleteResponse = RetrofitClient.instance.deleteStaff(staff.stfId)
                                if (deleteResponse.isSuccessful) {
                                    Toast.makeText(this@AdminDashboardActivity, "✅ Staff successfully deleted!", Toast.LENGTH_SHORT).show()
                                    loadDashboardStats()
                                    loadCurrentViewData()
                                } else {
                                    Toast.makeText(this@AdminDashboardActivity, "Error deleting staff: ${deleteResponse.code()}", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(this@AdminDashboardActivity, "Connection error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            } finally {
                                binding.swipeRefresh.isRefreshing = false
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            }
        } else {
            dialogBinding.btnDelete.visibility = View.GONE
        }

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnSave.setOnClickListener {
            val fname = dialogBinding.etStaffFname.text?.toString()?.trim() ?: ""
            val lname = dialogBinding.etStaffLname.text?.toString()?.trim() ?: ""
            val email = dialogBinding.etStaffEmail.text?.toString()?.trim() ?: ""
            val phone = dialogBinding.etStaffPhone.text?.toString()?.trim() ?: ""
            val password = dialogBinding.etStaffPassword.text?.toString()?.trim() ?: ""

            if (fname.isEmpty() || lname.isEmpty() || email.isEmpty() || phone.isEmpty() || (!isEdit && password.isEmpty())) {
                Toast.makeText(this, "Please fill in all required fields!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (phone.length != 11) {
                dialogBinding.etStaffPhone.error = "Must be 11 digits"
                return@setOnClickListener
            }

            val selectedRole = dialogBinding.spStaffRole.selectedItem.toString()
            val selectedBranchId = if (selectedRole == "admin") null else {
                val selectedBranchName = dialogBinding.spStaffBranch.selectedItem?.toString() ?: ""
                allBranchesList.find { it.brnchName == selectedBranchName }?.brnchId
            }

            dialog.dismiss()
            binding.swipeRefresh.isRefreshing = true

            lifecycleScope.launch {
                try {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val createdAtStr = dateFormat.format(Date())

                    if (isEdit) {
                        val hashedPassword = if (password.isNotEmpty()) {
                            BCrypt.hashpw(password, BCrypt.gensalt(10)).replaceFirst("$2a$", "$2y$")
                        } else {
                            staff!!.stfPasswordHash
                        }

                        val updatedStaff = Staff(
                            stfId = staff!!.stfId,
                            stfEmail = email,
                            stfPasswordHash = hashedPassword,
                            stfFname = fname,
                            stfLname = lname,
                            stfRole = selectedRole,
                            stfContactNumber = phone,
                            stfBrchId = selectedBranchId,
                            stfIsActive = staff.stfIsActive,
                            stfCreatedAt = staff.stfCreatedAt
                        )

                        val editResponse = RetrofitClient.instance.addStaff(staff.stfId, updatedStaff)
                        if (editResponse.isSuccessful) {
                            Toast.makeText(this@AdminDashboardActivity, "Staff details updated!", Toast.LENGTH_SHORT).show()
                            loadDashboardStats()
                            loadCurrentViewData()
                        }
                    } else {
                        // Check email duplicate first
                        val duplicate = allStaffList.any { it.stfEmail.equals(email, ignoreCase = true) }
                        if (duplicate) {
                            Toast.makeText(this@AdminDashboardActivity, "Email already exists in staff directory!", Toast.LENGTH_LONG).show()
                            return@launch
                        }

                        val newId = globalMaxStaffId + 1
                        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(10)).replaceFirst("$2a$", "$2y$")

                        val newStaff = Staff(
                            stfId = newId,
                            stfEmail = email,
                            stfPasswordHash = hashedPassword,
                            stfFname = fname,
                            stfLname = lname,
                            stfRole = selectedRole,
                            stfContactNumber = phone,
                            stfBrchId = selectedBranchId,
                            stfIsActive = 1,
                            stfCreatedAt = createdAtStr
                        )

                        val addResponse = RetrofitClient.instance.addStaff(newId, newStaff)
                        if (addResponse.isSuccessful) {
                            Toast.makeText(this@AdminDashboardActivity, "New staff added successfully!", Toast.LENGTH_SHORT).show()
                            loadDashboardStats()
                            loadCurrentViewData()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@AdminDashboardActivity, "Error saving staff: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }

        dialog.show()
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

    private fun showBranchFilterDialog() {
        val branchNames = mutableListOf("All Branches (Clear Filter)")
        branchNames.addAll(allBranchesList.map { it.brnchName })

        MaterialAlertDialogBuilder(this)
            .setTitle("Filter Logs by Branch")
            .setItems(branchNames.toTypedArray()) { _, which ->
                selectedFilterBranchId = if (which == 0) {
                    null
                } else {
                    allBranchesList[which - 1].brnchId
                }
                loadCurrentViewData()
            }
            .show()
    }

    private fun showLogDetailsDialog(
        log: ParcelStatusHistory,
        parcel: Parcel?,
        staff: Staff?,
        branch: Branch?
    ) {
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle("📄 Log Transaction Details")

        val detailView = LayoutInflater.from(this).inflate(R.layout.dialog_log_details, null)
        val tvTracking = detailView.findViewById<TextView>(R.id.tvDetailsTracking)
        val tvTimestamp = detailView.findViewById<TextView>(R.id.tvDetailsTimestamp)
        val tvStatus = detailView.findViewById<TextView>(R.id.tvDetailsStatus)
        val tvPersonnel = detailView.findViewById<TextView>(R.id.tvDetailsPersonnel)
        val tvBranch = detailView.findViewById<TextView>(R.id.tvDetailsBranch)
        val tvRemarks = detailView.findViewById<TextView>(R.id.tvDetailsRemarks)
        val tvCod = detailView.findViewById<TextView>(R.id.tvDetailsCod)
        val tvWeight = detailView.findViewById<TextView>(R.id.tvDetailsWeight)
        val tvReceiver = detailView.findViewById<TextView>(R.id.tvDetailsReceiver)
        val tvAddress = detailView.findViewById<TextView>(R.id.tvDetailsAddress)

        tvTracking.text = parcel?.parclTrackingNumber ?: "DDX-${log.histParclId}"
        tvTimestamp.text = log.histTimestamp
        tvStatus.text = log.histStatus
        
        tvPersonnel.text = if (staff != null) {
            "${staff.stfFname} ${staff.stfLname} (${staff.stfRole})"
        } else {
            "System Auto"
        }
        
        tvBranch.text = branch?.brnchName ?: "Automated Hub"
        tvRemarks.text = log.histRemark ?: log.histLocationNote ?: "N/A"
        
        tvCod.text = if (parcel != null) "₱${String.format(Locale.US, "%.2f", parcel.parclCodAmount)}" else "N/A"
        tvWeight.text = if (parcel != null) "${String.format(Locale.US, "%.1f", parcel.parclWeightKg)} kg" else "N/A"
        tvReceiver.text = if (parcel != null) "${parcel.parclReceiverName} (${parcel.parclReceiverPhone})" else "N/A"
        
        tvAddress.text = if (parcel != null) {
            val addressParts = listOfNotNull(
                parcel.parclReceiverStreet?.takeIf { it.isNotEmpty() },
                parcel.parclReceiverCity?.takeIf { it.isNotEmpty() },
                parcel.parclReceiverProvince?.takeIf { it.isNotEmpty() },
                parcel.parclReceiverPostalCode?.takeIf { it.isNotEmpty() }
            )
            if (addressParts.isNotEmpty()) addressParts.joinToString(", ") else parcel.parclReceiverAddress ?: "N/A"
        } else {
            "N/A"
        }

        builder.setView(detailView)
            .setPositiveButton("Close", null)
            .show()
    }

    // ================= ADAPTER FOR BRANCHES =================
    private inner class BranchAdapter(
        private val onEditClicked: (Branch) -> Unit
    ) : RecyclerView.Adapter<BranchAdapter.BranchViewHolder>() {

        private var items = listOf<Branch>()

        fun submitList(newItems: List<Branch>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BranchViewHolder {
            val binding = ItemAdminBranchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return BranchViewHolder(binding)
        }

        override fun onBindViewHolder(holder: BranchViewHolder, position: Int) = holder.bind(items[position])

        override fun getItemCount(): Int = items.size

        inner class BranchViewHolder(private val binding: ItemAdminBranchBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: Branch) {
                binding.tvBranchName.text = item.brnchName
                binding.tvBranchCity.text = item.brnchCity ?: "N/A"
                binding.tvBranchType.text = item.brnchType?.uppercase() ?: "HUB"

                binding.btnEditBranch.setOnClickListener { onEditClicked(item) }
            }
        }
    }

    // ================= ADAPTER FOR STAFF =================
    private inner class StaffAdapter(
        private val onEditClicked: (Staff) -> Unit
    ) : RecyclerView.Adapter<StaffAdapter.StaffViewHolder>() {

        private var items = listOf<Staff>()
        private var branches = listOf<Branch>()

        fun submitList(newItems: List<Staff>, newBranches: List<Branch>) {
            items = newItems
            branches = newBranches
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StaffViewHolder {
            val binding = ItemAdminStaffBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return StaffViewHolder(binding)
        }

        override fun onBindViewHolder(holder: StaffViewHolder, position: Int) = holder.bind(items[position])

        override fun getItemCount(): Int = items.size

        inner class StaffViewHolder(private val binding: ItemAdminStaffBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: Staff) {
                binding.tvStaffName.text = "${item.stfFname ?: ""} ${item.stfLname ?: ""}".trim().ifEmpty { "Unnamed Staff" }
                binding.tvStaffEmail.text = item.stfEmail ?: "No Email"
                binding.tvStaffPhone.text = item.stfContactNumber ?: "N/A"
                binding.tvStaffRole.text = (item.stfRole ?: "STAFF").uppercase()

                val branch = branches.find { it.brnchId == item.stfBrchId }
                binding.tvStaffBranch.text = "Branch: ${branch?.brnchName ?: "Unassigned / Global"}"

                binding.btnEditStaff.setOnClickListener { onEditClicked(item) }
            }
        }
    }
}
