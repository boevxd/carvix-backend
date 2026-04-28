package com.example.carvix.data.repository

import com.example.carvix.data.model.*
import com.example.carvix.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class AppRepository(private val authRepo: AuthRepository) {
    private val api = RetrofitClient.authApi
    private fun token() = authRepo.getAuthHeader()

    private suspend fun <T> safeCall(block: suspend () -> Response<T>): Result<T> = withContext(Dispatchers.IO) {
        try {
            val r = block()
            if (r.isSuccessful) {
                r.body()?.let { Result.success(it) } ?: Result.failure(Exception("Empty body"))
            } else {
                val err = r.errorBody()?.string() ?: "HTTP ${r.code()}"
                android.util.Log.e("AppRepo", "Call failed: $err")
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            android.util.Log.e("AppRepo", "Exception", e)
            Result.failure(e)
        }
    }

    suspend fun loadRefs() = safeCall { api.getRefs(token()) }

    // Zayavki
    suspend fun getZayavki(status: Int? = null, mine: Boolean = false, available: Boolean = false) =
        safeCall { api.getZayavki(token(), status, if (mine) 1 else null, if (available) 1 else null) }

    suspend fun getZayavka(id: Int) = safeCall { api.getZayavka(token(), id) }

    suspend fun createZayavka(req: CreateZayavkaRequest) = safeCall { api.createZayavka(token(), req) }
    suspend fun updateZayavka(id: Int, req: UpdateZayavkaRequest) = safeCall { api.updateZayavka(token(), id, req) }
    suspend fun deleteZayavka(id: Int) = safeCall { api.deleteZayavka(token(), id) }
    suspend fun takeZayavka(id: Int) = safeCall { api.takeZayavka(token(), id) }
    suspend fun changeStatus(id: Int, req: StatusChangeRequest) = safeCall { api.changeStatus(token(), id, req) }

    // TS
    suspend fun getTs() = safeCall { api.getTs(token()) }
    suspend fun getOneTs(id: Int) = safeCall { api.getOneTs(token(), id) }
    suspend fun updateTs(id: Int, req: UpdateTsRequest) = safeCall { api.updateTs(token(), id, req) }

    // Sotrudniki
    suspend fun getSotrudniki(rolId: Int? = null) = safeCall { api.getSotrudniki(token(), rolId) }
    suspend fun getActiveMechanics() = safeCall { api.getActiveMechanics(token()) }
    suspend fun createSotrudnik(req: CreateSotrudnikRequest) = safeCall { api.createSotrudnik(token(), req) }
    suspend fun updateSotrudnik(id: Int, req: UpdateSotrudnikRequest) = safeCall { api.updateSotrudnik(token(), id, req) }
    suspend fun deleteSotrudnik(id: Int) = safeCall { api.deleteSotrudnik(token(), id) }

    // Feedback
    suspend fun getFeedback() = safeCall { api.getFeedback(token()) }
    suspend fun sendFeedback(req: FeedbackRequest) = safeCall { api.sendFeedback(token(), req) }
    suspend fun markFeedbackRead(id: Int) = safeCall { api.markFeedbackRead(token(), id) }
    suspend fun getConversations() = safeCall { api.getConversations(token()) }
    suspend fun getMessagesWith(userId: Int) = safeCall { api.getMessagesWith(token(), userId) }
    suspend fun getMe() = safeCall { api.getMe(token()) }
    suspend fun getUnreadCount() = safeCall { api.getUnreadCount(token()) }
    suspend fun deleteConversation(userId: Int) = safeCall { api.deleteConversation(token(), userId) }
}
