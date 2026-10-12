
package com.tempchat.temchat.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Executors

data class AdminUser(
    val id: Long,
    val loginId: String,
    val role: String,
    val mustChangeCredentials: Boolean,
    val enabled: Boolean
)

object AdminApi {

    private const val BASE_URL = "http://10.0.2.2:8080"
    private const val USERS_URL = "$BASE_URL/api/admin/users"

    private val executor = Executors.newSingleThreadExecutor()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun execute(
        token: String,
        method: String,
        url: String,
        body: JSONObject? = null,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        executor.execute {
            try {
                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")

                if (body != null) {
                    requestBuilder.method(
                        method,
                        body.toString().toRequestBody(jsonType)
                    )
                } else {
                    requestBuilder.method(method, null)
                }

                ApiClient.client.newCall(requestBuilder.build()).execute().use {
                        response ->
                    val responseText = response.body?.string().orEmpty()

                    if (response.isSuccessful) {
                        onSuccess(responseText)
                    } else {
                        val message = try {
                            JSONObject(responseText)
                                .optString("message")
                                .takeIf { it.isNotBlank() }
                        } catch (_: Exception) {
                            null
                        }

                        onError(
                            message ?: when (response.code) {
                                400 -> "Invalid request. Check the entered values."
                                401 -> "Session expired. Please log in again."
                                403 -> "Administrator permission is required."
                                404 -> "User or endpoint not found."
                                else -> "Request failed (${response.code})."
                            }
                        )
                    }
                }
            } catch (e: IOException) {
                onError("Network error. Check that TempChat server is running.")
            } catch (e: Exception) {
                onError(e.message ?: "An unexpected error occurred.")
            }
        }
    }

    private fun parseUser(json: JSONObject): AdminUser {
        return AdminUser(
            id = json.getLong("id"),
            loginId = json.getString("loginId"),
            role = json.optString("role", "USER"),
            mustChangeCredentials = json.optBoolean(
                "mustChangeCredentials", false
            ),
            enabled = json.optBoolean("enabled", true)
        )
    }

    private fun parseUsers(json: String): List<AdminUser> {
        val array = JSONArray(json)
        return (0 until array.length()).map { index ->
            parseUser(array.getJSONObject(index))
        }
    }

    fun getUsers(
        token: String,
        onSuccess: (List<AdminUser>) -> Unit,
        onError: (String) -> Unit
    ) {
        execute(
            token = token,
            method = "GET",
            url = USERS_URL,
            onSuccess = { onSuccess(parseUsers(it)) },
            onError = onError
        )
    }

    fun createUser(
        token: String,
        loginId: String,
        password: String,
        onSuccess: (AdminUser) -> Unit,
        onError: (String) -> Unit
    ) {
        val body = JSONObject()
            .put("loginId", loginId)
            .put("password", password)

        execute(
            token = token,
            method = "POST",
            url = USERS_URL,
            body = body,
            onSuccess = { onSuccess(parseUser(JSONObject(it))) },
            onError = onError
        )
    }

    fun resetPassword(
        token: String,
        userId: Long,
        newPassword: String,
        onSuccess: (AdminUser) -> Unit,
        onError: (String) -> Unit
    ) {
        val body = JSONObject().put("newPassword", newPassword)

        execute(
            token = token,
            method = "PUT",
            url = "$USERS_URL/$userId/reset-password",
            body = body,
            onSuccess = { onSuccess(parseUser(JSONObject(it))) },
            onError = onError
        )
    }

    fun updateStatus(
        token: String,
        userId: Long,
        enabled: Boolean,
        onSuccess: (AdminUser) -> Unit,
        onError: (String) -> Unit
    ) {
        val body = JSONObject().put("enabled", enabled)

        execute(
            token = token,
            method = "PUT",
            url = "$USERS_URL/$userId/status",
            body = body,
            onSuccess = { onSuccess(parseUser(JSONObject(it))) },
            onError = onError
        )
    }

    fun deleteUser(
        token: String,
        userId: Long,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        execute(
            token = token,
            method = "DELETE",
            url = "$USERS_URL/$userId",
            onSuccess = { onSuccess() },
            onError = onError
        )
    }
}
