package com.spx.express.data.model

import com.google.gson.annotations.SerializedName

data class Parcel(
    @SerializedName("Parcl_Id") val parclId: Int,
    @SerializedName("Parcl_Bkng_Id") val parclBkngId: Int,
    @SerializedName("Parcl_Tracking_Number") val parclTrackingNumber: String,
    @SerializedName("Parcl_Sender_Address") val parclSenderAddress: String?,
    @SerializedName("Parcl_Receiver_Name") val parclReceiverName: String,
    @SerializedName("Parcl_Receiver_Phone") val parclReceiverPhone: String,
    @SerializedName("Parcl_Receiver_Address") val parclReceiverAddress: String?,
    @SerializedName("Parcl_Weight_Kg") val parclWeightKg: Double,
    @SerializedName("Parcl_Delivery_Status") val parclDeliveryStatus: String,
    @SerializedName("Parcl_Cod_Amount") val parclCodAmount: Double,
    @SerializedName("Parcl_Rider_Id") val parclRiderId: Int?,
    @SerializedName("Parcl_Proof_Img") val parclProofImg: String?,
    @SerializedName("Parcl_Receiver_Street") val parclReceiverStreet: String?,
    @SerializedName("Parcl_Receiver_City") val parclReceiverCity: String?,
    @SerializedName("Parcl_Receiver_Province") val parclReceiverProvince: String?,
    @SerializedName("Parcl_Receiver_Postal_Code") val parclReceiverPostalCode: String?,
    @SerializedName("Parcl_Orig_Brch_Id") val parclOrigBrchId: Int? = null,
    @SerializedName("Parcl_Dest_Brch_Id") val parclDestBrchId: Int? = null,
    @SerializedName("Parcl_Next_Hop_Brch_Id") val parclNextHopBrchId: Int? = null,
    @SerializedName("Parcl_Declared_Value") val parclDeclaredValue: Double? = null,
    @SerializedName("Parcl_Srvc_Id") val parclSrvcId: Int? = null,
    @SerializedName("Parcl_Remit_Status") val parclRemitStatus: String? = null,
    var firebaseKey: String? = null
)
