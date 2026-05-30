package com.spx.express.data.model

import com.google.gson.annotations.SerializedName

data class ServiceType(
    @SerializedName("Srvc_Id") val srvcId: Int,
    @SerializedName("Srvc_Code") val srvcCode: String,
    @SerializedName("Srvc_Description") val srvcDescription: String?,
    @SerializedName("Srvc_Base_Rate") val srvcBaseRate: Double,
    @SerializedName("Srvc_Rate_Per_Kg") val srvcRatePerKg: Double,
    @SerializedName("Srvc_Estimated_Days") val srvcEstimatedDays: Int?
)
