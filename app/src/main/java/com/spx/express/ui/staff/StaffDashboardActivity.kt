package com.spx.express.ui.staff

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.spx.express.R
import com.spx.express.data.model.Branch
import com.spx.express.data.model.Parcel
import com.spx.express.data.model.Staff
import com.spx.express.data.storage.SessionManager
import com.spx.express.databinding.ActivityStaffDashboardBinding
import com.spx.express.databinding.DialogDispatchOptionsBinding
import com.spx.express.databinding.DialogReceiveScanBinding
import com.spx.express.databinding.DialogRejectReasonBinding
import com.spx.express.ui.LoginActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class StaffDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStaffDashboardBinding
    private lateinit var viewModel: StaffDashboardViewModel
    private lateinit var sessionManager: SessionManager
    private lateinit var parcelAdapter: StaffParcelAdapter

    private var currentBranchId: Int = -1
    private var loggedInStaffId: Int = -1
    private var currentTabPosition = 0 // 0 = Incoming Queue, 1 = Active Inventory

    private var localRidersList = listOf<Staff>()
    private var branchesCache = mapOf<Int, Branch>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStaffDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[StaffDashboardViewModel::class.java]

        sessionManager = SessionManager(this)
        loggedInStaffId = sessionManager.getUserId()

        // 1. Session check
        if (!sessionManager.isLoggedIn() || sessionManager.getUserRole()?.lowercase() != "staff") {
            redirectToLogin()
            return
        }

        // We need the staff's branch ID. Let's load it dynamically.
        // We will query the staff details first using email or fetch from SessionManager.
        // Wait, SessionManager has getUserEmail. We can resolve the branch ID once staff list is fetched, 
        // but for now, let's load staff list and identify the logged-in staff member.
        // To be safe and simple: let's launch a coroutine or fetch in the viewmodel. 
        // In the PHP database: staff has Stf_Brch_Id. We will retrieve the branch ID by matching logged-in stfId.
        val staffEmail = sessionManager.getUserEmail() ?: "staff@spx.com"

        // Setup Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Populate Custom Welcome Header
        findViewById<TextView>(R.id.tvToolbarGreeting).text = "Hello Operator,"
        findViewById<TextView>(R.id.tvToolbarName).text = sessionManager.getUserName() ?: "Branch Operator"
        findViewById<TextView>(R.id.tvToolbarRole).apply {
            text = "Staff Operator"
            setTextColor(android.graphics.Color.parseColor("#2563EB")) // Elegant Blue
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cvToolbarBadge).apply {
            setCardBackgroundColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#EFF6FF")))
        }

        // Setup Drawer Values
        binding.tvDrawerName.text = sessionManager.getUserName() ?: "Branch Operator"
        binding.tvDrawerEmail.text = staffEmail

        // Setup Recycler Adapter
        setupRecyclerView()

        // Setup Tabs
        setupTabLayout()

        // Setup Observers & Listeners
        setupListeners()
        setupObservers()

        // Fetch staff profile to resolve currentBranchId and start real-time sync
        resolveStaffBranchAndSync(staffEmail)

        // Prompt for logout on back pressed instead of closing directly
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showSignOutConfirmation()
            }
        })
    }

    private fun setupRecyclerView() {
        parcelAdapter = StaffParcelAdapter(
            onAcceptClicked = { parcel -> acceptParcel(parcel) },
            onDeclineClicked = { parcel -> showDeclineDialog(parcel) },
            onDispatchClicked = { parcel -> showDispatchDialog(parcel) },
            onDetailsClicked = { parcel -> showParcelDetailsDialog(parcel) }
        )
        binding.rvParcels.layoutManager = LinearLayoutManager(this)
        binding.rvParcels.adapter = parcelAdapter
    }

    private fun showParcelDetailsDialog(parcel: Parcel) {
        val addressParts = listOfNotNull(
            parcel.parclReceiverStreet,
            parcel.parclReceiverCity,
            parcel.parclReceiverProvince,
            parcel.parclReceiverPostalCode
        )
        val fullAddress = if (addressParts.isNotEmpty()) {
            addressParts.joinToString(", ")
        } else {
            parcel.parclReceiverAddress ?: "N/A"
        }

        val declaredValStr = if (parcel.parclDeclaredValue != null && parcel.parclDeclaredValue > 0.0) {
            "<b>Declared Value:</b> ₱${String.format(Locale.US, "%.2f", parcel.parclDeclaredValue)}<br/>"
        } else ""

        val codStr = if (parcel.parclCodAmount > 0.0) {
            "<b>COD Amount:</b> <font color=\"#EE4D2D\">₱${String.format(Locale.US, "%.2f", parcel.parclCodAmount)}</font><br/>"
        } else ""

        val nextHopName = if (parcel.parclNextHopBrchId != null) {
            branchesCache[parcel.parclNextHopBrchId]?.brnchName ?: "Branch #${parcel.parclNextHopBrchId}"
        } else ""

        val nextHopStr = if (parcel.parclNextHopBrchId != null) {
            "<b>Next Hop Hub:</b> $nextHopName<br/>"
        } else ""

        val origHubName = if (parcel.parclOrigBrchId != null) {
            branchesCache[parcel.parclOrigBrchId]?.brnchName ?: "Branch #${parcel.parclOrigBrchId}"
        } else "N/A"

        val destHubName = if (parcel.parclDestBrchId != null) {
            branchesCache[parcel.parclDestBrchId]?.brnchName ?: "Branch #${parcel.parclDestBrchId}"
        } else "N/A"

        val riderStr = if (parcel.parclRiderId != null) {
            "<b>Assigned Rider ID:</b> #${parcel.parclRiderId}<br/>"
        } else ""

        val htmlText = """
            <b>Tracking Number:</b> <font color="#DD6B20">${parcel.parclTrackingNumber}</font><br/>
            <b>Status:</b> ${parcel.parclDeliveryStatus}<br/>
            <b>Weight:</b> ${parcel.parclWeightKg} kg<br/>
            $declaredValStr
            $codStr
            <b>Booking ID:</b> #${parcel.parclBkngId}<br/>
            <br/>
            <b>👤 RECIPIENT DETAILS:</b><br/>
            <b>Name:</b> ${parcel.parclReceiverName}<br/>
            <b>Phone:</b> ${parcel.parclReceiverPhone ?: "N/A"}<br/>
            <b>Address:</b> $fullAddress<br/>
            <br/>
            <b>🏢 HUB ROUTING:</b><br/>
            <b>Origin Hub:</b> $origHubName<br/>
            <b>Destination Hub:</b> $destHubName<br/>
            $nextHopStr
            $riderStr
        """.trimIndent()

        MaterialAlertDialogBuilder(this)
            .setTitle("Parcel Specifications")
            .setMessage(android.text.Html.fromHtml(htmlText, android.text.Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton("Dismiss", null)
            .show()
    }

    private fun setupTabLayout() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("📥 Incoming Queue"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("📦 Active Inventory"))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTabPosition = tab?.position ?: 0
                updateListForTab()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            if (currentBranchId != -1) {
                viewModel.refreshDashboard(currentBranchId)
            } else {
                binding.swipeRefresh.isRefreshing = false
            }
        }

        binding.fabReceive.setOnClickListener {
            showQuickReceiveDialog()
        }

        // Sidebar Navigation links
        binding.navOverview.setOnClickListener {
            closeDrawer()
        }

        binding.navScanReceive.setOnClickListener {
            closeDrawer()
            showQuickReceiveDialog()
        }

        binding.navProcessPayment.setOnClickListener {
            closeDrawer()
            showProcessPaymentsDialog()
        }

        binding.navCodRemittances.setOnClickListener {
            closeDrawer()
            showCodRemittancesDialog()
        }

        binding.btnDrawerSignOut.setOnClickListener {
            closeDrawer()
            showSignOutConfirmation()
        }
    }

    private fun setupObservers() {
        // Observe status counts to update stats card
        viewModel.incomingParcels.observe(this) { incoming ->
            binding.tvIncomingCount.text = incoming.size.toString()
            if (currentTabPosition == 0) {
                parcelAdapter.submitList(incoming, inventoryMode = false)
            }
        }

        viewModel.activeParcels.observe(this) { active ->
            binding.tvInventoryCount.text = active.size.toString()
            if (currentTabPosition == 1) {
                parcelAdapter.submitList(active, inventoryMode = true)
            }
        }

        viewModel.localRiders.observe(this) { riders ->
            localRidersList = riders
        }

        viewModel.branches.observe(this) { branches ->
            branchesCache = branches
        }

        viewModel.dashboardState.observe(this) { state ->
            when (state) {
                is StaffDashboardState.Loading -> {
                    // Show a light overlay or swiperefresh circle
                    binding.swipeRefresh.isRefreshing = true
                }
                is StaffDashboardState.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
                is StaffDashboardState.Error -> {
                    binding.swipeRefresh.isRefreshing = false
                    showErrorAlertDialog(state.message)
                }
                is StaffDashboardState.Idle -> {
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private fun resolveStaffBranchAndSync(email: String) {
        binding.swipeRefresh.isRefreshing = true
        // Fetch all staff to resolve branch
        lifecycleScope.launch {
            try {
                val response = com.spx.express.data.api.RetrofitClient.instance.getAllStaff()
                if (response.isSuccessful) {
                    val staffMap = response.body()
                    if (staffMap != null) {
                        val matchingStaff = staffMap.values.find { it?.stfEmail == email }
                        if (matchingStaff != null) {
                            currentBranchId = matchingStaff.stfBrchId ?: 1
                            supportActionBar?.subtitle = "Branch Hub ID: #$currentBranchId"
                            
                            // Initialize polling and initial load
                            viewModel.startRealtimeSync(currentBranchId)
                            viewModel.refreshDashboard(currentBranchId)
                        } else {
                            showErrorAlertDialog("Could not resolve Branch Profile details.")
                        }
                    }
                } else {
                    showErrorAlertDialog("Connection failed. Try again later.")
                }
            } catch (e: Exception) {
                showErrorAlertDialog("Error: ${e.localizedMessage}")
            }
        }
    }

    private fun updateListForTab() {
        if (currentTabPosition == 0) {
            viewModel.incomingParcels.value?.let {
                parcelAdapter.submitList(it, inventoryMode = false)
            }
        } else {
            viewModel.activeParcels.value?.let {
                parcelAdapter.submitList(it, inventoryMode = true)
            }
        }
    }

    // --- DIALOGS & MUTATIONS ---

    // 1. Accept parcel
    private fun acceptParcel(parcel: Parcel) {
        viewModel.acceptParcel(parcel, currentBranchId, loggedInStaffId)
    }

    // 2. Decline parcel
    private fun showDeclineDialog(parcel: Parcel) {
        val dialogBinding = DialogRejectReasonBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancelDecline.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSubmitDecline.setOnClickListener {
            val reason = dialogBinding.etDeclineReason.text?.toString()?.trim() ?: ""
            if (reason.isEmpty()) {
                dialogBinding.etDeclineReason.error = "Decline reason is mandatory"
                return@setOnClickListener
            }
            dialog.dismiss()
            viewModel.declineParcel(parcel, reason, currentBranchId, loggedInStaffId)
        }

        dialog.show()
    }

    // 3. Dispatch cargo parcel sheet
    private fun showDispatchDialog(parcel: Parcel) {
        if (localRidersList.isEmpty()) {
            Toast.makeText(this, "⚠️ No active local riders registered at this branch.", Toast.LENGTH_LONG).show()
            return
        }

        val dialogBinding = DialogDispatchOptionsBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        // Populate fields
        dialogBinding.tvParcelTrackingNo.text = "Tracking No: ${parcel.parclTrackingNumber}"
        dialogBinding.tvParcelWeight.text = "Parcel Weight: ${parcel.parclWeightKg} kg"

        // Calculate routing paths
        val destBranchId = parcel.parclDestBrchId ?: currentBranchId
        val nextHopId = RoutingHelper.calculateNextHop(currentBranchId, destBranchId)

        val currentName = branchesCache[currentBranchId]?.brnchName ?: "Branch #$currentBranchId"
        val nextHopName = branchesCache[nextHopId]?.brnchName ?: "Branch #$nextHopId"
        val destName = branchesCache[destBranchId]?.brnchName ?: "Branch #$destBranchId"

        dialogBinding.tvRoutingPath.text = if (nextHopId == currentBranchId) {
            "Route: $currentName (Local Delivery)"
        } else {
            "Route: $currentName ➔ $nextHopName ➔ $destName"
        }

        dialogBinding.tvRoutingType.text = if (nextHopId == currentBranchId) {
            "Dispatch Mode: Local Rider Assignment"
        } else {
            "Dispatch Mode: Hub Transfer route"
        }

        // Rider Spinner details
        val riderStrings = localRidersList.map { rider ->
            val load = viewModel.getRiderCurrentLoad(rider.stfId)
            "${rider.stfFname} ${rider.stfLname} [Current Load: $load / 25.0 kg]"
        }
        val spinnerAdapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, riderStrings)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spRiders.adapter = spinnerAdapter

        // Heavy cargo validation
        if (parcel.parclWeightKg > 10.0) {
            dialogBinding.llWeightWarningBlock.visibility = View.VISIBLE
        } else {
            dialogBinding.llWeightWarningBlock.visibility = View.GONE
        }

        dialogBinding.btnCancelDispatch.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnCompleteDispatch.setOnClickListener {
            val selectedIndex = dialogBinding.spRiders.selectedItemPosition
            if (selectedIndex == -1) {
                Toast.makeText(this, "Please select a rider", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selectedRider = localRidersList[selectedIndex]
            val overrideChecked = dialogBinding.swSupervisorOverride.isChecked

            dialog.dismiss()
            viewModel.dispatchParcel(
                parcel = parcel,
                riderId = selectedRider.stfId,
                supervisorOverrideChecked = overrideChecked,
                branchId = currentBranchId,
                staffId = loggedInStaffId
            )
        }

        dialog.show()
    }

    // 4. Quick Receive waybill barcode scanner dialog
    private fun showQuickReceiveDialog() {
        val dialogBinding = DialogReceiveScanBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        // Animated scan line effect
        dialogBinding.vScannerLaser.post {
            val dpHeight = 180 * resources.displayMetrics.density
            val animation = TranslateAnimation(0f, 0f, 0f, dpHeight).apply {
                duration = 2000
                repeatCount = Animation.INFINITE
                repeatMode = Animation.REVERSE
            }
            dialogBinding.vScannerLaser.startAnimation(animation)
        }

        dialogBinding.btnCancelScan.setOnClickListener {
            dialogBinding.vScannerLaser.clearAnimation()
            dialog.dismiss()
        }

        dialogBinding.btnVerifyReceive.setOnClickListener {
            val input = dialogBinding.etBarcode.text?.toString()?.trim() ?: ""
            if (input.isEmpty() || input == "DDX-") {
                dialogBinding.etBarcode.error = "Please enter tracking number"
                return@setOnClickListener
            }
            dialogBinding.vScannerLaser.clearAnimation()
            dialog.dismiss()
            viewModel.receiveScannedParcel(input, currentBranchId, loggedInStaffId)
        }

        dialog.show()
    }

    private fun showProcessPaymentsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_process_payments, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        val rvPayments: RecyclerView = dialogView.findViewById(R.id.rvPendingPayments)
        val btnClose: View = dialogView.findViewById(R.id.btnClosePayments)

        rvPayments.layoutManager = LinearLayoutManager(this)
        
        val paymentAdapter = StaffPaymentAdapter(
            onConfirmClicked = { paymentDetails ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Confirm Payment Collection")
                    .setMessage("Confirm payment receipt of ₱${String.format(Locale.US, "%.2f", paymentDetails.payment.pymtAmount)} for tracking ID: ${paymentDetails.parcel.parclTrackingNumber}?")
                    .setPositiveButton("Confirm") { _, _ ->
                        viewModel.confirmPayment(paymentDetails, loggedInStaffId, currentBranchId)
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        rvPayments.adapter = paymentAdapter

        val llEmpty: View = dialogView.findViewById(R.id.llEmptyPayments)

        // Observe pending payments LiveData from ViewModel
        viewModel.pendingPayments.observe(this) { pendingList ->
            paymentAdapter.submitList(pendingList)
            if (pendingList.isNullOrEmpty()) {
                rvPayments.visibility = View.GONE
                llEmpty.visibility = View.VISIBLE
            } else {
                rvPayments.visibility = View.VISIBLE
                llEmpty.visibility = View.GONE
            }
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showCodRemittancesDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_cod_remittances, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        val rvRemittances: RecyclerView = dialogView.findViewById(R.id.rvCodRemittances)
        val btnClose: View = dialogView.findViewById(R.id.btnCloseCodRemittances)
        val llEmpty: View = dialogView.findViewById(R.id.llEmptyCodRemittances)

        rvRemittances.layoutManager = LinearLayoutManager(this)

        val remittanceAdapter = StaffCodRemittanceAdapter { parcel ->
            MaterialAlertDialogBuilder(this)
                .setTitle("Confirm COD Receipt")
                .setMessage("Confirm receipt of \u20b1${String.format(Locale.US, "%.2f", parcel.parclCodAmount)} remitted by rider for parcel: ${parcel.parclTrackingNumber}?")
                .setPositiveButton("Confirm") { _, _ ->
                    viewModel.confirmRemittance(parcel, loggedInStaffId, currentBranchId)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        rvRemittances.adapter = remittanceAdapter

        viewModel.pendingRemittances.observe(this) { remittances ->
            remittanceAdapter.submitList(remittances)
            if (remittances.isNullOrEmpty()) {
                rvRemittances.visibility = View.GONE
                llEmpty.visibility = View.VISIBLE
            } else {
                rvRemittances.visibility = View.VISIBLE
                llEmpty.visibility = View.GONE
            }
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // --- SIDE NAVIGATION UTILITIES ---
    private fun toggleDrawer() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.END)) {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
        } else {
            binding.drawerLayout.openDrawer(GravityCompat.END)
        }
    }

    private fun closeDrawer() {
        binding.drawerLayout.closeDrawer(GravityCompat.END)
    }

    private fun showSignOutConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Confirm Logout")
            .setMessage("Are you sure you want to log out of the Staff operations dashboard?")
            .setPositiveButton("Log Out") { _, _ ->
                sessionManager.clearSession()
                redirectToLogin()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun showErrorAlertDialog(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Operational Alert")
            .setMessage(message)
            .setPositiveButton("Dismiss", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopRealtimeSync()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        val item = menu.add(0, 100, 0, "Menu")
        item.setIcon(R.drawable.ic_hamburger)
        item.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == 100) {
            toggleDrawer()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
