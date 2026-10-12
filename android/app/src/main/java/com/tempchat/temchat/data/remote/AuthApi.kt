package com.tempchat.temchat.data.remote

import com.tempchat.temchat.data.LoginRequest
import com.tempchat.temchat.data.LoginResponse
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import android.os.Handler
import android.os.Looper

object AuthApi {

    fun login(
        loginRequest: LoginRequest,
        onSuccess: (LoginResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        val json = JSONObject().apply {
            put("loginId", loginRequest.loginId)
            put("password", loginRequest.password)
        }

        val requestBody = json.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(ApiClient.LOGIN_URL)
            .post(requestBody)
            .build()

        ApiClient.client.newCall(request).enqueue(object : Callback {

            override fun onFailure(
                call: Call,
                e: IOException
            ) {
                android.os.Handler(
                    android.os.Looper.getMainLooper()
                ).post {
                    onError(e.message ?: "Network error")
                }
            }

            override fun onResponse(
                call: Call,
                response: Response
            ) {
                response.use {

                    val responseBody = it.body?.string().orEmpty()

                    if (!it.isSuccessful) {
                        android.os.Handler(
                            android.os.Looper.getMainLooper()
                        ).post {
                            onError("Login failed: HTTP ${it.code}")
                        }
                        return
                    }

                    try {
                        val jsonResponse = JSONObject(responseBody)

                        val loginResponse = LoginResponse(
                            token = jsonResponse.getString("token"),
                            loginId = jsonResponse.getString("loginId"),
                            role = jsonResponse.getString("role"),
                            mustChangeCredentials =
                                jsonResponse.getBoolean("mustChangeCredentials")
                        )

                        android.os.Handler(
                            android.os.Looper.getMainLooper()
                        ).post {
                            onSuccess(loginResponse)
                        }

                    } catch (e: Exception) {
                        android.os.Handler(
                            android.os.Looper.getMainLooper()
                        ).post {
                            onError("Invalid server response")
                        }
                    }
                }
            }
        })
    }


    fun changeCredentials(
        token: String,
        currentPassword: String,
        newLoginId: String,
        newPassword: String,
        onSuccess: (LoginResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        val json = JSONObject().apply {
            put("currentPassword", currentPassword)
            put("newLoginId", newLoginId)
            put("newPassword", newPassword)
        }

        val request = Request.Builder()
            .url("http://10.0.2.2:8080/api/auth/change-credentials")
            .put(
                json.toString().toRequestBody(
                    "application/json".toMediaType()
                )
            )
            .header("Authorization", "Bearer $token")
            .build()

        ApiClient.client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Handler(Looper.getMainLooper()).post {
                    onError(e.message ?: "Network error")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()

                    if (!it.isSuccessful) {
                        Handler(Looper.getMainLooper()).post {
                            onError("Credential change failed: HTTP ${it.code}. $body")
                        }
                        return
                    }

                    try {
                        val result = JSONObject(body)
                        val loginResponse = LoginResponse(
                            token = result.getString("token"),
                            loginId = result.getString("loginId"),
                            role = result.getString("role"),
                            mustChangeCredentials =
                                result.getBoolean("mustChangeCredentials")
                        )

                        Handler(Looper.getMainLooper()).post {
                            onSuccess(loginResponse)
                        }
                    } catch (e: Exception) {
                        Handler(Looper.getMainLooper()).post {
                            onError("Invalid server response")
                        }
                    }
                }
            }
        })
    }

}