package com.spx.express.data.api

import com.spx.express.data.model.Customer
import com.spx.express.data.model.Staff
import com.spx.express.data.model.Parcel
import com.spx.express.data.model.ParcelStatusHistory
import com.spx.express.data.model.Payment
import com.spx.express.data.model.Branch
import com.spx.express.data.model.Booking
import retrofit2.Response
import retrofit2.http.*

interface FirebaseSpxApi {

    // Fetch tracking milestones for a specific parcel
    @GET("parcelstatushistory.json")
    suspend fun getTrackingHistory(
        @Query("orderBy") orderBy: String = "\"Hist_Parcl_Id\"",
        @Query("equalTo") parcelId: Int
    ): Response<Map<String, ParcelStatusHistory>>

    // Fetch parcel details by tracking number string
    @GET("parcel.json")
    suspend fun getParcelByTrackingNumber(
        @Query("orderBy") orderBy: String = "\"Parcl_Tracking_Number\"",
        @Query("equalTo") trackingNumber: String
    ): Response<Map<String, Parcel>>

    // Fetch parcels assigned to a specific rider
    @GET("parcel.json")
    suspend fun getRiderParcels(
        @Query("orderBy") orderBy: String = "\"Parcl_Rider_Id\"",
        @Query("equalTo") riderId: Int
    ): Response<Map<String, Parcel>>

    // Fetch all parcels in the database (for filtering by customer or admin metrics)
    @GET("parcel.json")
    suspend fun getAllParcels(): Response<Map<String, Parcel>>

    // Fetch customer details by email for native sign-in authentication
    @GET("customer.json")
    suspend fun getCustomerByEmail(
        @Query("orderBy") orderBy: String = "\"Cust_Email\"",
        @Query("equalTo") email: String
    ): Response<Map<String, Customer>>

    // Fetch staff details by email for native sign-in authentication
    @GET("staff.json")
    suspend fun getStaffByEmail(
        @Query("orderBy") orderBy: String = "\"Stf_Email\"",
        @Query("equalTo") email: String
    ): Response<Map<String, Staff>>

    // Fetch all staff members in the database (for roster or branch filtering)
    @GET("staff.json")
    suspend fun getAllStaff(): Response<Map<String, Staff>>

    // Fetch all customers in the database
    @GET("customer.json")
    suspend fun getAllCustomers(): Response<Map<String, Customer>>

    // PATCH update to modify delivery status and proof of delivery image
    @PATCH("parcel/{parcelId}.json")
    suspend fun updateParcelStatus(
        @Path("parcelId") parcelId: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any>
    ): Response<Map<String, Any>>

    // PATCH update to modify staff details
    @PATCH("staff/{staffId}.json")
    suspend fun updateStaffStatus(
        @Path("staffId") staffId: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any>
    ): Response<Map<String, Any>>

    // Add milestone event to the history logs
    @POST("parcelstatushistory.json")
    suspend fun addHistoryLog(
        @Body log: ParcelStatusHistory
    ): Response<Map<String, String>>

    // Fetch all history logs globally (for branch manager log overview)
    @GET("parcelstatushistory.json")
    suspend fun getAllHistoryLogs(): Response<Map<String, ParcelStatusHistory>>

    // Create a new parcel natively — PUT to explicit path so node key matches the integer ID
    @PUT("parcel/{parcelId}.json")
    suspend fun addParcel(
        @Path("parcelId") parcelId: Int,
        @Body parcel: Parcel
    ): Response<Map<String, String>>

    // PATCH update to modify booking status
    @PATCH("booking/{bookingId}.json")
    suspend fun updateBookingStatus(
        @Path("bookingId") bookingId: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any>
    ): Response<Map<String, Any>>

    // Fetch all payments
    @GET("payment.json")
    suspend fun getAllPayments(): Response<Map<String, Payment>>

    // Fetch all branches
    @GET("branch.json")
    suspend fun getAllBranches(): Response<Map<String, Branch>>

    // PATCH update to modify payment status
    @PATCH("payment/{paymentId}.json")
    suspend fun updatePaymentStatus(
        @Path("paymentId") paymentId: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any>
    ): Response<Map<String, Any>>

    // Fetch all bookings
    @GET("booking.json")
    suspend fun getAllBookings(): Response<Map<String, Booking>>

    // Create a new booking natively — PUT to explicit path so node key matches the integer ID
    @PUT("booking/{bookingId}.json")
    suspend fun addBooking(
        @Path("bookingId") bookingId: Int,
        @Body booking: Booking
    ): Response<Map<String, String>>

    // Create a new payment natively — PUT to explicit path so node key matches the integer ID
    @PUT("payment/{paymentId}.json")
    suspend fun addPayment(
        @Path("paymentId") paymentId: Int,
        @Body payment: Payment
    ): Response<Map<String, String>>

    // PATCH update to modify customer details/profile
    @PATCH("customer/{customerId}.json")
    suspend fun updateCustomerProfile(
        @Path("customerId") customerId: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any>
    ): Response<Map<String, Any>>

    // Create a new customer natively — PUT to explicit path so node key matches the integer ID
    @PUT("customer/{customerId}.json")
    suspend fun addCustomer(
        @Path("customerId") customerId: Int,
        @Body customer: Customer
    ): Response<Map<String, String>>

    // Create a new branch natively — PUT to explicit path
    @PUT("branch/{branchId}.json")
    suspend fun addBranch(
        @Path("branchId") branchId: Int,
        @Body branch: Branch
    ): Response<Map<String, String>>

    // Create a new staff natively — PUT to explicit path
    @PUT("staff/{staffId}.json")
    suspend fun addStaff(
        @Path("staffId") staffId: Int,
        @Body staff: Staff
    ): Response<Map<String, String>>

    // DELETE staff record
    @DELETE("staff/{staffId}.json")
    suspend fun deleteStaff(
        @Path("staffId") staffId: Int
    ): Response<Any?>

    // DELETE branch record
    @DELETE("branch/{branchId}.json")
    suspend fun deleteBranch(
        @Path("branchId") branchId: Int
    ): Response<Any?>
}
