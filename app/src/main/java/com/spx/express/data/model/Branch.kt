package com.spx.express.data.model

import com.google.gson.annotations.SerializedName

data class Branch(
    @SerializedName("Brnch_Id") val brnchId: Int,
    @SerializedName("Brnch_Name") val brnchName: String,
    @SerializedName("Brnch_Street") val brnchStreet: String?,
    @SerializedName("Brnch_City") val brnchCity: String?,
    @SerializedName("Brnch_Province") val brnchProvince: String?,
    @SerializedName("Brnch_Postal_Code") val brnchPostalCode: String?,
    @SerializedName("Brnch_Contact_Number") val brnchContactNumber: String?,
    @SerializedName("Brnch_Opening_Time") val brnchOpeningTime: String?,
    @SerializedName("Brnch_Closing_Time") val brnchClosingTime: String?,
    @SerializedName("Brnch_Type") val brnchType: String?
)
