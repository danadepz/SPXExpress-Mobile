package com.spx.express.data.model

import com.google.gson.annotations.SerializedName

data class Booking(
    @SerializedName("Bkng_Id") val bkngId: Int,
    @SerializedName("Bkng_Customer_Id") val bkngCustomerId: Int,
    @SerializedName("Bkng_Booking_Date") val bkngBookingDate: String,
    @SerializedName("Bkng_Total_Parcels") val bkngTotalParcels: Int = 1,
    @SerializedName("Bkng_Total_Weight_Kg") val bkngTotalWeightKg: Double?,
    @SerializedName("Bkng_Status") val bkngStatus: String = "Pending",
    @SerializedName("Bkng_Created_At") val bkngCreatedAt: String,
    @SerializedName("Bkng_Updated_At") val bkngUpdatedAt: String
)
