package com.spx.express.data.model

import com.google.gson.annotations.SerializedName

data class ParcelStatusHistory(
    @SerializedName("Hist_Id") val histId: Int = 0,
    @SerializedName("Hist_Parcl_Id") val histParclId: Int,
    @SerializedName("Hist_Status") val histStatus: String,
    @SerializedName("Hist_Location_Note") val histLocationNote: String?,
    @SerializedName("Hist_Timestamp") val histTimestamp: String,
    @SerializedName("Hist_Stf_Id") val histStfId: Int?,
    @SerializedName("Hist_Remark") val histRemark: String?
)
