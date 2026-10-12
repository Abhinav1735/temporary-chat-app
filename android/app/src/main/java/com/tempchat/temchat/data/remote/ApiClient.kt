package com.tempchat.temchat.data.remote

import okhttp3.OkHttpClient

object ApiClient {

    private const val BASE_URL = "http://10.0.2.2:8080"

    val client: OkHttpClient = OkHttpClient()

    const val LOGIN_URL = "$BASE_URL/api/auth/login"
}