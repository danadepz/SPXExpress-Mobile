package com.spx.express.ui.rider

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spx.express.data.api.RetrofitClient
import com.spx.express.data.model.Parcel
import kotlinx.coroutines.launch

sealed class HistoryState {
    object Loading : HistoryState()
    data class Success(val completedParcels: List<Parcel>) : HistoryState()
    data class Error(val message: String) : HistoryState()
}

class RiderHistoryViewModel : ViewModel() {

    private val _historyState = MutableLiveData<HistoryState>()
    val historyState: LiveData<HistoryState> get() = _historyState

    fun loadHistoryData(riderId: Int) {
        _historyState.value = HistoryState.Loading

        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.getAllParcels()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        // Filter for completed ("Delivered") parcels assigned to this rider in memory
                        val completedList = body.values.filterNotNull().filter { 
                            it.parclRiderId == riderId && it.parclDeliveryStatus == "Delivered"
                        }.sortedByDescending { it.parclId }
                        _historyState.value = HistoryState.Success(completedList)
                    } else {
                        _historyState.value = HistoryState.Success(emptyList())
                    }
                } else {
                    _historyState.value = HistoryState.Error("Server Connection Failed: ${response.code()}")
                }
            } catch (e: Exception) {
                _historyState.value = HistoryState.Error("Connection Error: ${e.localizedMessage}")
            }
        }
    }
}
