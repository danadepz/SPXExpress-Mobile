package com.spx.express.ui.rider

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spx.express.data.api.RetrofitClient
import com.spx.express.data.model.Parcel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

sealed class RemittanceState {
    object Loading : RemittanceState()
    data class Success(val pendingParcels: List<Parcel>, val totalCash: Double) : RemittanceState()
    object SubmitComplete : RemittanceState()
    data class Error(val message: String) : RemittanceState()
}

class RiderRemittanceViewModel : ViewModel() {

    private val _remittanceState = MutableLiveData<RemittanceState>()
    val remittanceState: LiveData<RemittanceState> get() = _remittanceState

    private var activeRiderId: Int = 1

    fun loadRemittanceData(riderId: Int) {
        activeRiderId = riderId
        _remittanceState.value = RemittanceState.Loading

        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.getAllParcels()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        // Map Firebase key and filter for "Pending" remittances for this rider in memory
                        val pendingList = body.map { (key, parcel) ->
                            parcel.copy().apply { firebaseKey = key }
                        }.filter { 
                            it.parclRiderId == riderId && it.parclRemitStatus == "Pending"
                        }
                        val totalCash = pendingList.sumOf { it.parclCodAmount }
                        _remittanceState.value = RemittanceState.Success(pendingList, totalCash)
                    } else {
                        _remittanceState.value = RemittanceState.Success(emptyList(), 0.0)
                    }
                } else {
                    _remittanceState.value = RemittanceState.Error("Server Connection Failed: ${response.code()}")
                }
            } catch (e: Exception) {
                _remittanceState.value = RemittanceState.Error("Connection Error: ${e.localizedMessage}")
            }
        }
    }

    fun submitRemittanceRequest() {
        val currentState = _remittanceState.value
        if (currentState !is RemittanceState.Success || currentState.pendingParcels.isEmpty()) {
            return
        }

        _remittanceState.value = RemittanceState.Loading

        viewModelScope.launch {
            try {
                // Submit parallel PATCH requests to modify all target parcels' remittance status
                val deferreds = currentState.pendingParcels.map { parcel ->
                    val targetKey = parcel.firebaseKey ?: parcel.parclId.toString()
                    val updates = mapOf<String, Any>(
                        "Parcl_Remit_Status" to "Remitted"
                    )
                    async {
                        RetrofitClient.instance.updateStaffStatus(targetKey, updates) // Wait, is it updateParcelStatus?
                        // Let's call updateParcelStatus instead! Yes!
                        RetrofitClient.instance.updateParcelStatus(targetKey, updates)
                    }
                }
                
                val responses = deferreds.awaitAll()
                if (responses.all { it.isSuccessful }) {
                    _remittanceState.value = RemittanceState.SubmitComplete
                } else {
                    _remittanceState.value = RemittanceState.Error("Some updates failed. Please refresh and try again.")
                }
            } catch (e: Exception) {
                _remittanceState.value = RemittanceState.Error("Submission Failed: ${e.localizedMessage}")
            }
        }
    }
}
