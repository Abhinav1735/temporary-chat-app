package com.tempchat.temchat.data

data class LoginResponse(
    val token: String,
    val loginId: String,
    val role: String,
    val mustChangeCredentials: Boolean
)