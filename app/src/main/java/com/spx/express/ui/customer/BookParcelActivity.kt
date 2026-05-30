package com.spx.express.ui.customer

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import android.widget.TextView
import com.spx.express.R
import com.spx.express.data.storage.SessionManager
import com.spx.express.data.api.RetrofitClient
import com.spx.express.data.model.Booking
import com.spx.express.data.model.Branch
import com.spx.express.data.model.Parcel
import com.spx.express.data.model.ParcelStatusHistory
import com.spx.express.data.model.Payment
import com.spx.express.databinding.ActivityBookParcelBinding
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class BookParcelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookParcelBinding
    private lateinit var sessionManager: SessionManager

    private var currentStep = 1

    private var branchList = listOf<Branch>()
    private var selectedBranchId = 1
    
    private var calculatedShippingFee = 50.0
    private var totalCollectAmount = 50.0
    private var selectedPaymentMethod = "Cash"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookParcelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        // Setup Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Book New Shipment"

        setupDropdowns()
        setupListeners()
        updateStepViews()
        
        // Fetch drop-off branches from Firebase
        loadBranches()
    }

    private fun loadBranches() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getAllBranches()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        branchList = body.values.filterNotNull()
                        val branchNames = branchList.map { it.brnchName }
                        val branchAdapter = NoFilterArrayAdapter(this@BookParcelActivity, android.R.layout.simple_dropdown_item_1line, branchNames)
                        binding.spDropoffBranch.setAdapter(branchAdapter)

                        // Default select first branch
                        if (branchList.isNotEmpty()) {
                            binding.spDropoffBranch.setText(branchList[0].brnchName, false)
                            selectedBranchId = branchList[0].brnchId
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@BookParcelActivity, "Failed to load drop-off points.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupDropdowns() {
        // Payment methods
        val methods = listOf("Cash", "GCash", "Maya", "Cash on Delivery (COD)")
        val methodAdapter = NoFilterArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, methods)
        binding.spPaymentMethod.setAdapter(methodAdapter)
        binding.spPaymentMethod.setText("Cash", false)

        // Provinces dynamic options
        val provinces = LocationHelper.locationsMap.keys.toList()
        val provinceAdapter = NoFilterArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, provinces)
        binding.spReceiverProvince.setAdapter(provinceAdapter)

        binding.spReceiverProvince.setOnItemClickListener { _, _, position, _ ->
            if (position < provinces.size) {
                val selectedProv = provinces[position]
                binding.spReceiverCity.setText("")
                binding.spReceiverBarangay.setText("")
                binding.etReceiverPostal.setText("")
                binding.spReceiverCity.setAdapter(null)
                binding.spReceiverBarangay.setAdapter(null)

                val cities = LocationHelper.locationsMap[selectedProv]?.keys?.toList() ?: emptyList()
                val cityAdapter = NoFilterArrayAdapter(this@BookParcelActivity, android.R.layout.simple_dropdown_item_1line, cities)
                binding.spReceiverCity.setAdapter(cityAdapter)
            }
        }

        binding.spReceiverCity.setOnItemClickListener { _, _, position, _ ->
            val selectedProv = binding.spReceiverProvince.text.toString()
            val cities = LocationHelper.locationsMap[selectedProv]?.keys?.toList() ?: emptyList()
            if (position < cities.size) {
                val selectedCity = cities[position]
                binding.spReceiverBarangay.setText("")

                val cityDetails = LocationHelper.locationsMap[selectedProv]?.get(selectedCity)
                val postalCode = cityDetails?.postal ?: "1000"
                binding.etReceiverPostal.setText(postalCode)

                val barangays = cityDetails?.barangays ?: emptyList()
                val barangayAdapter = NoFilterArrayAdapter(this@BookParcelActivity, android.R.layout.simple_dropdown_item_1line, barangays)
                binding.spReceiverBarangay.setAdapter(barangayAdapter)
            }
        }
    }

    private fun setupListeners() {
        // Drop-off branch listener
        binding.spDropoffBranch.setOnItemClickListener { _, _, position, _ ->
            if (position < branchList.size) {
                selectedBranchId = branchList[position].brnchId
            }
        }

        // Payment method selector
        binding.spPaymentMethod.setOnItemClickListener { _, _, position, _ ->
            val selected = binding.spPaymentMethod.adapter.getItem(position) as String
            selectedPaymentMethod = selected

            when (selected) {
                "GCash", "Maya" -> {
                    // Show reference field, hide COD fields
                    binding.inputLayoutPaymentRef.visibility = View.VISIBLE
                    binding.inputLayoutDeclaredVal.visibility = View.GONE
                    binding.rlTotalCollectRow.visibility = View.GONE
                    binding.rlSummaryCod.visibility = View.GONE
                    binding.rlSummaryTotalCollect.visibility = View.GONE
                    binding.etCod.setText("")
                    totalCollectAmount = 0.0
                    updateCostBreakdown()
                }
                "Cash on Delivery (COD)" -> {
                    // Show COD fields, hide reference
                    binding.inputLayoutPaymentRef.visibility = View.GONE
                    binding.etPaymentRef.setText("")
                    binding.inputLayoutDeclaredVal.visibility = View.VISIBLE
                    binding.rlTotalCollectRow.visibility = View.VISIBLE
                    binding.rlSummaryCod.visibility = View.VISIBLE
                    binding.rlSummaryTotalCollect.visibility = View.VISIBLE
                    updateCostBreakdown()
                }
                else -> {
                    // Cash — hide both
                    binding.inputLayoutPaymentRef.visibility = View.GONE
                    binding.etPaymentRef.setText("")
                    binding.inputLayoutDeclaredVal.visibility = View.GONE
                    binding.rlTotalCollectRow.visibility = View.GONE
                    binding.rlSummaryCod.visibility = View.GONE
                    binding.rlSummaryTotalCollect.visibility = View.GONE
                    binding.etCod.setText("")
                    totalCollectAmount = 0.0
                }
            }
        }

        // Weight text change listener
        binding.etWeight.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateCostBreakdown()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // COD Declared Price change listener
        binding.etCod.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateCostBreakdown()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Back and Next Footer Buttons
        binding.btnBackStep.setOnClickListener {
            if (currentStep > 1) {
                currentStep--
                updateStepViews()
            }
        }

        binding.btnNextStep.setOnClickListener {
            if (validateCurrentStep()) {
                if (currentStep < 4) {
                    currentStep++
                    updateStepViews()
                } else {
                    handleBookParcelConfirm()
                }
            }
        }
    }

    private fun updateCostBreakdown() {
        val weight = binding.etWeight.text?.toString()?.toDoubleOrNull() ?: 0.0
        calculatedShippingFee = if (weight <= 1.0) 50.0 else 50.0 + (weight - 1.0) * 20.0
        
        binding.tvShippingFeeVal.text = "₱${String.format(Locale.US, "%.2f", calculatedShippingFee)}"
        binding.tvSummaryShippingVal.text = "₱${String.format(Locale.US, "%.2f", calculatedShippingFee)}"

        if (selectedPaymentMethod == "Cash on Delivery (COD)") {
            val codVal = binding.etCod.text?.toString()?.toDoubleOrNull() ?: 0.0
            totalCollectAmount = calculatedShippingFee + codVal
            
            binding.tvTotalCollectVal.text = "₱${String.format(Locale.US, "%.2f", totalCollectAmount)}"
            binding.tvSummaryTotalVal.text = "₱${String.format(Locale.US, "%.2f", totalCollectAmount)}"
            binding.tvSummaryCodVal.text = "₱${String.format(Locale.US, "%.2f", codVal)}"
        } else {
            totalCollectAmount = 0.0
        }
    }

    private fun updateStepViews() {
        updateStepIndicator()
        updateStepPanels()
    }

    private fun updateStepIndicator() {
        val activeColor = "#EE4D2D"
        val inactiveColor = "#94A3B8"

        val setBubbleColor = { bubble: View, text: TextView, step: Int ->
            val drawable = bubble.background as? GradientDrawable
            if (currentStep >= step) {
                drawable?.setColor(Color.parseColor(activeColor))
                text.setTextColor(Color.WHITE)
            } else {
                drawable?.setColor(Color.parseColor(inactiveColor))
                text.setTextColor(Color.WHITE)
            }
        }

        setBubbleColor(binding.tvStep1Bubble, binding.tvStep1Bubble, 1)
        setBubbleColor(binding.tvStep2Bubble, binding.tvStep2Bubble, 2)
        setBubbleColor(binding.tvStep3Bubble, binding.tvStep3Bubble, 3)
        setBubbleColor(binding.tvStep4Bubble, binding.tvStep4Bubble, 4)
    }

    private fun updateStepPanels() {
        binding.layoutStepDropoff.visibility = if (currentStep == 1) View.VISIBLE else View.GONE
        binding.layoutStepParcel.visibility = if (currentStep == 2) View.VISIBLE else View.GONE
        binding.layoutStepRecipient.visibility = if (currentStep == 3) View.VISIBLE else View.GONE
        binding.layoutStepPayment.visibility = if (currentStep == 4) View.VISIBLE else View.GONE

        // Back button visibility
        binding.btnBackStep.visibility = if (currentStep == 1) View.INVISIBLE else View.VISIBLE

        // Next button CTA
        binding.btnNextStep.text = if (currentStep == 4) "Confirm & Book" else "Next"
        
        if (currentStep == 4) {
            populateSummary()
        }
    }

    private fun populateSummary() {
        val branchName = binding.spDropoffBranch.text.toString()
        val street = binding.etReceiverStreet.text.toString()
        val province = binding.spReceiverProvince.text.toString()
        val city = binding.spReceiverCity.text.toString()
        val barangay = binding.spReceiverBarangay.text.toString()
        val postal = binding.etReceiverPostal.text.toString()
        val weight = binding.etWeight.text.toString()

        binding.tvSummaryOrigin.text = "🏢 Drop-off Hub: $branchName"
        binding.tvSummaryDest.text = "📍 Delivery Target: $street, Brgy. $barangay, $city, $province $postal"
        val recipientFullName = "${binding.etReceiverFname.text.toString().trim()} ${binding.etReceiverLname.text.toString().trim()}"
        binding.tvSummaryReceiver.text = "👤 Recipient: $recipientFullName (${binding.etReceiverPhone.text.toString()})"
        binding.tvSummaryWeight.text = "⚖️ Declared Weight: $weight kg"
        
        binding.tvSummaryMethod.text = selectedPaymentMethod

        val isOnlinePayment = selectedPaymentMethod == "GCash" || selectedPaymentMethod == "Maya"
        binding.tvSummaryPaymentStatus.text = if (isOnlinePayment) "Pending Verification" else "Pending"
        binding.tvSummaryPaymentStatus.setTextColor(Color.parseColor(if (isOnlinePayment) "#F59E0B" else "#EF4444"))

        // Show / hide reference row in summary
        if (isOnlinePayment) {
            val refNo = binding.etPaymentRef.text?.toString()?.trim() ?: ""
            binding.rlSummaryRefNo.visibility = View.VISIBLE
            binding.tvSummaryRefNo.text = refNo.ifEmpty { "--" }
        } else {
            binding.rlSummaryRefNo.visibility = View.GONE
        }
    }

    private fun validateCurrentStep(): Boolean {
        when (currentStep) {
            1 -> {
                val branch = binding.spDropoffBranch.text.toString()
                if (branch.isEmpty()) {
                    Toast.makeText(this, "⚠️ Please select a drop-off outlet.", Toast.LENGTH_SHORT).show()
                    return false
                }
            }
            2 -> {
                val weightStr = binding.etWeight.text?.toString()?.trim() ?: ""
                val weight = weightStr.toDoubleOrNull() ?: 0.0
                if (weight <= 0.0) {
                    binding.etWeight.error = "Weight must be greater than 0 kg"
                    return false
                }
                if (selectedPaymentMethod == "Cash on Delivery (COD)") {
                    val codStr = binding.etCod.text?.toString()?.trim() ?: ""
                    val cod = codStr.toDoubleOrNull() ?: 0.0
                    if (cod <= 0.0) {
                        binding.etCod.error = "Declared COD price is required"
                        return false
                    }
                }
                // Validate GCash / Maya reference number strict digit lengths
                if (selectedPaymentMethod == "GCash" || selectedPaymentMethod == "Maya") {
                    val referenceNo = binding.etPaymentRef.text?.toString()?.trim() ?: ""
                    val expectedLen = if (selectedPaymentMethod == "GCash") 13 else 12
                    if (referenceNo.isEmpty()) {
                        binding.etPaymentRef.error = "Reference number is required for $selectedPaymentMethod payments."
                        return false
                    }
                    if (!referenceNo.matches(Regex("^[0-9]+$"))) {
                        binding.etPaymentRef.error = "Reference number must contain digits only."
                        return false
                    }
                    if (referenceNo.length != expectedLen) {
                        binding.etPaymentRef.error = "Invalid $selectedPaymentMethod Reference Number. Must be exactly $expectedLen digits."
                        return false
                    }
                }
            }
            3 -> {
                val fname = binding.etReceiverFname.text?.toString()?.trim() ?: ""
                val lname = binding.etReceiverLname.text?.toString()?.trim() ?: ""
                val phone = binding.etReceiverPhone.text?.toString()?.trim() ?: ""
                val street = binding.etReceiverStreet.text?.toString()?.trim() ?: ""
                val province = binding.spReceiverProvince.text.toString()
                val city = binding.spReceiverCity.text.toString()
                val barangay = binding.spReceiverBarangay.text.toString()

                if (fname.isEmpty() || lname.isEmpty() || phone.isEmpty() || street.isEmpty() || province.isEmpty() || city.isEmpty() || barangay.isEmpty()) {
                    Toast.makeText(this, "⚠️ Please fill in all recipient fields!", Toast.LENGTH_SHORT).show()
                    return false
                }

                if (phone.length != 11 || !phone.startsWith("09")) {
                    binding.etReceiverPhone.error = "Must be a valid 11-digit mobile (09xxxxxxxxx)"
                    return false
                }
            }
        }
        return true
    }

    private fun handleBookParcelConfirm() {
        binding.btnNextStep.isEnabled = false
        binding.btnNextStep.text = "Processing..."
        binding.btnBackStep.isEnabled = false

        val customerId = sessionManager.getUserId()
        
        val fname = binding.etReceiverFname.text?.toString()?.trim() ?: ""
        val lname = binding.etReceiverLname.text?.toString()?.trim() ?: ""
        val name = "$fname $lname"
        val phone = binding.etReceiverPhone.text?.toString()?.trim() ?: ""
        val street = binding.etReceiverStreet.text?.toString()?.trim() ?: ""
        val province = binding.spReceiverProvince.text.toString()
        val city = binding.spReceiverCity.text.toString()
        val barangay = binding.spReceiverBarangay.text.toString()
        val postal = binding.etReceiverPostal.text.toString()
        val weight = binding.etWeight.text.toString().toDoubleOrNull() ?: 1.0

        val codAmount = if (selectedPaymentMethod == "Cash on Delivery (COD)") {
            binding.etCod.text?.toString()?.toDoubleOrNull() ?: 0.0
        } else {
            0.0
        }

        lifecycleScope.launch {
            try {
                // Fetch all existing collections to calculate sequential auto-incremented IDs
                val bookingsRes = RetrofitClient.instance.getAllBookings()
                val parcelsRes = RetrofitClient.instance.getAllParcels()
                val paymentsRes = RetrofitClient.instance.getAllPayments()
                val historyRes = RetrofitClient.instance.getAllHistoryLogs()

                val bookingsMap = if (bookingsRes.isSuccessful) bookingsRes.body() else null
                val parcelsMap = if (parcelsRes.isSuccessful) parcelsRes.body() else null
                val paymentsMap = if (paymentsRes.isSuccessful) paymentsRes.body() else null
                val historyMap = if (historyRes.isSuccessful) historyRes.body() else null

                // Compute next sequential IDs
                val nextBkngId = (bookingsMap?.values?.mapNotNull { it.bkngId }?.maxOrNull() ?: 0) + 1
                val nextParclId = (parcelsMap?.values?.mapNotNull { it.parclId }?.maxOrNull() ?: 0) + 1
                val nextPymtId = (paymentsMap?.values?.mapNotNull { it.pymtId }?.maxOrNull() ?: 0) + 1
                val nextHistId = (historyMap?.values?.mapNotNull { it.histId }?.maxOrNull() ?: 0) + 1

                val uniqueHex = Integer.toHexString(Random.nextInt(1000000, 99999999)).uppercase()
                val trackingNumber = "DDX-$uniqueHex"
                val fullAddress = "$street, Brgy. $barangay, $city, $province $postal"

                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                val currentTimestamp = dateFormat.format(Date())

                // 1. Create Booking Node
                val newBooking = Booking(
                    bkngId = nextBkngId,
                    bkngCustomerId = customerId,
                    bkngBookingDate = currentTimestamp,
                    bkngTotalParcels = 1,
                    bkngTotalWeightKg = weight,
                    bkngStatus = "Pending Drop-off",
                    bkngCreatedAt = currentTimestamp,
                    bkngUpdatedAt = currentTimestamp
                )

                // 2. Auto-resolve Destination Hub based on receiver's city or province
                var resolvedDestBranchId = 1 // Fallback to Main Branch (ID 1)
                val cityClean = city.trim().lowercase(java.util.Locale.US).replace("city", "").trim()
                val matchingCityBranch = branchList.find { b ->
                    val bCityClean = (b.brnchCity ?: "").trim().lowercase(java.util.Locale.US).replace("city", "").trim()
                    bCityClean.isNotEmpty() && bCityClean == cityClean
                }
                if (matchingCityBranch != null) {
                    resolvedDestBranchId = matchingCityBranch.brnchId
                } else {
                    val matchingProvinceBranch = branchList.find { b ->
                        val bProv = (b.brnchProvince ?: "").trim().lowercase(java.util.Locale.US)
                        bProv.isNotEmpty() && bProv.contains(province.trim().lowercase(java.util.Locale.US))
                    }
                    if (matchingProvinceBranch != null) {
                        resolvedDestBranchId = matchingProvinceBranch.brnchId
                    }
                }

                // Create Parcel Node
                val newParcel = Parcel(
                    parclId = nextParclId,
                    parclBkngId = nextBkngId,
                    parclTrackingNumber = trackingNumber,
                    parclSenderAddress = "Customer Home Saved Address",
                    parclReceiverName = name,
                    parclReceiverPhone = phone,
                    parclReceiverAddress = fullAddress,
                    parclWeightKg = weight,
                    parclDeliveryStatus = "Pending Drop-off",
                    parclCodAmount = codAmount,
                    parclRiderId = null,
                    parclProofImg = null,
                    parclReceiverStreet = street,
                    parclReceiverCity = city,
                    parclReceiverProvince = province,
                    parclReceiverPostalCode = postal,
                    parclOrigBrchId = selectedBranchId,
                    parclDestBrchId = resolvedDestBranchId, // Auto-resolved destination hub!
                    parclNextHopBrchId = null,
                    parclDeclaredValue = if (codAmount > 0.0) codAmount else calculatedShippingFee,
                    parclSrvcId = 1,
                    parclRemitStatus = "None"
                )

                // 3. Create Payment Node
                val isOnlinePayment = selectedPaymentMethod == "GCash" || selectedPaymentMethod == "Maya"
                val paymentStatus = if (isOnlinePayment) "Pending Verification" else "Pending"
                // Use actual user-entered reference (already validated for correct digit length)
                val paymentRefNo: String? = if (isOnlinePayment) {
                    binding.etPaymentRef.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                } else {
                    null
                }
                
                val newPayment = Payment(
                    pymtId = nextPymtId,
                    pymtBkngId = nextBkngId,
                    pymtAmount = if (codAmount > 0.0) totalCollectAmount else calculatedShippingFee,
                    pymtPaymentMethod = selectedPaymentMethod,
                    pymtPaymentStatus = paymentStatus,
                    pymtPaymentReference = paymentRefNo,
                    pymtPaymentDate = currentTimestamp
                )

                // 4. Create HistoryLog Node
                val milestoneLog = ParcelStatusHistory(
                    histId = nextHistId,
                    histParclId = nextParclId,
                    histStatus = "Pending Drop-off",
                    histLocationNote = "Booked via Customer Mobile App",
                    histTimestamp = currentTimestamp,
                    histStfId = null,
                    histRemark = "Customer booked parcel via app. Declared weight: $weight kg. Payment status: $paymentStatus"
                )

                // Run atomic writes concurrently — PUT to explicit integer-keyed paths
                val bookingWrite = async { RetrofitClient.instance.addBooking(nextBkngId, newBooking) }
                val parcelWrite = async { RetrofitClient.instance.addParcel(nextParclId, newParcel) }
                val paymentWrite = async { RetrofitClient.instance.addPayment(nextPymtId, newPayment) }
                val historyWrite = async { RetrofitClient.instance.addHistoryLog(milestoneLog) }

                val results = awaitAll(bookingWrite, parcelWrite, paymentWrite, historyWrite)

                val allSuccessful = results.all { it.isSuccessful }

                if (allSuccessful) {
                    Toast.makeText(this@BookParcelActivity, "🎉 Shipment Booked! Tracking ID: $trackingNumber", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    val errors = results.filter { !it.isSuccessful }.map { it.code() }.joinToString(", ")
                    Toast.makeText(this@BookParcelActivity, "Booking partial failure. Codes: $errors", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Toast.makeText(this@BookParcelActivity, "Booking Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnNextStep.isEnabled = true
                binding.btnNextStep.text = "Confirm & Book"
                binding.btnBackStep.isEnabled = true
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
