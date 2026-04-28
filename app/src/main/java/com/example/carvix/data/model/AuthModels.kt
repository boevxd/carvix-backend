package com.example.carvix.data.model

data class RegisterRequest(
    val fullName: String,
    val login: String,
    val password: String
)

data class LoginRequest(
    val login: String,
    val password: String
)

data class AuthResponse(
    val success: Boolean,
    val token: String? = null,
    val user: UserDto? = null,
    val error: String? = null,
    val message: String? = null
)

data class UserDto(
    val id: Int? = null,
    val fullName: String,
    val login: String,
    val rolId: Int? = null,
    val podrazdelenieId: Int? = null
)
