package com.spx.express.data.model

import com.google.gson.annotations.SerializedName

data class Payment(
    @SerializedName("Pymt_Id") val pymtId: Int,
    @SerializedName("Pymt_Bkng_Id") val pymtBkngId: Int,
    @SerializedName("Pymt_Amount") val pymtAmount: Double,
    @SerializedName("Pymt_Currency") val pymtCurrency: String = "PHP",
    @SerializedName("Pymt_Payment_Method") val pymtPaymentMethod: String?,
    @SerializedName("Pymt_Payment_Status") val pymtPaymentStatus: String = "Pending",
    @SerializedName("Pymt_Payment_Reference") val pymtPaymentReference: String?,
    @SerializedName("Pymt_Payment_Date") val pymtPaymentDate: String
)
