package com.example.carvix.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // TODO: change to your deployed backend URL
    // For local testing with emulator: http://10.0.2.2:3000/
    // For deployed Render service: https://your-service.onrender.com/
    private const val BASE_URL = "https://carvix-api.onrender.com/"

    val authApi: AuthApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }
}
