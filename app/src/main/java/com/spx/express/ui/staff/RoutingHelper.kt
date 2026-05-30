package com.spx.express.ui.staff

import com.spx.express.data.model.Parcel

object RoutingHelper {

    fun getRegionOfBranch(branchId: Int): String {
        val luzonBranches = listOf(1, 5, 7, 8, 9, 10, 16, 17, 18, 19, 20, 21)
        val visayasBranches = listOf(2, 4, 6, 11, 12, 14, 15, 24)
        val mindanaoBranches = listOf(3, 13, 22)

        return when {
            luzonBranches.contains(branchId) -> "Luzon"
            visayasBranches.contains(branchId) -> "Visayas"
            mindanaoBranches.contains(branchId) -> "Mindanao"
            else -> "Luzon" // Default fallback
        }
    }

    fun calculateNextHop(currentBranchId: Int, destBranchId: Int): Int {
        if (currentBranchId == destBranchId) return destBranchId

        val currentRegion = getRegionOfBranch(currentBranchId)
        val destRegion = getRegionOfBranch(destBranchId)

        val luzonHubId = 1
        val visayasHubId = 2
        val mindanaoHubId = 3

        // Same region: route directly to the destination branch
        if (currentRegion == destRegion) {
            return destBranchId
        }

        // Cross-region routing: must pass through the regional sorting hubs
        return when (currentRegion) {
            "Luzon" -> {
                if (currentBranchId != luzonHubId) luzonHubId 
                else if (destRegion == "Visayas") visayasHubId else mindanaoHubId
            }
            "Visayas" -> {
                if (currentBranchId != visayasHubId) visayasHubId 
                else if (destRegion == "Luzon") luzonHubId else mindanaoHubId
            }
            "Mindanao" -> {
                if (currentBranchId != mindanaoHubId) mindanaoHubId 
                else if (destRegion == "Luzon") luzonHubId else visayasHubId
            }
            else -> destBranchId
        }
    }

    fun checkRiderCapacity(
        parcel: Parcel,
        activeParcelsForRider: List<Parcel>,
        supervisorOverrideChecked: Boolean
    ): Result {
        if (parcel.parclWeightKg > 10.0 && !supervisorOverrideChecked) {
            return Result.Failure(
                "⚠️ Single parcel limit exceeded! Maximum allowed weight is 10 kg (This parcel: ${parcel.parclWeightKg} kg). Requires Supervisor Override."
            )
        }

        val currentLoad = activeParcelsForRider.sumOf { it.parclWeightKg }
        if (currentLoad + parcel.parclWeightKg > 25.0) {
            return Result.Failure(
                "⚠️ Rider weight capacity exceeded! Maximum allowed load is 25 kg (Rider's current load: $currentLoad kg, trying to add: ${parcel.parclWeightKg} kg)."
            )
        }

        return Result.Success
    }

    sealed class Result {
        object Success : Result()
        data class Failure(val message: String) : Result()
    }
}
