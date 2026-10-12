
package com.tempchat.temchat.data.remote

import android.os.Handler
import android.os.Looper
import com.tempchat.temchat.data.LoginResponse
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class ChatUser(
    val id: Long,
    val loginId: String,
    val role: String,
    val enabled: Boolean
)

object ChatUsersApi {

    private const val BASE_URL = "http://10.0.2.2:8080/api/users"

    private val mainHandler = Handler(Looper.getMainLooper())

    fun fetchUsers(
        token: String,
        onSuccess: (List<ChatUser>) -> Unit,
        onError: (String) -> Unit
    ) {
        val request = Request.Builder()
            .url("$BASE_URL/chat-list")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        executeListRequest(request, onSuccess, onError)
    }

    fun searchUsers(
        token: String,
        query: String,
        onSuccess: (List<ChatUser>) -> Unit,
        onError: (String) -> Unit
    ) {
        if (query.isBlank()) {
            mainHandler.post { onSuccess(emptyList()) }
            return
        }

        val encodedQuery = java.net.URLEncoder.encode(
            query.trim(),
            "UTF-8"
        )

        val request = Request.Builder()
            .url("$BASE_URL/search?q=$encodedQuery")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        executeListRequest(request, onSuccess, onError)
    }

    fun addContact(
        token: String,
        userId: Long,
        onSuccess: (ChatUser) -> Unit,
        onError: (String) -> Unit
    ) {
        val request = Request.Builder()
            .url("$BASE_URL/chat-list/$userId")
            .header("Authorization", "Bearer $token")
            .post(
                okhttp3.RequestBody.create(
                    null,
                    ByteArray(0)
                )
            )
            .build()

        ApiClient.client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    onError(e.message ?: "Network error")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()

                    if (!it.isSuccessful) {
                        val message = try {
                            JSONObject(body).optString(
                                "message",
                                "Could not add user: HTTP ${it.code}"
                            )
                        } catch (_: Exception) {
                            "Could not add user: HTTP ${it.code}"
                        }

                        mainHandler.post { onError(message) }
                        return
                    }

                    try {
                        val user = parseUser(JSONObject(body))
                        mainHandler.post { onSuccess(user) }
                    } catch (e: Exception) {
                        mainHandler.post {
                            onError("Invalid server response")
                        }
                    }
                }
            }
        })
    }

    private fun executeListRequest(
        request: Request,
        onSuccess: (List<ChatUser>) -> Unit,
        onError: (String) -> Unit
    ) {
        ApiClient.client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    onError(e.message ?: "Network error")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()

                    if (!it.isSuccessful) {
                        mainHandler.post {
                            onError("Request failed: HTTP ${it.code}")
                        }
                        return
                    }

                    try {
                        val array = JSONArray(body)
                        val users = mutableListOf<ChatUser>()

                        for (i in 0 until array.length()) {
                            users.add(parseUser(array.getJSONObject(i)))
                        }

                        mainHandler.post { onSuccess(users) }
                    } catch (e: Exception) {
                        mainHandler.post {
                            onError("Invalid server response")
                        }
                    }
                }
            }
        })
    }

    private fun parseUser(json: JSONObject): ChatUser {
        return ChatUser(
            id = json.getLong("id"),
            loginId = json.getString("loginId"),
            role = json.optString("role", "USER"),
            enabled = json.optBoolean("enabled", true)
        )
    }
}
