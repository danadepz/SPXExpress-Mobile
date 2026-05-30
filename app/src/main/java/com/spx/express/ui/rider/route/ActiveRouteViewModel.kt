package com.spx.express.ui.rider.route

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spx.express.data.api.RetrofitClient
import com.spx.express.data.model.Parcel
import com.spx.express.data.model.ParcelStatusHistory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class RouteState {
    object Loading : RouteState()
    data class Success(val activeParcels: List<Parcel>) : RouteState()
    object UpdateComplete : RouteState()
    data class Error(val message: String) : RouteState()
}

class ActiveRouteViewModel : ViewModel() {

    private val _routeState = MutableLiveData<RouteState>()
    val routeState: LiveData<RouteState> get() = _routeState

    private val _branchesMap = MutableLiveData<Map<Int, String>>()
    val branchesMap: LiveData<Map<Int, String>> get() = _branchesMap

    fun loadActiveRoutes(riderId: Int = 1) {
        _routeState.value = RouteState.Loading
        
        viewModelScope.launch {
            try {
                // Fetch all branches to resolve next hop names dynamically
                val branchResponse = RetrofitClient.instance.getAllBranches()
                if (branchResponse.isSuccessful) {
                    val branchData = branchResponse.body()?.values?.filterNotNull()?.associate { it.brnchId to it.brnchName } ?: emptyMap()
                    _branchesMap.value = branchData
                }

                // Fetch all parcels and filter locally by riderId to prevent index errors
                val response = RetrofitClient.instance.getAllParcels()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        // Filter for in-transit/active routes assigned to this rider, and bind the Firebase node key to each Parcel
                        val activeList = body.filterValues { it != null }.map { (key, parcel) ->
                            parcel.copy().apply { firebaseKey = key }
                        }.filter { 
                            it.parclRiderId == riderId && (it.parclDeliveryStatus == "In Transit" || it.parclDeliveryStatus == "Out for Delivery")
                        }
                        _routeState.value = RouteState.Success(activeList)
                    } else {
                        _routeState.value = RouteState.Success(emptyList())
                    }
                } else {
                    _routeState.value = RouteState.Error("Server Connection Failed: ${response.code()}")
                }
            } catch (e: Exception) {
                _routeState.value = RouteState.Error("Failed to fetch routes: ${e.localizedMessage}")
            }
        }
    }

    fun submitDeliveryUpdate(parcel: Parcel, status: String, imageUri: Uri) {
        _routeState.value = RouteState.Loading
        
        viewModelScope.launch {
            try {
                // In production, upload physical image to storage, then save URL path.
                val proofPath = imageUri.toString()
                
                val updates = mutableMapOf<String, Any>(
                    "Parcl_Delivery_Status" to status,
                    "Parcl_Proof_Img" to proofPath,
                    "Parcl_Remit_Status" to if (parcel.parclCodAmount > 0.0) "Pending" else "None"
                )
                
                val targetKey = parcel.firebaseKey ?: parcel.parclId.toString()
                
                // 1. Update specific 'Parcel' key
                val response = RetrofitClient.instance.updateParcelStatus(targetKey, updates)
                if (response.isSuccessful) {
                    
                    // 2. Update master 'Booking' key: Set Bkng_Status to "Delivered"
                    val bookingIdStr = parcel.parclBkngId.toString()
                    val bookingUpdates = mapOf<String, Any>(
                        "Bkng_Status" to "Delivered"
                    )
                    RetrofitClient.instance.updateBookingStatus(bookingIdStr, bookingUpdates)

                    // 3. Update 'Payment' status: Set Pymt_Payment_Status to "Paid" under the booking
                    try {
                        val paymentResponse = RetrofitClient.instance.getAllPayments()
                        if (paymentResponse.isSuccessful) {
                            val paymentsMap = paymentResponse.body()
                            if (paymentsMap != null) {
                                val paymentEntry = paymentsMap.entries.firstOrNull { 
                                    it.value.pymtBkngId == parcel.parclBkngId 
                                }
                                if (paymentEntry != null) {
                                    val paymentKey = paymentEntry.key
                                    val paymentUpdates = mapOf<String, Any>(
                                        "Pymt_Payment_Status" to "Paid"
                                    )
                                    RetrofitClient.instance.updatePaymentStatus(paymentKey, paymentUpdates)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Fail silently for secondary Payment mutations to maintain main update reliability
                    }

                    // 4. Record secure milestone history audit trail log
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val currentTimestamp = dateFormat.format(Date())
                    
                    val milestoneLog = ParcelStatusHistory(
                        histParclId = parcel.parclId,
                        histStatus = "Delivered",
                        histLocationNote = "Updated by Rider",
                        histTimestamp = currentTimestamp,
                        histStfId = parcel.parclRiderId ?: 1,
                        histRemark = "Delivered successfully. Proof of delivery uploaded."
                    )
                    
                    RetrofitClient.instance.addHistoryLog(milestoneLog)
                    _routeState.value = RouteState.UpdateComplete
                } else {
                    _routeState.value = RouteState.Error("Failed to sync status: ${response.code()}")
                }
            } catch (e: Exception) {
                _routeState.value = RouteState.Error("Error: ${e.localizedMessage}")
            }
        }
    }

    fun submitDeliveryFailure(parcel: Parcel, remark: String) {
        _routeState.value = RouteState.Loading
        
        viewModelScope.launch {
            try {
                val updates = mapOf<String, Any>(
                    "Parcl_Delivery_Status" to "Delivery Failed"
                )
                
                val targetKey = parcel.firebaseKey ?: parcel.parclId.toString()
                
                // 1. Update specific 'Parcel' key: Set Parcl_Delivery_Status to "Delivery Failed"
                val response = RetrofitClient.instance.updateParcelStatus(targetKey, updates)
                if (response.isSuccessful) {
                    
                    // 2. Update master 'Booking' key: Set Bkng_Status to "Delivery Failed"
                    val bookingIdStr = parcel.parclBkngId.toString()
                    val bookingUpdates = mapOf<String, Any>(
                        "Bkng_Status" to "Delivery Failed"
                    )
                    RetrofitClient.instance.updateBookingStatus(bookingIdStr, bookingUpdates)

                    // 3. Record secure milestone history audit trail log
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val currentTimestamp = dateFormat.format(Date())
                    
                    val milestoneLog = ParcelStatusHistory(
                        histParclId = parcel.parclId,
                        histStatus = "Delivery Failed",
                        histLocationNote = "Updated by Rider",
                        histTimestamp = currentTimestamp,
                        histStfId = parcel.parclRiderId ?: 1,
                        histRemark = remark
                    )
                    
                    RetrofitClient.instance.addHistoryLog(milestoneLog)
                    _routeState.value = RouteState.UpdateComplete
                } else {
                    _routeState.value = RouteState.Error("Failed to sync failure: ${response.code()}")
                }
            } catch (e: Exception) {
                _routeState.value = RouteState.Error("Error: ${e.localizedMessage}")
            }
        }
    }

    fun submitHubHandoff(parcel: Parcel) {
        _routeState.value = RouteState.Loading

        viewModelScope.launch {
            try {
                val targetKey = parcel.firebaseKey ?: parcel.parclId.toString()
                val nextHopId = parcel.parclNextHopBrchId ?: 0

                // 1. Update Parcel: clear rider assignment, keeping status "In Transit" and keeping Next_Hop_Brch_Id
                val updates = mapOf<String, Any>(
                    "Parcl_Rider_Id" to 0
                )
                val response = RetrofitClient.instance.updateParcelStatus(targetKey, updates)

                if (response.isSuccessful) {
                    // 2. Audit log milestone — rider-initiated, so Hist_Stf_Id uses the rider's own ID
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val currentTimestamp = dateFormat.format(Date())
                    val hubName = _branchesMap.value?.get(nextHopId) ?: "Branch #$nextHopId"
                    val locationNote = "Arrived at Hub: $hubName"
                    val remark = "Handed over by transit rider at intermediate hub: $hubName. Awaiting hub staff acceptance."

                    val milestoneLog = ParcelStatusHistory(
                        histParclId = parcel.parclId,
                        histStatus = "In Transit",
                        histLocationNote = locationNote,
                        histTimestamp = currentTimestamp,
                        histStfId = parcel.parclRiderId,   // rider who did the handoff
                        histRemark = remark
                    )
                    RetrofitClient.instance.addHistoryLog(milestoneLog)

                    _routeState.value = RouteState.UpdateComplete
                } else {
                    _routeState.value = RouteState.Error("Hub handoff failed: ${response.code()}")
                }
            } catch (e: Exception) {
                _routeState.value = RouteState.Error("Error: ${e.localizedMessage}")
            }
        }
    }
}
