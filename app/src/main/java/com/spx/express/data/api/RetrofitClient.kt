package com.spx.express.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://spx-express-a77a0-default-rtdb.asia-southeast1.firebasedatabase.app/"

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    private val gson by lazy {
        com.google.gson.GsonBuilder()
            .registerTypeHierarchyAdapter(Map::class.java, FirebaseMapDeserializer())
            .registerTypeAdapter(com.spx.express.data.model.Staff::class.java, StaffDeserializer())
            .serializeNulls()
            .create()
    }

    val instance: FirebaseSpxApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(FirebaseSpxApi::class.java)
    }
}
