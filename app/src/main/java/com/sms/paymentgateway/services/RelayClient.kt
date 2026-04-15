package com.sms.paymentgateway.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.PowerManager
import android.util.Log
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RelayClient(private val context: Context) {
    private val TAG = "RelayClient"
    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var reconnectAttempts = 0
    private val MAX_RECONNECT_ATTEMPTS = 10
    private var isManualDisconnect = false
    private var heartbeatRunnable: Runnable? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // ⚠️ استخدم الرابط الصحيح لخادم Hugging Face
    private val SERVER_URL = "wss://nopoh22-sms-relay-server.hf.space/device"
    private val AUTH_TOKEN = "YOUR_API_KEY_HERE"  // استبدل بمفتاح API الفعلي

    private val client: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    init {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RelayClient::WakeLock")
    }

    fun connect() {
        isManualDisconnect = false
        Log.d(TAG, "محاولة الاتصال بالخادم...")
        val request = Request.Builder()
            .url(SERVER_URL)
            .addHeader("Authorization", "Bearer $AUTH_TOKEN")
            .addHeader("X-Api-Key", AUTH_TOKEN)
            .build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                isConnected = true
                reconnectAttempts = 0
                Log.d(TAG, "✅ WebSocket متصل بنجاح")
                wakeLock?.let { if (!it.isHeld) it.acquire(10 * 60 * 1000L) }
                startManualHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                Log.d(TAG, "رسالة واردة: $text")
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosing(webSocket, code, reason)
                Log.w(TAG, "WebSocket يتم إغلاقه: $code - $reason")
                isConnected = false
                releaseWakeLock()
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                Log.w(TAG, "WebSocket مغلق: $code - $reason")
                isConnected = false
                releaseWakeLock()
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                Log.e(TAG, "❌ فشل الاتصال: ${t.message}")
                isConnected = false
                releaseWakeLock()
                scheduleReconnect()
            }
        })
    }

    fun disconnect() {
        isManualDisconnect = true
        stopManualHeartbeat()
        webSocket?.close(1000, "Manual disconnect")
        webSocket = null
        isConnected = false
        releaseWakeLock()
        Log.d(TAG, "تم قطع الاتصال يدويًا")
    }

    fun sendMessage(message: String): Boolean {
        if (!isConnected || webSocket == null) {
            Log.e(TAG, "لا يمكن الإرسال: غير متصل")
            return false
        }
        webSocket?.send(message)
        Log.d(TAG, "تم إرسال: $message")
        return true
    }

    private fun scheduleReconnect() {
        if (isManualDisconnect) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "وصلت محاولات إعادة الاتصال إلى الحد الأقصى، توقف.")
            return
        }
        val delay = (1 shl reconnectAttempts.coerceAtMost(5)).coerceAtMost(60).toLong()
        Log.d(TAG, "سيتم إعادة الاتصال بعد $delay ثانية (المحاولة ${reconnectAttempts + 1})")
        android.os.Handler(context.mainLooper).postDelayed({ connect() }, delay * 1000)
        reconnectAttempts++
    }

    private fun startManualHeartbeat() {
        stopManualHeartbeat()
        heartbeatRunnable = Runnable {
            if (isConnected && webSocket != null) {
                webSocket?.send(JSONObject().put("type", "ping").toString())
                Log.v(TAG, "❤️ Heartbeat sent")
            }
            if (isConnected) {
                android.os.Handler(context.mainLooper).postDelayed(heartbeatRunnable!!, 30000)
            }
        }
        heartbeatRunnable?.let { android.os.Handler(context.mainLooper).postDelayed(it, 30000) }
    }

    private fun stopManualHeartbeat() {
        heartbeatRunnable?.let { android.os.Handler(context.mainLooper).removeCallbacks(it) }
        heartbeatRunnable = null
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    private fun handleIncomingMessage(message: String) {
        try {
            val json = JSONObject(message)
            when (json.optString("type")) {
                "pong" -> Log.v(TAG, "Pong received")
                "request" -> {
                    val requestId = json.getString("requestId")
                    val method = json.getString("method")
                    val path = json.getString("path")
                    val body = json.optString("body")
                    // هنا يمكنك إضافة منطق معالجة الطلب (مثل إرسال SMS)
                    val responseBody = """{"status":"ok"}"""
                    sendMessage(JSONObject().apply {
                        put("type", "response")
                        put("requestId", requestId)
                        put("status", 200)
                        put("body", responseBody)
                    }.toString())
                }
                else -> Log.d(TAG, "رسالة غير معروفة: $json")
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في معالجة الرسالة: ${e.message}")
        }
    }

    fun registerNetworkCallback() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.d(TAG, "الشبكة متاحة، إعادة محاولة الاتصال إن لزم")
                if (!isConnected && !isManualDisconnect) connect()
            }
            override fun onLost(network: Network) {
                super.onLost(network)
                Log.w(TAG, "فقدان الشبكة")
                isConnected = false
            }
        })
    }
}
