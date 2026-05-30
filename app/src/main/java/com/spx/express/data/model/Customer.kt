package com.spx.express.data.model

import com.google.gson.annotations.SerializedName

data class Customer(
    @SerializedName("Cust_Id") val custId: Int,
    @SerializedName("Cust_Email") val custEmail: String,
    @SerializedName("Cust_Password_Hash") val custPasswordHash: String,
    @SerializedName("Cust_Fname") val custFname: String,
    @SerializedName("Cust_Lname") val custLname: String,
    @SerializedName("Cust_Contact_Number") val custContactNumber: String?,
    @SerializedName("Cust_Street") val custStreet: String?,
    @SerializedName("Cust_City") val custCity: String?,
    @SerializedName("Cust_Province") val custProvince: String?,
    @SerializedName("Cust_Postal_Code") val custPostalCode: String?,
    @SerializedName("Cust_Is_Active") val custIsActive: Int = 1,
    @SerializedName("Cust_Created_At") val custCreatedAt: String
)
