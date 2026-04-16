package com.sms.paymentgateway.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.sms.paymentgateway.BuildConfig
import com.sms.paymentgateway.utils.security.SecurityManager
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RelayClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityManager: SecurityManager
) {
    private val TAG = "RelayClient"

    // حالة داخلية — أسماء مختلفة عن الدوال العامة لتجنب التعارض
    private var _connected = false
    private var _started   = false

    private var webSocket: WebSocket? = null
    private var reconnectAttempts = 0
    private val MAX_RECONNECT_ATTEMPTS = 50
    private var isManualDisconnect = false

    private val handler = Handler(Looper.getMainLooper())
    private var heartbeatRunnable: Runnable? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(40, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    init {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RelayClient::WakeLock")
        registerNetworkCallback()
    }

    // ─── Public API ─────────────────────────────────────────────────────────

    fun isConnected(): Boolean = _connected && webSocket != null
    fun isStarted(): Boolean   = _started

    fun start() {
        Log.d(TAG, "🚀 بدء RelayClient…")
        _started = true
        isManualDisconnect = false
        reconnectAttempts = 0
        connect()
    }

    fun stop() {
        Log.d(TAG, "⏹️ إيقاف RelayClient…")
        _started = false
        disconnect()
        unregisterNetworkCallback()
    }

    fun connect() {
        if (!_started) return
        isManualDisconnect = false

        val relayUrl = securityManager.getRelayUrl()
        if (relayUrl.isNullOrBlank()) {
            Log.w(TAG, "⚠️ لم يتم ضبط رابط Relay Server بعد")
            return
        }

        if (!isNetworkAvailable()) {
            Log.w(TAG, "لا يوجد إنترنت، سيتم إعادة المحاولة لاحقاً")
            scheduleReconnect()
            return
        }

        Log.d(TAG, "محاولة الاتصال بـ $relayUrl (محاولة ${reconnectAttempts + 1})")

        val request = Request.Builder()
            .url(relayUrl)
            .addHeader("Authorization", "Bearer ${securityManager.getApiKey()}")
            .addHeader("X-Api-Key", securityManager.getApiKey())
            .addHeader("User-Agent", "SMS-Gateway-Android/1.0")
            .build()

        webSocket = client.newWebSocket(request, createListener())
    }

    fun disconnect() {
        isManualDisconnect = true
        stopHeartbeat()
        handler.removeCallbacksAndMessages(null)
        webSocket?.close(1000, "إغلاق يدوي")
        webSocket = null
        _connected = false
        releaseWakeLock()
        Log.d(TAG, "🔌 تم قطع الاتصال يدوياً")
    }

    fun sendMessage(message: String): Boolean {
        if (!isConnected()) {
            Log.e(TAG, "لا يمكن الإرسال: غير متصل")
            return false
        }
        return webSocket?.send(message) == true
    }

    // ─── WebSocket Listener ──────────────────────────────────────────────────

    private fun createListener() = object : WebSocketListener() {

        override fun onOpen(ws: WebSocket, response: Response) {
            _connected = true
            reconnectAttempts = 0
            Log.i(TAG, "✅ WebSocket متصل بنجاح")
            wakeLock?.let { if (!it.isHeld) it.acquire(10 * 60 * 1000L) }
            startHeartbeat()
            sendRegistration()
        }

        override fun onMessage(ws: WebSocket, text: String) {
            Log.d(TAG, "📨 رسالة واردة: $text")
            handleMessage(text)
        }

        override fun onClosing(ws: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "⚠️ WebSocket يُغلق: $code - $reason")
            _connected = false
            releaseWakeLock()
            if (_started && !isManualDisconnect) scheduleReconnect()
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "❌ WebSocket مغلق: $code - $reason")
            _connected = false
            releaseWakeLock()
            if (_started && !isManualDisconnect) scheduleReconnect()
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "💥 فشل الاتصال: ${t.message}")
            _connected = false
            releaseWakeLock()
            if (_started && !isManualDisconnect) scheduleReconnect()
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun scheduleReconnect() {
        if (isManualDisconnect || !_started) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) reconnectAttempts = 0

        val baseDelay = (1L shl reconnectAttempts.coerceAtMost(8)).coerceAtMost(300)
        val jitter    = (Math.random() * 10).toLong()
        val delay     = baseDelay + jitter

        Log.d(TAG, "⏰ إعادة الاتصال بعد ${delay}s (محاولة ${reconnectAttempts + 1}/$MAX_RECONNECT_ATTEMPTS)")
        handler.postDelayed({ if (_started && !isManualDisconnect) connect() }, delay * 1000)
        reconnectAttempts++
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatRunnable = object : Runnable {
            override fun run() {
                if (!_connected || webSocket == null || !_started) return
                val ping = JSONObject().apply {
                    put("type", "ping")
                    put("timestamp", System.currentTimeMillis())
                }.toString()
                val sent = webSocket?.send(ping) ?: false
                if (!sent) {
                    Log.w(TAG, "⚠️ فشل إرسال Heartbeat")
                    _connected = false
                    if (_started && !isManualDisconnect) scheduleReconnect()
                    return
                }
                Log.v(TAG, "💓 Heartbeat أُرسل")
                if (_connected && _started) handler.postDelayed(this, 20_000)
            }
        }
        handler.postDelayed(heartbeatRunnable!!, 20_000)
    }

    private fun stopHeartbeat() {
        heartbeatRunnable?.let { handler.removeCallbacks(it) }
        heartbeatRunnable = null
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    private fun handleMessage(raw: String) {
        try {
            val json = JSONObject(raw)
            when (json.optString("type")) {
                "pong"    -> Log.v(TAG, "💓 Pong استُلم - الاتصال سليم")
                "welcome" -> Log.d(TAG, "🎉 رسالة ترحيب من الخادم")
                "request" -> {
                    val requestId = json.getString("requestId")
                    val method    = json.getString("method")
                    val path      = json.getString("path")
                    Log.d(TAG, "📥 طلب وارد: $method $path")
                    val body = """{"status":"ok","message":"تمت المعالجة"}"""
                    sendMessage(JSONObject().apply {
                        put("type", "response")
                        put("requestId", requestId)
                        put("status", 200)
                        put("body", body)
                    }.toString())
                }
                else -> Log.d(TAG, "❓ رسالة غير معروفة: $json")
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في معالجة الرسالة: ${e.message}")
        }
    }

    private fun sendRegistration() {
        val msg = JSONObject().apply {
            put("type", "register")
            put("deviceId", android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.ANDROID_ID))
            put("deviceType", "android")
            put("appVersion", "1.0")
            put("timestamp", System.currentTimeMillis())
        }.toString()
        sendMessage(msg)
        Log.d(TAG, "📝 تم إرسال تسجيل الجهاز")
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerNetworkCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!_connected && !isManualDisconnect && _started) {
                    handler.postDelayed({
                        if (!_connected && _started && !isManualDisconnect) {
                            reconnectAttempts = 0
                            connect()
                        }
                    }, 2_000)
                }
            }
            override fun onLost(network: Network) {
                Log.w(TAG, "📵 فُقد الاتصال بالشبكة")
                _connected = false
            }
        }
        try { cm.registerNetworkCallback(req, networkCallback!!) }
        catch (e: Exception) { Log.e(TAG, "فشل تسجيل مراقب الشبكة: ${e.message}") }
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
        networkCallback = null
    }
}
