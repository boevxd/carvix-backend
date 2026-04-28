package com.example.carvix.data.remote

import com.example.carvix.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface AuthApi {
    // Auth
    @POST("api/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @GET("api/me")
    suspend fun getMe(@Header("Authorization") token: String): Response<MeResponse>

    // Refs
    @GET("api/refs")
    suspend fun getRefs(@Header("Authorization") token: String): Response<RefsResponse>

    // Zayavki
    @GET("api/zayavki")
    suspend fun getZayavki(
        @Header("Authorization") token: String,
        @Query("status") status: Int? = null,
        @Query("mine") mine: Int? = null,
        @Query("available") available: Int? = null
    ): Response<ZayavkiResponse>

    @GET("api/zayavki/{id}")
    suspend fun getZayavka(@Header("Authorization") token: String, @Path("id") id: Int): Response<ZayavkaDetails>

    @POST("api/zayavki")
    suspend fun createZayavka(@Header("Authorization") token: String, @Body req: CreateZayavkaRequest): Response<SimpleResponse>

    @PUT("api/zayavki/{id}")
    suspend fun updateZayavka(@Header("Authorization") token: String, @Path("id") id: Int, @Body req: UpdateZayavkaRequest): Response<SimpleResponse>

    @DELETE("api/zayavki/{id}")
    suspend fun deleteZayavka(@Header("Authorization") token: String, @Path("id") id: Int): Response<SimpleResponse>

    @POST("api/zayavki/{id}/take")
    suspend fun takeZayavka(@Header("Authorization") token: String, @Path("id") id: Int): Response<SimpleResponse>

    @POST("api/zayavki/{id}/status")
    suspend fun changeStatus(@Header("Authorization") token: String, @Path("id") id: Int, @Body req: StatusChangeRequest): Response<SimpleResponse>

    // TS
    @GET("api/ts")
    suspend fun getTs(@Header("Authorization") token: String): Response<TsResponse>

    @GET("api/ts/{id}")
    suspend fun getOneTs(@Header("Authorization") token: String, @Path("id") id: Int): Response<TsOneResponse>

    @PUT("api/ts/{id}")
    suspend fun updateTs(@Header("Authorization") token: String, @Path("id") id: Int, @Body req: UpdateTsRequest): Response<SimpleResponse>

    // Sotrudniki
    @GET("api/sotrudniki")
    suspend fun getSotrudniki(@Header("Authorization") token: String, @Query("rol_id") rolId: Int? = null): Response<SotrudnikiResponse>

    @GET("api/sotrudniki/mekhaniki/active")
    suspend fun getActiveMechanics(@Header("Authorization") token: String): Response<MekhanikiActiveResponse>

    @POST("api/sotrudniki")
    suspend fun createSotrudnik(@Header("Authorization") token: String, @Body req: CreateSotrudnikRequest): Response<SimpleResponse>

    @PUT("api/sotrudniki/{id}")
    suspend fun updateSotrudnik(@Header("Authorization") token: String, @Path("id") id: Int, @Body req: UpdateSotrudnikRequest): Response<SimpleResponse>

    @DELETE("api/sotrudniki/{id}")
    suspend fun deleteSotrudnik(@Header("Authorization") token: String, @Path("id") id: Int): Response<SimpleResponse>

    // Feedback
    @GET("api/feedback")
    suspend fun getFeedback(@Header("Authorization") token: String): Response<FeedbackResponse>

    @POST("api/feedback")
    suspend fun sendFeedback(@Header("Authorization") token: String, @Body req: FeedbackRequest): Response<SimpleResponse>

    @POST("api/feedback/{id}/read")
    suspend fun markFeedbackRead(@Header("Authorization") token: String, @Path("id") id: Int): Response<SimpleResponse>

    @GET("api/feedback/conversations")
    suspend fun getConversations(@Header("Authorization") token: String): Response<ConversationsResponse>

    @GET("api/feedback/with/{userId}")
    suspend fun getMessagesWith(@Header("Authorization") token: String, @Path("userId") userId: Int): Response<FeedbackResponse>

    @GET("api/feedback/unread")
    suspend fun getUnreadCount(@Header("Authorization") token: String): Response<UnreadResponse>

    @DELETE("api/feedback/with/{userId}")
    suspend fun deleteConversation(@Header("Authorization") token: String, @Path("userId") userId: Int): Response<SimpleResponse>
}
