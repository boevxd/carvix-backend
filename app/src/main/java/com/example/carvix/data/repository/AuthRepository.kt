package com.example.carvix.data.repository

import android.content.Context
import androidx.core.content.edit
import com.example.carvix.data.model.LoginRequest
import com.example.carvix.data.model.RegisterRequest
import com.example.carvix.data.model.UserDto
import com.example.carvix.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(context: Context) {
    private val api = RetrofitClient.authApi
    private val prefs = context.getSharedPreferences("carvix_auth", Context.MODE_PRIVATE)

    fun saveSession(token: String, user: UserDto) {
        prefs.edit {
            clear() // полностью сбросить старые данные предыдущего пользователя
            putString("token", token)
            putInt("user_id", user.id ?: 0)
            putString("full_name", user.fullName)
            putString("login", user.login)
            putInt("rol_id", user.rolId ?: 0)
            putInt("podrazdelenie_id", user.podrazdelenieId ?: 0)
        }
    }

    fun getToken(): String? = prefs.getString("token", null)
    fun getAuthHeader(): String = "Bearer ${getToken().orEmpty()}"

    fun getCurrentUser(): UserDto? {
        val name = prefs.getString("full_name", null) ?: return null
        return UserDto(
            id = prefs.getInt("user_id", 0).takeIf { it > 0 },
            fullName = name,
            login = prefs.getString("login", "") ?: "",
            rolId = prefs.getInt("rol_id", 0).takeIf { it > 0 },
            podrazdelenieId = prefs.getInt("podrazdelenie_id", 0).takeIf { it > 0 }
        )
    }

    fun getRolId(): Int = prefs.getInt("rol_id", 0)

    /** Обновить локальные данные пользователя после /api/me, не трогая токен. */
    fun updateUserInfo(id: Int, fullName: String, login: String?, rolId: Int?, podrazdelenieId: Int?) {
        prefs.edit {
            putInt("user_id", id)
            putString("full_name", fullName)
            putString("login", login ?: "")
            putInt("rol_id", rolId ?: 0)
            putInt("podrazdelenie_id", podrazdelenieId ?: 0)
        }
    }

    fun clearToken() {
        prefs.edit { clear() }
    }

    fun isLoggedIn(): Boolean = getToken() != null

    suspend fun register(fullName: String, login: String, password: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.register(RegisterRequest(fullName, login, password))
                if (response.isSuccessful && response.body()?.success == true) {
                    Result.success(response.body()?.message ?: "OK")
                } else {
                    val errBody = response.errorBody()?.string()
                    val msg = response.body()?.error ?: errBody ?: "HTTP ${response.code()}"
                    android.util.Log.e("AuthRepo", "Register failed: $msg")
                    Result.failure(Exception(msg))
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthRepo", "Register exception", e)
                Result.failure(e)
            }
        }

    suspend fun login(login: String, password: String): Result<UserDto> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.login(LoginRequest(login, password))
                if (response.isSuccessful && response.body()?.success == true) {
                    val body = response.body()!!
                    val token = body.token
                    val user = body.user
                    if (token != null && user != null) {
                        saveSession(token, user)
                        Result.success(user)
                    } else {
                        Result.failure(Exception("No token/user"))
                    }
                } else {
                    val errBody = response.errorBody()?.string()
                    val msg = response.body()?.error ?: errBody ?: "HTTP ${response.code()}"
                    android.util.Log.e("AuthRepo", "Login failed: $msg")
                    Result.failure(Exception(msg))
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthRepo", "Login exception", e)
                Result.failure(e)
            }
        }
}
