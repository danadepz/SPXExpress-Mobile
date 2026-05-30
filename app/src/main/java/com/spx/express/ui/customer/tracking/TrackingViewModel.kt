package com.spx.express.ui.customer.tracking

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spx.express.data.api.RetrofitClient
import com.spx.express.data.model.ParcelStatusHistory
import kotlinx.coroutines.launch

sealed class TrackingState {
    object Loading : TrackingState()
    data class Success(val milestones: List<ParcelStatusHistory>) : TrackingState()
    data class Error(val errorMessage: String) : TrackingState()
}

class TrackingViewModel : ViewModel() {

    private val _timelineState = MutableLiveData<TrackingState>()
    val timelineState: LiveData<TrackingState> get() = _timelineState

    fun fetchTrackingHistory(parcelId: Int) {
        _timelineState.value = TrackingState.Loading
        
        viewModelScope.launch {
            try {
                // Fetch all history logs globally and filter locally by parcelId to prevent index 400 errors
                val response = RetrofitClient.instance.getAllHistoryLogs()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        // Map key-value Firebase objects into sorted lists filtering by parcelId
                        val milestonesList = body.values.filterNotNull().filter { 
                            it.histParclId == parcelId 
                        }.sortedByDescending { it.histTimestamp }
                        _timelineState.value = TrackingState.Success(milestonesList)
                    } else {
                        _timelineState.value = TrackingState.Success(emptyList())
                    }
                } else {
                    _timelineState.value = TrackingState.Error("Server Connection Failed: ${response.code()}")
                }
            } catch (e: Exception) {
                _timelineState.value = TrackingState.Error("Failed to fetch routes: ${e.localizedMessage}")
            }
        }
    }
}
