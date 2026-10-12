package com.tempchat.temchat.data.remote

import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

object ChatWebSocket {

    private var webSocket: WebSocket? = null

    fun connect(
        token: String,
        onConnected: () -> Unit,
        onMessage: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        disconnect()

        val request = Request.Builder()
            .url("ws://10.0.2.2:8080/ws/chat")
            .header("Authorization", "Bearer $token")
            .build()

        webSocket = ApiClient.client.newWebSocket(
            request,
            object : WebSocketListener() {

                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response
                ) {
                    onConnected()
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String
                ) {
                    onMessage(text)
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    bytes: ByteString
                ) {
                    onMessage(bytes.hex())
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?
                ) {
                    onError(
                        response?.let {
                            "WebSocket failed: HTTP ${it.code}"
                        } ?: "WebSocket error: ${t.message}"
                    )
                }
            }
        )
    }

    fun sendMessage(receiver: String, message: String): Boolean {
        val json = org.json.JSONObject().apply {
            put("receiver", receiver)
            put("message", message)
        }

        return webSocket?.send(json.toString()) == true
    }

    fun disconnect() {
        webSocket?.close(1000, "Disconnected")
        webSocket = null
    }
}