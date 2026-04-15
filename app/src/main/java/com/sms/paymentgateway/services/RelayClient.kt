package com.sms.paymentgateway.services

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sms.paymentgateway.utils.security.SecurityManager
import okhttp3.*
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RelayClient - Connects the Android device to the cloud Relay Server via WebSocket.
 *
 * Flow:
 *   Website → HTTPS → Relay Server → WebSocket → RelayClient → Local ApiServer (port 8080)
 *
 * The relay server forwards HTTP requests from the website to this device.
 * This client processes them using the local NanoHTTPD server and sends back the response.
 */
@Singleton
class RelayClient @Inject constructor(
    private val securityManager: SecurityManager,
    private val gson: Gson
) {
    private var webSocket: WebSocket? = null
    private var httpClient: OkHttpClient? = null
    private var isConnected = false
    private var shouldReconnect = true
    private var reconnectDelayMs = 3000L
    private val maxReconnectDelayMs = 60000L

    fun start() {
        shouldReconnect = true
        connect()
    }

    fun stop() {
        shouldReconnect = false
        webSocket?.close(1000, "Service stopped")
        webSocket = null
        isConnected = false
        Timber.i("RelayClient stopped")
    }

    private fun connect() {
        val relayUrl = securityManager.getRelayUrl() ?: run {
            Timber.d("RelayClient: No relay URL configured, skipping connection")
            return
        }

        val apiKey = securityManager.getApiKey()
        Timber.i("RelayClient: Connecting to relay server: $relayUrl")

        httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // No timeout for WebSocket
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(relayUrl)
            .addHeader("X-Api-Key", apiKey)
            .build()

        httpClient?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                this@RelayClient.webSocket = webSocket
                isConnected = true
                reconnectDelayMs = 3000L
                Timber.i("RelayClient: Connected to relay server")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleRelayMessage(webSocket, text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                Timber.e(t, "RelayClient: Connection failed")
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                Timber.i("RelayClient: Disconnected ($code: $reason)")
                if (shouldReconnect && code != 1000) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun handleRelayMessage(webSocket: WebSocket, text: String) {
        try {
            val msg = gson.fromJson(text, JsonObject::class.java)
            val type = msg.get("type")?.asString ?: return

            when (type) {
                "connected" -> {
                    Timber.i("RelayClient: Server confirmed connection: ${msg.get("message")?.asString}")
                }
                "request" -> {
                    processForwardedRequest(webSocket, msg)
                }
                "pong" -> {
                    // Heartbeat response
                }
                else -> {
                    Timber.d("RelayClient: Unknown message type: $type")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "RelayClient: Error handling message")
        }
    }

    private fun processForwardedRequest(webSocket: WebSocket, msg: JsonObject) {
        val requestId = msg.get("requestId")?.asString ?: return
        val method = msg.get("method")?.asString ?: "GET"
        val path = msg.get("path")?.asString ?: "/api/v1/health"
        val query = msg.get("query")?.asString ?: ""
        val body = msg.get("body")?.asString

        Timber.d("RelayClient: Forwarding request [$requestId] $method $path")

        // Forward to local NanoHTTPD server on port 8080
        Thread {
            try {
                val localUrl = "http://localhost:8080$path$query"
                val apiKey = securityManager.getApiKey()

                val connection = URL(localUrl).openConnection() as HttpURLConnection
                connection.requestMethod = method
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (body != null && (method == "POST" || method == "PUT" || method == "PATCH")) {
                    connection.doOutput = true
                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(body)
                        writer.flush()
                    }
                }

                val responseCode = connection.responseCode
                val responseBody = try {
                    BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                } catch (e: Exception) {
                    BufferedReader(InputStreamReader(connection.errorStream ?: return@Thread)).use { it.readText() }
                }

                // Send response back to relay server
                val response = gson.toJson(mapOf(
                    "type" to "response",
                    "requestId" to requestId,
                    "status" to responseCode,
                    "body" to responseBody
                ))
                webSocket.send(response)
                Timber.d("RelayClient: Sent response [$requestId] status=$responseCode")

            } catch (e: Exception) {
                Timber.e(e, "RelayClient: Error forwarding request")
                val errorResponse = gson.toJson(mapOf(
                    "type" to "response",
                    "requestId" to requestId,
                    "status" to 500,
                    "body" to gson.toJson(mapOf("error" to "Device internal error: ${e.message}"))
                ))
                webSocket.send(errorResponse)
            }
        }.start()
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        Timber.i("RelayClient: Reconnecting in ${reconnectDelayMs}ms...")
        Thread.sleep(reconnectDelayMs)
        reconnectDelayMs = minOf(reconnectDelayMs * 2, maxReconnectDelayMs)
        if (shouldReconnect) connect()
    }

    fun isConnected(): Boolean = isConnected
}
