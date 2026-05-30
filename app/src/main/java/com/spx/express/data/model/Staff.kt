package com.spx.express.data.model

import com.google.gson.annotations.SerializedName

data class Staff(
    @SerializedName("Stf_Id") val stfId: Int,
    @SerializedName("Stf_Email") val stfEmail: String,
    @SerializedName("Stf_Password_Hash") val stfPasswordHash: String,
    @SerializedName("Stf_Fname") val stfFname: String,
    @SerializedName("Stf_Lname") val stfLname: String,
    @SerializedName("Stf_Role") val stfRole: String,  // 'admin', 'branch_manager', 'staff', 'rider'
    @SerializedName("Stf_Contact_Number") val stfContactNumber: String?,
    @SerializedName("Stf_Brch_Id") val stfBrchId: Int?,
    @SerializedName("Stf_Is_Active") val stfIsActive: Int = 1,
    @SerializedName("Stf_Created_At") val stfCreatedAt: String
)

