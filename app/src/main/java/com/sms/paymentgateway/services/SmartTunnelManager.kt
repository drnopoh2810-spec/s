package com.sms.paymentgateway.services

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SmartTunnelManager - يولد رابط https عام حقيقي بدون أي إعداد خارجي
 *
 * الفكرة:
 * التطبيق يتصل بـ Relay Server عبر WebSocket دائم.
 * الـ Relay Server يستقبل طلبات HTTP من الإنترنت ويمررها للتطبيق عبر WebSocket.
 * التطبيق يعالج الطلب ويرد عبر نفس الـ WebSocket.
 * النتيجة: رابط https://DEVICE_ID.relay.app يعمل كأنه موقع حقيقي.
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │  أي موقع/عميل في العالم                                     │
 * │  POST https://abc123.relay.app/api/v1/transactions          │
 * └──────────────────────┬──────────────────────────────────────┘
 *                        │ HTTPS
 *                        ▼
 * ┌─────────────────────────────────────────────────────────────┐
 * │  Relay Server (Huggingface / مخصص)                          │
 * │  يستقبل الطلب ويحوله لـ WebSocket message                   │
 * └──────────────────────┬──────────────────────────────────────┘
 *                        │ WebSocket (مشفر)
 *                        ▼
 * ┌─────────────────────────────────────────────────────────────┐
 * │  SmartTunnelManager (داخل التطبيق)                          │
 * │  يعالج الطلب عبر ApiServer ويرد                             │
 * └─────────────────────────────────────────────────────────────┘
 */
@Singleton
class SmartTunnelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityManager: com.sms.paymentgateway.utils.security.SecurityManager
) {
    companion object {
        private const val TAG = "SmartTunnel"

        // Relay servers مرتبة حسب الأولوية
        private val RELAY_SERVERS = listOf(
            RelayServer(
                name = "Primary Relay",
                wsUrl  = "wss://nopoh22-sms-relay-server.hf.space/device",
                httpBase = "https://nopoh22-sms-relay-server.hf.space"
            )
        )

        private const val HEARTBEAT_MS = 25_000L
        private const val RECONNECT_BASE_MS = 2_000L
        private const val MAX_RECONNECT_MS = 60_000L
    }

    // ─── State ───────────────────────────────────────────────────────────────

    data class TunnelState(
        val status: TunnelStatus = TunnelStatus.DISCONNECTED,
        val publicUrl: String? = null,
        val deviceId: String? = null,
        val relayServer: String? = null,
        val requestsHandled: Int = 0,
        val error: String? = null
    )

    enum class TunnelStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,      // متصل لكن لم يُسجَّل بعد
        ACTIVE,         // مسجل ورابطه جاهز
        RECONNECTING,
        ERROR
    }

    private val _state = MutableStateFlow(TunnelState())
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    // ─── Internal ────────────────────────────────────────────────────────────

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isRunning = AtomicBoolean(false)
    private val isManualStop = AtomicBoolean(false)

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var currentServerIndex = 0
    @Volatile private var reconnectAttempts = 0
    @Volatile private var requestsHandled = 0

    // callback يُستدعى عند وصول طلب HTTP - يُعالجه ApiServer
    var requestHandler: ((method: String, path: String, headers: Map<String, String>, body: String?) -> TunnelResponse)? = null

    private val httpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // ─── Public API ──────────────────────────────────────────────────────────

    fun start() {
        if (isRunning.getAndSet(true)) return
        isManualStop.set(false)
        reconnectAttempts = 0
        Log.i(TAG, "🚀 Starting SmartTunnel...")
        connect()
    }

    fun stop() {
        Log.i(TAG, "⏹️ Stopping SmartTunnel...")
        isManualStop.set(true)
        isRunning.set(false)
        webSocket?.close(1000, "Manual stop")
        webSocket = null
        _state.value = TunnelState(status = TunnelStatus.DISCONNECTED)
    }

    fun isActive(): Boolean = _state.value.status == TunnelStatus.ACTIVE

    fun getPublicUrl(): String? = _state.value.publicUrl

    // ─── Connection ──────────────────────────────────────────────────────────

    private fun connect() {
        if (isManualStop.get()) return

        val server = RELAY_SERVERS[currentServerIndex % RELAY_SERVERS.size]
        val deviceId = getDeviceId()

        Log.i(TAG, "🔌 Connecting to ${server.name}...")
        _state.value = _state.value.copy(
            status = TunnelStatus.CONNECTING,
            relayServer = server.name,
            error = null
        )

        val request = Request.Builder()
            .url(server.wsUrl)
            .addHeader("Authorization", "Bearer ${securityManager.getApiKey()}")
            .addHeader("X-Device-Id", deviceId)
            .addHeader("X-Tunnel-Version", "2.0")
            .addHeader("User-Agent", "SmartTunnel-Android/2.0")
            .build()

        webSocket = httpClient.newWebSocket(request, createListener(server, deviceId))
    }

    private fun createListener(server: RelayServer, deviceId: String) = object : WebSocketListener() {

        override fun onOpen(ws: WebSocket, response: Response) {
            Log.i(TAG, "✅ WebSocket connected to ${server.name}")
            reconnectAttempts = 0
            _state.value = _state.value.copy(status = TunnelStatus.CONNECTED)

            // تسجيل الجهاز وطلب رابط عام
            registerDevice(ws, deviceId, server)
            startHeartbeat(ws)
        }

        override fun onMessage(ws: WebSocket, text: String) {
            handleServerMessage(ws, text, server, deviceId)
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "❌ WebSocket closed: $code - $reason")
            onDisconnected()
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "💥 WebSocket failure: ${t.message}")
            onDisconnected()
        }
    }

    // ─── Message Handling ────────────────────────────────────────────────────

    private fun handleServerMessage(ws: WebSocket, raw: String, server: RelayServer, deviceId: String) {
        try {
            val json = JSONObject(raw)
            when (json.optString("type")) {

                // الخادم أعطانا رابط عام
                "registered", "tunnel_ready", "connected" -> {
                    val assignedId = json.optString("deviceId", deviceId)
                    val publicUrl = json.optString("publicUrl")
                        .takeIf { it.isNotBlank() }
                        ?: buildPublicUrl(server, assignedId)

                    _state.value = _state.value.copy(
                        status = TunnelStatus.ACTIVE,
                        publicUrl = publicUrl,
                        deviceId = assignedId
                    )
                    Log.i(TAG, "🌐 Tunnel ACTIVE! Public URL: $publicUrl")
                }

                // طلب HTTP وارد من الإنترنت عبر الـ Relay
                "http_request", "request" -> {
                    handleIncomingRequest(ws, json)
                }

                // pong من الخادم
                "pong", "ping" -> {
                    if (json.optString("type") == "ping") {
                        ws.send(JSONObject().apply {
                            put("type", "pong")
                            put("timestamp", System.currentTimeMillis())
                        }.toString())
                    }
                }

                "error" -> {
                    val msg = json.optString("message", "Unknown error")
                    Log.e(TAG, "Server error: $msg")
                    _state.value = _state.value.copy(error = msg)
                }

                else -> Log.v(TAG, "Unknown message type: ${json.optString("type")}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message: ${e.message}")
        }
    }

    /**
     * معالجة طلب HTTP وارد من الإنترنت
     * الـ Relay يحول HTTP → WebSocket message → نعالجه ونرد
     */
    private fun handleIncomingRequest(ws: WebSocket, json: JSONObject) {
        scope.launch {
            val requestId = json.optString("requestId")
                .takeIf { it.isNotBlank() } ?: json.optString("request_id")
            val method = json.optString("method", "GET").uppercase()
            val path = json.optString("path", "/")
            val body = json.optString("body").takeIf { it.isNotBlank() }

            // استخراج headers
            val headers = mutableMapOf<String, String>()
            json.optJSONObject("headers")?.let { h ->
                h.keys().forEach { key -> headers[key] = h.optString(key) }
            }

            Log.d(TAG, "📥 Incoming: $method $path (requestId=$requestId)")

            // معالجة الطلب عبر ApiServer
            val response = try {
                requestHandler?.invoke(method, path, headers, body)
                    ?: TunnelResponse(200, """{"status":"ok","message":"Gateway running"}""")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling request", e)
                TunnelResponse(500, """{"error":"Internal server error"}""")
            }

            requestsHandled++
            _state.value = _state.value.copy(requestsHandled = requestsHandled)

            // إرسال الرد للـ Relay ليعيده للعميل
            if (requestId.isNotBlank()) {
                val reply = JSONObject().apply {
                    put("type", "http_response")
                    put("requestId", requestId)
                    put("request_id", requestId)
                    put("status", response.statusCode)
                    put("status_code", response.statusCode)
                    put("body", response.body)
                    put("headers", JSONObject().apply {
                        put("Content-Type", "application/json")
                        put("Access-Control-Allow-Origin", "*")
                        put("X-Powered-By", "SmartTunnel/2.0")
                    })
                }
                ws.send(reply.toString())
                Log.d(TAG, "📤 Response sent: ${response.statusCode} for $requestId")
            }
        }
    }

    // ─── Registration ────────────────────────────────────────────────────────

    private fun registerDevice(ws: WebSocket, deviceId: String, server: RelayServer) {
        // deviceToken = SHA-256 من API Key → يُستخدم للتحقق لاحقاً
        val deviceToken = sha256(securityManager.getApiKey())

        val msg = JSONObject().apply {
            put("type", "register")
            put("deviceId", deviceId)
            put("deviceToken", deviceToken)
            put("deviceType", "android_gateway")
            put("version", "2.0")
            put("timestamp", System.currentTimeMillis())
        }
        ws.send(msg.toString())
        Log.d(TAG, "📝 Registration sent for device: ${deviceId.take(16)}...")
    }

    private fun sha256(input: String): String {
        val bytes = java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ─── Heartbeat ───────────────────────────────────────────────────────────

    private fun startHeartbeat(ws: WebSocket) {
        scope.launch {
            while (isActive && webSocket == ws) {
                delay(HEARTBEAT_MS)
                if (webSocket == ws) {
                    ws.send(JSONObject().apply {
                        put("type", "ping")
                        put("timestamp", System.currentTimeMillis())
                    }.toString())
                }
            }
        }
    }

    // ─── Reconnect ───────────────────────────────────────────────────────────

    private fun onDisconnected() {
        webSocket = null
        if (_state.value.status == TunnelStatus.ACTIVE) {
            _state.value = _state.value.copy(
                status = TunnelStatus.RECONNECTING,
                publicUrl = null
            )
        } else {
            _state.value = _state.value.copy(status = TunnelStatus.RECONNECTING)
        }

        if (!isManualStop.get() && isRunning.get()) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        val delay = minOf(
            RECONNECT_BASE_MS * (1L shl reconnectAttempts.coerceAtMost(5)),
            MAX_RECONNECT_MS
        )
        reconnectAttempts++
        // جرب الخادم التالي بعد 3 محاولات
        if (reconnectAttempts % 3 == 0) currentServerIndex++

        Log.d(TAG, "⏰ Reconnecting in ${delay}ms (attempt $reconnectAttempts)")
        scope.launch {
            delay(delay)
            if (!isManualStop.get() && isRunning.get()) connect()
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun getDeviceId(): String {
        // نستخدم نفس الـ deviceId المحفوظ في SecurityManager لضمان التطابق
        return securityManager.buildDeviceId()
    }

    private fun buildPublicUrl(server: RelayServer, deviceId: String): String {
        // fallback إذا لم يرسل الـ Relay الرابط - يبني رابط كامل
        return "${server.httpBase}/gateway/$deviceId"
    }

    /** الرابط الكامل للـ API - دائماً absolute URL */
    fun getFullApiUrl(): String? {
        val state = _state.value
        val url = state.publicUrl ?: return null
        // إذا كان الرابط نسبياً، أضف الـ base
        return if (url.startsWith("http")) {
            url  // رابط كامل من الـ Relay
        } else {
            "${RELAY_SERVERS[0].httpBase}$url"  // أضف الـ base
        }
    }

    fun cleanup() {
        stop()
        scope.cancel()
    }
}

// ─── Data Classes ────────────────────────────────────────────────────────────

data class RelayServer(
    val name: String,
    val wsUrl: String,
    val httpBase: String
)

data class TunnelResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String> = mapOf("Content-Type" to "application/json")
)
