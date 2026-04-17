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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RelayClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityManager: SecurityManager
) {
    private val TAG = "RelayClient"

    private val _connected = AtomicBoolean(false)
    private val _started   = AtomicBoolean(false)
    private val isManualDisconnect = AtomicBoolean(false)
    private val reconnectAttempts = AtomicInteger(0)
    private val lastSuccessfulConnection = AtomicLong(0L)
    private val lastPongReceived = AtomicLong(0L)

    /** callback لمعالجة طلبات HTTP الواردة عبر الـ Relay */
    var tunnelRequestHandler: ((method: String, path: String, headers: Map<String, String>, body: String?) -> TunnelResponse)? = null

    private @Volatile var webSocket: WebSocket? = null

    private val handler = Handler(Looper.getMainLooper())
    private var heartbeatRunnable: Runnable? = null
    private var connectionCheckRunnable: Runnable? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        private const val MAX_BACKOFF_SECONDS = 300L    // 5 دقائق كحد أقصى
        private const val HEARTBEAT_INTERVAL  = 20_000L // كل 20 ثانية
        private const val PONG_TIMEOUT_MS     = 60_000L // 1 دقيقة بدون pong = إعادة اتصال
        private const val MONITOR_INTERVAL    = 60_000L // فحص كل دقيقة
        private const val NETWORK_DELAY_MS    = 2_000L  // تأخير عند توفر الشبكة
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    init {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RelayClient::WakeLock")
        registerNetworkCallback()
    }

    // ─── Public API ─────────────────────────────────────────────────────────

    fun isConnected(): Boolean = _connected.get() && webSocket != null
    fun isStarted(): Boolean   = _started.get()

    fun start() {
        Log.d(TAG, "🚀 بدء RelayClient…")
        _started.set(true)
        isManualDisconnect.set(false)
        reconnectAttempts.set(0)
        connect()
        startConnectionMonitor()
    }

    fun stop() {
        Log.d(TAG, "⏹️ إيقاف RelayClient…")
        _started.set(false)
        disconnect()
        stopConnectionMonitor()
        unregisterNetworkCallback()
    }

    fun connect() {
        if (!_started.get()) return
        if (isManualDisconnect.get()) return

        val relayUrl = securityManager.getRelayUrl()
        if (relayUrl.isBlank()) {
            Log.w(TAG, "⚠️ رابط Relay Server فارغ")
            return
        }

        if (!isNetworkAvailable()) {
            Log.w(TAG, "لا يوجد إنترنت، سيتم إعادة المحاولة عند توفر الشبكة")
            return
        }

        // تجنّب الاتصال المزدوج
        if (_connected.get() && webSocket != null) {
            Log.d(TAG, "✅ متصل بالفعل، لا حاجة لإعادة الاتصال")
            return
        }

        Log.d(TAG, "محاولة الاتصال بـ $relayUrl (محاولة ${reconnectAttempts.get() + 1})")

        val request = Request.Builder()
            .url(relayUrl)
            .addHeader("Authorization", "Bearer ${securityManager.getApiKey()}")
            .addHeader("X-Api-Key", securityManager.getApiKey() ?: "")
            .addHeader("User-Agent", "SMS-Gateway-Android/2.0")
            .build()

        webSocket = client.newWebSocket(request, createListener())
    }

    fun disconnect() {
        isManualDisconnect.set(true)
        stopHeartbeat()
        handler.removeCallbacksAndMessages(null)
        webSocket?.close(1000, "إغلاق يدوي")
        webSocket = null
        _connected.set(false)
        releaseWakeLock()
        Log.d(TAG, "🔌 تم قطع الاتصال يدوياً")
    }

    fun sendMessage(message: String): Boolean {
        if (!isConnected()) {
            Log.e(TAG, "لا يمكن الإرسال: غير متصل")
            return false
        }
        val sent = webSocket?.send(message) == true
        if (sent) lastSuccessfulConnection.set(System.currentTimeMillis())
        return sent
    }

    // ─── WebSocket Listener ──────────────────────────────────────────────────

    private fun createListener() = object : WebSocketListener() {

        override fun onOpen(ws: WebSocket, response: Response) {
            _connected.set(true)
            reconnectAttempts.set(0)
            val now = System.currentTimeMillis()
            lastSuccessfulConnection.set(now)
            lastPongReceived.set(now)
            Log.i(TAG, "✅ WebSocket متصل بنجاح بـ Huggingface Relay")
            wakeLock?.let { if (!it.isHeld) it.acquire(10 * 60 * 1000L) }
            startHeartbeat()
            sendRegistration()
        }

        override fun onMessage(ws: WebSocket, text: String) {
            Log.d(TAG, "📨 رسالة واردة: $text")
            lastSuccessfulConnection.set(System.currentTimeMillis())
            handleMessage(text)
        }

        override fun onClosing(ws: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "⚠️ WebSocket يُغلق: $code - $reason")
            ws.close(1000, null)
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "❌ WebSocket مغلق: $code - $reason")
            _connected.set(false)
            webSocket = null
            releaseWakeLock()
            stopHeartbeat()
            if (_started.get() && !isManualDisconnect.get()) scheduleReconnect()
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "💥 فشل الاتصال: ${t.message}")
            _connected.set(false)
            webSocket = null
            releaseWakeLock()
            stopHeartbeat()
            if (_started.get() && !isManualDisconnect.get()) scheduleReconnect()
        }
    }

    // ─── Reconnect ───────────────────────────────────────────────────────────

    private fun scheduleReconnect() {
        if (isManualDisconnect.get() || !_started.get()) return

        val attempts = reconnectAttempts.get()

        // إعادة تعيين العداد بعد 50 محاولة لمنع التأخير الطويل جداً
        if (attempts >= 50) {
            reconnectAttempts.set(10)
            Log.d(TAG, "🔄 إعادة تعيين عداد المحاولات")
        }

        // Exponential backoff: 1s, 2s, 4s, 8s … حتى 300s
        val baseDelay = (1L shl reconnectAttempts.get().coerceAtMost(8)).coerceAtMost(MAX_BACKOFF_SECONDS)
        val jitter    = (Math.random() * 5).toLong()
        val delay     = baseDelay + jitter

        Log.d(TAG, "⏰ إعادة الاتصال بعد ${delay}s (محاولة ${attempts + 1})")
        handler.postDelayed({
            if (_started.get() && !isManualDisconnect.get() && !_connected.get()) {
                Log.d(TAG, "🔄 محاولة إعادة الاتصال...")
                connect()
            }
        }, delay * 1000)
        reconnectAttempts.incrementAndGet()
    }

    // ─── Connection Monitor ──────────────────────────────────────────────────

    private fun startConnectionMonitor() {
        stopConnectionMonitor()
        connectionCheckRunnable = object : Runnable {
            override fun run() {
                if (!_started.get()) return

                if (!_connected.get() && isNetworkAvailable() && !isManualDisconnect.get()) {
                    Log.w(TAG, "⚠️ مراقب الاتصال: غير متصل رغم وجود الإنترنت - إعادة الاتصال")
                    reconnectAttempts.set(0)
                    connect()
                } else if (_connected.get()) {
                    // فحص آخر pong/رسالة مستلمة
                    val timeSinceLastPong = System.currentTimeMillis() - lastPongReceived.get()
                    if (timeSinceLastPong > PONG_TIMEOUT_MS) {
                        Log.w(TAG, "⚠️ لم يُستلم pong منذ ${timeSinceLastPong / 1000}s - إعادة الاتصال")
                        val oldWs = webSocket
                        webSocket = null
                        _connected.set(false)
                        stopHeartbeat()
                        oldWs?.cancel()
                        reconnectAttempts.set(0)
                        handler.postDelayed({ if (_started.get() && !isManualDisconnect.get()) connect() }, 1_000)
                    }
                }

                if (_started.get()) {
                    handler.postDelayed(this, MONITOR_INTERVAL)
                }
            }
        }
        handler.postDelayed(connectionCheckRunnable!!, MONITOR_INTERVAL)
        Log.d(TAG, "🔍 بدء مراقب الاتصال")
    }

    private fun stopConnectionMonitor() {
        connectionCheckRunnable?.let { handler.removeCallbacks(it) }
        connectionCheckRunnable = null
    }

    // ─── Heartbeat ───────────────────────────────────────────────────────────

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatRunnable = object : Runnable {
            override fun run() {
                if (!_connected.get() || webSocket == null || !_started.get()) {
                    Log.w(TAG, "⚠️ Heartbeat: الاتصال مفقود")
                    return
                }

                val ping = JSONObject().apply {
                    put("type", "ping")
                    put("timestamp", System.currentTimeMillis())
                }.toString()

                val sent = webSocket?.send(ping) == true
                if (!sent) {
                    Log.w(TAG, "⚠️ فشل إرسال Heartbeat - إعادة الاتصال")
                    _connected.set(false)
                    webSocket?.cancel()
                    webSocket = null
                    stopHeartbeat()
                    if (_started.get() && !isManualDisconnect.get()) {
                        handler.postDelayed({ connect() }, 2_000)
                    }
                    return
                }

                Log.v(TAG, "💓 Heartbeat أُرسل")

                if (_connected.get() && _started.get()) {
                    handler.postDelayed(this, HEARTBEAT_INTERVAL)
                }
            }
        }
        handler.postDelayed(heartbeatRunnable!!, HEARTBEAT_INTERVAL)
        Log.d(TAG, "💓 بدء Heartbeat")
    }

    private fun stopHeartbeat() {
        heartbeatRunnable?.let { handler.removeCallbacks(it) }
        heartbeatRunnable = null
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun releaseWakeLock() {
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
    }

    private fun handleMessage(raw: String) {
        try {
            val json = JSONObject(raw)
            when (json.optString("type")) {
                "pong", "ping" -> {
                    lastPongReceived.set(System.currentTimeMillis())
                    Log.v(TAG, "💓 Pong/Ping استُلم - الاتصال سليم")
                    // رد على ping بـ pong
                    if (json.optString("type") == "ping") {
                        webSocket?.send(JSONObject().apply {
                            put("type", "pong")
                            put("timestamp", System.currentTimeMillis())
                        }.toString())
                    }
                }
                "connected", "welcome" -> {
                    lastPongReceived.set(System.currentTimeMillis())
                    Log.d(TAG, "🎉 رسالة ترحيب من الخادم")
                }
                "request" -> {
                    val requestId = json.optString("requestId").takeIf { it.isNotBlank() }
                        ?: json.optString("request_id")
                    val method    = json.optString("method", "GET")
                    val path      = json.optString("path", "/")
                    Log.d(TAG, "📥 طلب وارد: $method $path")

                    // تمرير الطلب لـ SmartTunnelManager ليعالجه ApiServer
                    val body = json.optString("body").takeIf { it.isNotBlank() }
                    val headers = mutableMapOf<String, String>()
                    json.optJSONObject("headers")?.let { h ->
                        h.keys().forEach { k -> headers[k] = h.optString(k) }
                    }

                    // معالجة الطلب عبر requestHandler المسجل
                    val response = try {
                        tunnelRequestHandler?.invoke(method, path, headers, body)
                            ?: TunnelResponse(200, """{"status":"ok","gateway":"running"}""")
                    } catch (e: Exception) {
                        TunnelResponse(500, """{"error":"${e.message}"}""")
                    }

                    if (requestId.isNotBlank()) {
                        sendMessage(JSONObject().apply {
                            put("type", "response")
                            put("requestId", requestId)
                            put("request_id", requestId)
                            put("status", response.statusCode)
                            put("status_code", response.statusCode)
                            put("body", response.body)
                            put("headers", JSONObject().apply {
                                put("Content-Type", "application/json")
                                put("Access-Control-Allow-Origin", "*")
                            })
                        }.toString())
                        Log.d(TAG, "📤 Response sent: ${response.statusCode} for $requestId")
                    }
                }
                else -> Log.d(TAG, "❓ رسالة واردة: ${json.optString("type")}")
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
            put("appVersion", "2.0")
            put("timestamp", System.currentTimeMillis())
        }.toString()
        sendMessage(msg)
        Log.d(TAG, "📝 تم إرسال تسجيل الجهاز")
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            false
        }
    }

    private fun registerNetworkCallback() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .build()
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i(TAG, "🌐 الشبكة متاحة")
                    if (!_connected.get() && !isManualDisconnect.get() && _started.get()) {
                        handler.postDelayed({
                            if (!_connected.get() && _started.get() && !isManualDisconnect.get()) {
                                Log.d(TAG, "🔄 إعادة الاتصال بعد توفر الشبكة")
                                reconnectAttempts.set(0)
                                connect()
                            }
                        }, NETWORK_DELAY_MS)
                    }
                }

                override fun onLost(network: Network) {
                    Log.w(TAG, "📵 فُقد الاتصال بالشبكة")
                    _connected.set(false)
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    val validated   = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

                    if (hasInternet && validated && !_connected.get() && !isManualDisconnect.get() && _started.get()) {
                        Log.d(TAG, "🌐 الإنترنت متاح ومُتحقق منه - محاولة الاتصال")
                        handler.postDelayed({
                            if (!_connected.get() && _started.get() && !isManualDisconnect.get()) {
                                reconnectAttempts.set(0)
                                connect()
                            }
                        }, 1_000)
                    }
                }
            }
            cm.registerNetworkCallback(req, networkCallback!!)
            Log.d(TAG, "✅ تم تسجيل مراقب الشبكة")
        } catch (e: Exception) {
            Log.e(TAG, "❌ فشل تسجيل مراقب الشبكة: ${e.message}")
        }
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

// TunnelResponse مشترك بين RelayClient و SmartTunnelManager
// (معرّف في SmartTunnelManager.kt)
