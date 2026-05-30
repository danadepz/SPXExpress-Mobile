package com.spx.express.ui.staff

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spx.express.data.api.RetrofitClient
import com.spx.express.data.model.Branch
import com.spx.express.data.model.Parcel
import com.spx.express.data.model.ParcelStatusHistory
import com.spx.express.data.model.Staff
import com.spx.express.data.model.Booking
import com.spx.express.data.model.Payment
import com.spx.express.data.model.Customer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PaymentWithDetails(
    val payment: Payment,
    val paymentKey: String,
    val booking: Booking,
    val bookingKey: String,
    val customer: Customer,
    val parcel: Parcel,
    val parcelKey: String
)

sealed class StaffDashboardState {
    object Idle : StaffDashboardState()
    object Loading : StaffDashboardState()
    data class Success(val message: String) : StaffDashboardState()
    data class Error(val message: String) : StaffDashboardState()
}

class StaffDashboardViewModel : ViewModel() {

    private val _incomingParcels = MutableLiveData<List<Parcel>>()
    val incomingParcels: LiveData<List<Parcel>> get() = _incomingParcels

    private val _activeParcels = MutableLiveData<List<Parcel>>()
    val activeParcels: LiveData<List<Parcel>> get() = _activeParcels

    private val _localRiders = MutableLiveData<List<Staff>>()
    val localRiders: LiveData<List<Staff>> get() = _localRiders

    private val _branches = MutableLiveData<Map<Int, Branch>>()
    val branches: LiveData<Map<Int, Branch>> get() = _branches

    private val _pendingPayments = MutableLiveData<List<PaymentWithDetails>>()
    val pendingPayments: LiveData<List<PaymentWithDetails>> get() = _pendingPayments

    private val _pendingRemittances = MutableLiveData<List<Parcel>>()
    val pendingRemittances: LiveData<List<Parcel>> get() = _pendingRemittances

    private val _dashboardState = MutableLiveData<StaffDashboardState>(StaffDashboardState.Idle)
    val dashboardState: LiveData<StaffDashboardState> get() = _dashboardState

    private var isPolling = false
    private var allParcelsCached = listOf<Parcel>()

    fun startRealtimeSync(branchId: Int) {
        if (isPolling) return
        isPolling = true
        viewModelScope.launch {
            while (isPolling) {
                try {
                    fetchData(branchId)
                } catch (e: Exception) {
                    // Fail silently during background polling to prevent user interruption
                }
                delay(4000)
            }
        }
    }

    fun stopRealtimeSync() {
        isPolling = false
    }

    fun refreshDashboard(branchId: Int) {
        _dashboardState.value = StaffDashboardState.Loading
        viewModelScope.launch {
            try {
                fetchData(branchId)
                _dashboardState.value = StaffDashboardState.Success("Dashboard Refreshed")
            } catch (e: Exception) {
                _dashboardState.value = StaffDashboardState.Error("Refresh failed: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun fetchData(branchId: Int) {
        // 1. Fetch all branches (if not already cached)
        if (_branches.value.isNullOrEmpty()) {
            val branchResponse = RetrofitClient.instance.getAllBranches()
            if (branchResponse.isSuccessful) {
                val body = branchResponse.body()
                if (body != null) {
                    _branches.postValue(body.filterValues { it != null }.mapKeys { it.value.brnchId })
                }
            }
        }

        // 2. Fetch all staff members to extract local active riders
        val staffResponse = RetrofitClient.instance.getAllStaff()
        if (staffResponse.isSuccessful) {
            val body = staffResponse.body()
            if (body != null) {
                val ridersList = body.values.filterNotNull().filter {
                    it.stfBrchId == branchId && it.stfRole.lowercase() == "rider" && it.stfIsActive == 1
                }.sortedBy { it.stfFname }
                _localRiders.postValue(ridersList)
            }
        }

        // 3. Fetch all parcels and filter
        val parcelResponse = RetrofitClient.instance.getAllParcels()
        if (parcelResponse.isSuccessful) {
            val body = parcelResponse.body()
            if (body != null) {
                val parcelsList = body.filterValues { it != null }.map { (key, parcel) ->
                    parcel.copy().apply { firebaseKey = key }
                }
                allParcelsCached = parcelsList

                // Queue filter:
                // p.Parcl_Delivery_Status = 'Pending Drop-off' AND p.Parcl_Orig_Brch_Id = branchId
                // OR p.Parcl_Delivery_Status = 'In Transit' AND p.Parcl_Next_Hop_Brch_Id = branchId
                val incomingList = parcelsList.filter {
                    (it.parclDeliveryStatus == "Pending Drop-off" && it.parclOrigBrchId == branchId) ||
                    (it.parclDeliveryStatus == "In Transit" && it.parclNextHopBrchId == branchId)
                }.sortedByDescending { it.parclId }

                // Inventory filter:
                // p.Parcl_Orig_Brch_Id = branchId 
                // AND p.Parcl_Delivery_Status != 'Delivered' 
                // AND p.Parcl_Delivery_Status != 'Rejected'
                // AND p.Parcl_Delivery_Status != 'Pending Drop-off'
                val inventoryList = parcelsList.filter {
                    it.parclOrigBrchId == branchId &&
                    it.parclDeliveryStatus != "Delivered" &&
                    it.parclDeliveryStatus != "Rejected" &&
                    it.parclDeliveryStatus != "Pending Drop-off"
                }.sortedByDescending { it.parclId }

                _incomingParcels.postValue(incomingList)
                _activeParcels.postValue(inventoryList)

                // COD Remittance filter:
                // Delivered parcels where this branch is the DESTINATION and Parcl_Remit_Status = 'Remitted'
                val remittanceList = parcelsList.filter {
                    it.parclDestBrchId == branchId &&
                    it.parclDeliveryStatus == "Delivered" &&
                    it.parclCodAmount > 0.0 &&
                    it.parclRemitStatus == "Remitted"
                }.sortedByDescending { it.parclId }
                _pendingRemittances.postValue(remittanceList)

                // 4. Fetch payments, bookings and customers to zip pending payments locally
                val paymentsResponse = RetrofitClient.instance.getAllPayments()
                val bookingsResponse = RetrofitClient.instance.getAllBookings()
                val customersResponse = RetrofitClient.instance.getAllCustomers()

                if (paymentsResponse.isSuccessful && bookingsResponse.isSuccessful && customersResponse.isSuccessful) {
                    val paymentsMap = paymentsResponse.body() ?: emptyMap()
                    val bookingsMap = bookingsResponse.body() ?: emptyMap()
                    val customersMap = customersResponse.body() ?: emptyMap()

                    val pendingList = mutableListOf<PaymentWithDetails>()

                    paymentsMap.forEach { (payKey, payment) ->
                        val method = payment.pymtPaymentMethod?.lowercase() ?: ""
                        if ((payment.pymtPaymentStatus == "Pending" || payment.pymtPaymentStatus == "Pending Verification") &&
                            (method == "gcash" || method == "maya")) {
                            // Find corresponding Booking
                            val bookingEntry = bookingsMap.entries.find { it.value.bkngId == payment.pymtBkngId }
                            if (bookingEntry != null) {
                                val booking = bookingEntry.value
                                // Find Customer
                                val customer = customersMap.values.find { it.custId == booking.bkngCustomerId }
                                if (customer != null) {
                                    // Find corresponding Parcel
                                    val parcelEntry = parcelsList.find { it.parclBkngId == booking.bkngId }
                                    if (parcelEntry != null) {
                                        pendingList.add(
                                            PaymentWithDetails(
                                                payment = payment,
                                                paymentKey = payKey,
                                                booking = booking,
                                                bookingKey = bookingEntry.key,
                                                customer = customer,
                                                parcel = parcelEntry,
                                                parcelKey = parcelEntry.firebaseKey ?: ""
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    _pendingPayments.postValue(pendingList.sortedByDescending { it.payment.pymtBkngId })
                } else {
                    _pendingPayments.postValue(emptyList())
                }
            } else {
                _incomingParcels.postValue(emptyList())
                _activeParcels.postValue(emptyList())
                _pendingPayments.postValue(emptyList())
            }
        }
    }

    // Mutation: Accept Parcel
    fun acceptParcel(parcel: Parcel, branchId: Int, staffId: Int) {
        _dashboardState.value = StaffDashboardState.Loading
        viewModelScope.launch {
            try {
                val isTransit = parcel.parclDeliveryStatus == "In Transit"
                val declaredWeight = parcel.parclWeightKg
                val targetKey = parcel.firebaseKey ?: parcel.parclId.toString()

                val updates = mapOf<String, Any?>(
                    "Parcl_Delivery_Status" to "Received at Branch",
                    "Parcl_Orig_Brch_Id" to branchId,
                    "Parcl_Rider_Id" to null,
                    "Parcl_Next_Hop_Brch_Id" to null
                )

                val response = RetrofitClient.instance.updateParcelStatus(targetKey, updates as Map<String, Any>)
                if (response.isSuccessful) {

                    // Update Booking Status
                    RetrofitClient.instance.updateBookingStatus(parcel.parclBkngId.toString(), mapOf("Bkng_Status" to "Received at Branch"))

                    // Audit Log History
                    val locationNote = if (isTransit) "Verified and received at intermediate hub" else "Verified and accepted at counter"
                    val remark = if (isTransit) "Transited package received. Declared weight: $declaredWeight kg" else "Accepted at counter. Declared weight: $declaredWeight kg"

                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val currentTimestamp = dateFormat.format(Date())

                    val milestoneLog = ParcelStatusHistory(
                        histParclId = parcel.parclId,
                        histStatus = "Received at Branch",
                        histLocationNote = "$locationNote (Branch ID: $branchId)",
                        histTimestamp = currentTimestamp,
                        histStfId = staffId,
                        histRemark = remark
                    )
                    RetrofitClient.instance.addHistoryLog(milestoneLog)

                    fetchData(branchId)
                    _dashboardState.value = StaffDashboardState.Success("Parcel accepted successfully!")
                } else {
                    _dashboardState.value = StaffDashboardState.Error("Database update failed: ${response.code()}")
                }
            } catch (e: Exception) {
                _dashboardState.value = StaffDashboardState.Error("Error: ${e.localizedMessage}")
            }
        }
    }

    // Mutation: Decline/Reject Parcel
    fun declineParcel(parcel: Parcel, reason: String, branchId: Int, staffId: Int) {
        _dashboardState.value = StaffDashboardState.Loading
        viewModelScope.launch {
            try {
                val targetKey = parcel.firebaseKey ?: parcel.parclId.toString()
                val response = RetrofitClient.instance.updateParcelStatus(targetKey, mapOf("Parcl_Delivery_Status" to "Rejected"))
                if (response.isSuccessful) {
                    
                    // Sync Booking Status
                    RetrofitClient.instance.updateBookingStatus(parcel.parclBkngId.toString(), mapOf("Bkng_Status" to "Rejected"))

                    // Audit Log History
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val currentTimestamp = dateFormat.format(Date())

                    val milestoneLog = ParcelStatusHistory(
                        histParclId = parcel.parclId,
                        histStatus = "Rejected",
                        histLocationNote = "Rejected at counter (Branch ID: $branchId)",
                        histTimestamp = currentTimestamp,
                        histStfId = staffId,
                        histRemark = reason
                    )
                    RetrofitClient.instance.addHistoryLog(milestoneLog)

                    fetchData(branchId)
                    _dashboardState.value = StaffDashboardState.Success("Parcel rejected successfully.")
                } else {
                    _dashboardState.value = StaffDashboardState.Error("Database update failed: ${response.code()}")
                }
            } catch (e: Exception) {
                _dashboardState.value = StaffDashboardState.Error("Error: ${e.localizedMessage}")
            }
        }
    }

    // Mutation: Dispatch Parcel
    fun dispatchParcel(
        parcel: Parcel, 
        riderId: Int, 
        supervisorOverrideChecked: Boolean, 
        branchId: Int, 
        staffId: Int
    ) {
        _dashboardState.value = StaffDashboardState.Loading
        viewModelScope.launch {
            try {
                // 1 & 2. Capacity Validation — delegate to RoutingHelper
                val activeParcelsForRider = allParcelsCached.filter {
                    it.parclRiderId == riderId &&
                    (it.parclDeliveryStatus == "Out for Delivery" || it.parclDeliveryStatus == "In Transit")
                }
                val capacityCheck = RoutingHelper.checkRiderCapacity(
                    parcel = parcel,
                    activeParcelsForRider = activeParcelsForRider,
                    supervisorOverrideChecked = supervisorOverrideChecked
                )
                if (capacityCheck is RoutingHelper.Result.Failure) {
                    _dashboardState.value = StaffDashboardState.Error(capacityCheck.message)
                    return@launch
                }

                // 3. Routing next-hop calculations — delegate to RoutingHelper
                val destBranchId = parcel.parclDestBrchId ?: branchId
                val nextHopId = RoutingHelper.calculateNextHop(branchId, destBranchId)
                val branchesMap = _branches.value ?: emptyMap()

                val newStatus: String
                val locationNote: String
                val remark: String

                if (nextHopId == branchId) {
                    newStatus = "Out for Delivery"
                    locationNote = "Dispatched for Local Delivery"
                    remark = "Assigned to rider for final local delivery."
                } else {
                    newStatus = "In Transit"
                    val nextHopName = branchesMap[nextHopId]?.brnchName ?: "Next Hub"
                    locationNote = "Dispatched to Intermediate Hub: $nextHopName"
                    remark = "Assigned to transit driver for Hub-to-Hub transfer. Next Hub Target: $nextHopName"
                }

                // 4. Atomic Updates in Firebase
                val targetKey = parcel.firebaseKey ?: parcel.parclId.toString()
                val updates = mapOf<String, Any>(
                    "Parcl_Rider_Id" to riderId,
                    "Parcl_Delivery_Status" to newStatus,
                    "Parcl_Next_Hop_Brch_Id" to nextHopId
                )

                val response = RetrofitClient.instance.updateParcelStatus(targetKey, updates)
                if (response.isSuccessful) {
                    
                    // Sync Booking Status
                    RetrofitClient.instance.updateBookingStatus(parcel.parclBkngId.toString(), mapOf("Bkng_Status" to newStatus))

                    // Audit milestone history log
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val currentTimestamp = dateFormat.format(Date())

                    val milestoneLog = ParcelStatusHistory(
                        histParclId = parcel.parclId,
                        histStatus = newStatus,
                        histLocationNote = "$locationNote (Branch ID: $branchId)",
                        histTimestamp = currentTimestamp,
                        histStfId = staffId,
                        histRemark = remark
                    )
                    RetrofitClient.instance.addHistoryLog(milestoneLog)

                    fetchData(branchId)
                    _dashboardState.value = StaffDashboardState.Success("Parcel successfully dispatched!")
                } else {
                    _dashboardState.value = StaffDashboardState.Error("Database update failed: ${response.code()}")
                }

            } catch (e: Exception) {
                _dashboardState.value = StaffDashboardState.Error("Error: ${e.localizedMessage}")
            }
        }
    }

    // Receive scanned parcel
    fun receiveScannedParcel(trackingNumber: String, branchId: Int, staffId: Int) {
        _dashboardState.value = StaffDashboardState.Loading
        viewModelScope.launch {
            try {
                val formattedTracking = trackingNumber.trim().uppercase()
                
                // 1. Search for parcel matching the tracking number locally to prevent index 400 errors
                val response = RetrofitClient.instance.getAllParcels()
                if (response.isSuccessful) {
                    val map = response.body()
                    if (!map.isNullOrEmpty()) {
                        val parcelEntry = map.entries.find { 
                            it.value.parclTrackingNumber.equals(formattedTracking, ignoreCase = true) 
                        }
                        if (parcelEntry != null) {
                            val parcel = parcelEntry.value.copy().apply { firebaseKey = parcelEntry.key }
                            // 2. Perform Accept mutation
                            acceptParcel(parcel, branchId, staffId)
                        } else {
                            _dashboardState.value = StaffDashboardState.Error("Error: Parcel tracking number '$formattedTracking' not found in database.")
                        }
                    } else {
                        _dashboardState.value = StaffDashboardState.Error("Error: No parcels found in database.")
                    }
                } else {
                    _dashboardState.value = StaffDashboardState.Error("Query failed: ${response.code()}")
                }
            } catch (e: Exception) {
                _dashboardState.value = StaffDashboardState.Error("Error: ${e.localizedMessage}")
            }
        }
    }

    // Calculate a rider's current load
    fun getRiderCurrentLoad(riderId: Int): Double {
        return allParcelsCached.filter {
            it.parclRiderId == riderId && (it.parclDeliveryStatus == "Out for Delivery" || it.parclDeliveryStatus == "In Transit")
        }.sumOf { it.parclWeightKg }
    }

    // Confirm Payment
    fun confirmPayment(paymentDetails: PaymentWithDetails, staffId: Int, branchId: Int) {
        _dashboardState.value = StaffDashboardState.Loading
        viewModelScope.launch {
            try {
                // 1. Update Payment Status to 'Paid'
                val payUpdates = mapOf<String, Any>(
                    "Pymt_Payment_Status" to "Paid",
                    "Pymt_Payment_Date" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                )
                val payRes = RetrofitClient.instance.updatePaymentStatus(paymentDetails.paymentKey, payUpdates)
                if (payRes.isSuccessful) {
                    
                    // 2. Add Audit Log History
                    val method = paymentDetails.payment.pymtPaymentMethod
                    val remark = if (method == "Cash") "Cash payment received at counter" else "Online payment verified against reference: ${paymentDetails.payment.pymtPaymentReference ?: "N/A"}"
                    
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val currentTimestamp = dateFormat.format(Date())

                    val milestoneLog = ParcelStatusHistory(
                        histParclId = paymentDetails.parcel.parclId,
                        histStatus = "Payment Confirmed",
                        histLocationNote = "Station Counter (Branch ID: $branchId)",
                        histTimestamp = currentTimestamp,
                        histStfId = staffId,
                        histRemark = remark
                    )
                    RetrofitClient.instance.addHistoryLog(milestoneLog)

                    // Refresh dashboard
                    fetchData(branchId)
                    _dashboardState.value = StaffDashboardState.Success("Payment successfully confirmed!")
                } else {
                    _dashboardState.value = StaffDashboardState.Error("Failed to update payment status: ${payRes.code()}")
                }
            } catch (e: Exception) {
                _dashboardState.value = StaffDashboardState.Error("Error: ${e.localizedMessage}")
            }
        }
    }
    // Confirm COD Remittance (Destination Branch Staff)
    fun confirmRemittance(parcel: Parcel, staffId: Int, branchId: Int) {
        _dashboardState.value = StaffDashboardState.Loading
        viewModelScope.launch {
            try {
                // Validate: only allow if this is the destination branch
                if (parcel.parclDestBrchId != branchId) {
                    _dashboardState.value = StaffDashboardState.Error("Unauthorized: COD can only be confirmed at the destination branch.")
                    return@launch
                }

                val targetKey = parcel.firebaseKey ?: parcel.parclId.toString()
                val updates = mapOf<String, Any>("Parcl_Remit_Status" to "Confirmed")
                val res = RetrofitClient.instance.updateParcelStatus(targetKey, updates)
                if (res.isSuccessful) {
                    // Audit log
                    val remark = "COD cash remittance confirmed by destination branch staff. Amount: ₱${String.format(Locale.US, "%.2f", parcel.parclCodAmount)}"
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val milestoneLog = ParcelStatusHistory(
                        histParclId = parcel.parclId,
                        histStatus = "COD Remittance Confirmed",
                        histLocationNote = "Destination Branch Counter (Branch ID: $branchId)",
                        histTimestamp = dateFormat.format(Date()),
                        histStfId = staffId,
                        histRemark = remark
                    )
                    RetrofitClient.instance.addHistoryLog(milestoneLog)

                    fetchData(branchId)
                    _dashboardState.value = StaffDashboardState.Success("💰 COD remittance confirmed! ₱${String.format(Locale.US, "%.2f", parcel.parclCodAmount)} received.")
                } else {
                    _dashboardState.value = StaffDashboardState.Error("Failed to confirm remittance: ${res.code()}")
                }
            } catch (e: Exception) {
                _dashboardState.value = StaffDashboardState.Error("Error: ${e.localizedMessage}")
            }
        }
    }
}
