package com.sms.paymentgateway.services

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.PowerManager
import android.util.Log
import com.sms.paymentgateway.utils.security.SecurityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدير الاتصال المباشر - يولد URL مباشر ويدير الاتصالات الواردة
 * بدلاً من استخدام خادم وسيط، يعمل التطبيق كخادم مباشر
 */
@Singleton
class DirectConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityManager: SecurityManager,
    private val webSocketHandler: WebSocketHandler,
    private val networkDetector: NetworkDetector
) {
    
    companion object {
        private const val TAG = "DirectConnectionManager"
        private const val DEFAULT_PORT = 8080
        private const val HEARTBEAT_INTERVAL = 30_000L // 30 ثانية
        private const val CONNECTION_TIMEOUT = 60_000L // 60 ثانية
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // حالة الاتصال
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive
    
    private val _connectionUrl = MutableStateFlow<String?>(null)
    val connectionUrl: StateFlow<String?> = _connectionUrl
    
    private val _connectedClients = MutableStateFlow<Set<String>>(emptySet())
    val connectedClients: StateFlow<Set<String>> = _connectedClients
    
    private val _connectionUrls = MutableStateFlow<List<NetworkDetector.ConnectionUrl>>(emptyList())
    val connectionUrls: StateFlow<List<NetworkDetector.ConnectionUrl>> = _connectionUrls
    
    private val _networkInfo = MutableStateFlow<NetworkDetector.NetworkInfo?>(null)
    val networkInfo: StateFlow<NetworkDetector.NetworkInfo?> = _networkInfo
    
    // إدارة الطاقة
    private var wakeLock: PowerManager.WakeLock? = null
    
    // مراقبة الشبكة
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    // خادم HTTP المدمج
    private var httpServer: fi.iki.elonen.NanoHTTPD? = null
    
    // عملاء WebSocket المتصلين
    private val activeConnections = mutableMapOf<String, WebSocket>()
    
    // مهام الـ Heartbeat
    private var heartbeatJob: Job? = null

    /**
     * بدء الخدمة وتوليد URL مباشر
     */
    fun start() {
        if (_isActive.value) {
            Log.i(TAG, "DirectConnectionManager already active")
            return
        }
        
        Log.i(TAG, "🚀 Starting DirectConnectionManager...")
        
        scope.launch {
            try {
                // كشف معلومات الشبكة الشاملة
                Log.i(TAG, "🔍 كشف معلومات الشبكة...")
                val networkInfo = networkDetector.detectNetworkInfo(DEFAULT_PORT)
                _networkInfo.value = networkInfo
                _connectionUrls.value = networkInfo.suggestedUrls
                
                val localIp = networkInfo.localIp
                Log.i(TAG, "📍 IP المحلي: $localIp")
                Log.i(TAG, "🌐 IP العام: ${networkInfo.publicIp ?: "غير متاح"}")
                Log.i(TAG, "🔗 نوع الشبكة: ${networkInfo.networkType}")
                
                // بدء خادم HTTP
                startHttpServer()
                
                // توليد URLs متعددة
                val directUrl = generateDirectConnectionUrl(localIp, DEFAULT_PORT)
                _connectionUrl.value = directUrl
                securityManager.setDirectConnectionUrl(directUrl)
                
                // عرض جميع الروابط المتاحة
                logAvailableUrls(networkInfo)
                
                // تفعيل مراقبة الشبكة
                setupNetworkMonitoring()
                
                // الحصول على Wake Lock
                acquireWakeLock()
                
                // بدء Heartbeat
                startHeartbeat()
                
                _isActive.value = true
                
                Log.i(TAG, "✅ DirectConnectionManager started successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to start DirectConnectionManager", e)
                stop()
            }
        }
    }

    /**
     * إيقاف الخدمة
     */
    fun stop() {
        Log.i(TAG, "🛑 Stopping DirectConnectionManager...")
        
        scope.launch {
            try {
                // إيقاف Heartbeat
                heartbeatJob?.cancel()
                
                // قطع جميع الاتصالات
                disconnectAllClients()
                
                // إيقاف خادم HTTP
                httpServer?.stop()
                httpServer = null
                
                // إيقاف مراقبة الشبكة
                stopNetworkMonitoring()
                
                // تحرير Wake Lock
                releaseWakeLock()
                
                _isActive.value = false
                _connectionUrl.value = null
                _connectedClients.value = emptySet()
                
                Log.i(TAG, "✅ DirectConnectionManager stopped")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error stopping DirectConnectionManager", e)
            }
        }
    }

    /**
     * إرسال رسالة لجميع العملاء المتصلين
     */
    fun broadcastMessage(message: String) {
        scope.launch {
            val clients = activeConnections.values.toList()
            Log.d(TAG, "📡 Broadcasting message to ${clients.size} clients")
            
            clients.forEach { webSocket ->
                try {
                    webSocket.send(message)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to send message to client", e)
                }
            }
        }
    }

    /**
     * إرسال إشعار دفعة مؤكدة
     */
    fun sendPaymentConfirmation(transactionId: String, smsData: Map<String, Any>) {
        val message = JSONObject().apply {
            put("type", "PAYMENT_CONFIRMED")
            put("transactionId", transactionId)
            put("smsData", JSONObject(smsData))
            put("timestamp", System.currentTimeMillis())
        }.toString()
        
        broadcastMessage(message)
        
        // إرسال عبر WebSocketHandler أيضاً
        webSocketHandler.broadcastPaymentConfirmation(transactionId, smsData)
    }

    /**
     * عرض جميع الروابط المتاحة في السجل
     */
    private fun logAvailableUrls(networkInfo: NetworkDetector.NetworkInfo) {
        Log.i(TAG, "🔗 الروابط المتاحة للاتصال:")
        networkInfo.suggestedUrls.forEach { url ->
            val status = when (url.accessible) {
                NetworkDetector.AccessibilityLevel.LOCAL_ONLY -> "محلي فقط"
                NetworkDetector.AccessibilityLevel.PUBLIC_READY -> "جاهز للعموم ✅"
                NetworkDetector.AccessibilityLevel.REQUIRES_SETUP -> "يحتاج إعداد ⚙️"
                NetworkDetector.AccessibilityLevel.UNKNOWN -> "غير معروف"
            }
            Log.i(TAG, "   ${url.type.uppercase()}: ${url.url}?key=${securityManager.getApiKey()} ($status)")
        }
    }

    /**
     * الحصول على عنوان IP المحلي (محدث)
     */
    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                
                // تجاهل الواجهات غير النشطة أو loopback
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    
                    // البحث عن IPv4 غير محلي
                    if (!address.isLoopbackAddress && 
                        !address.isLinkLocalAddress && 
                        address is java.net.Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP address", e)
        }
        return null
    }

    /**
     * توليد URL الاتصال المباشر
     */
    private fun generateDirectConnectionUrl(ip: String, port: Int): String {
        val apiKey = securityManager.getApiKey()
        return "http://$ip:$port/api/v1/connect?key=$apiKey"
    }

    /**
     * بدء خادم HTTP
     */
    private fun startHttpServer() {
          // استخدام NanoHTTPD مباشرة للاتصال المباشر
          httpServer = object : fi.iki.elonen.NanoHTTPD(DEFAULT_PORT) {
              override fun serve(session: IHTTPSession): Response {
                  return newFixedLengthResponse(
                      Response.Status.OK,
                      "application/json",
                      handleDirectConnection(session)
                  )
              }
          }.also { it.start() }
      }


    /**
     * معالجة طلبات الاتصال المباشر
     */
    private fun handleDirectConnection(session: fi.iki.elonen.NanoHTTPD.IHTTPSession): String {
        // معالجة طلب الاتصال وإرجاع معلومات الاتصال
        return JSONObject().apply {
            put("status", "connected")
            put("serverTime", System.currentTimeMillis())
            put("heartbeatInterval", HEARTBEAT_INTERVAL)
            put("apiVersion", "1.0")
        }.toString()
    }

    /**
     * إعداد مراقبة الشبكة
     */
    private fun setupNetworkMonitoring() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "🌐 Network available")
                // إعادة تشغيل الخدمة إذا لزم الأمر
                if (!_isActive.value) {
                    start()
                }
            }
            
            override fun onLost(network: Network) {
                Log.w(TAG, "🌐 Network lost")
                // يمكن الاحتفاظ بالخدمة نشطة للاتصالات المحلية
            }
        }
        
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
            
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
    }

    /**
     * إيقاف مراقبة الشبكة
     */
    private fun stopNetworkMonitoring() {
        networkCallback?.let { callback ->
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.unregisterNetworkCallback(callback)
        }
        networkCallback = null
    }

    /**
     * الحصول على Wake Lock
     */
    private fun acquireWakeLock() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PaymentGateway:DirectConnection"
        ).apply {
            acquire(10 * 60 * 1000L) // 10 دقائق
        }
        Log.d(TAG, "🔋 Wake lock acquired")
    }

    /**
     * تحرير Wake Lock
     */
    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
                Log.d(TAG, "🔋 Wake lock released")
            }
        }
        wakeLock = null
    }

    /**
     * بدء Heartbeat
     */
    private fun startHeartbeat() {
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL)
                
                if (_isActive.value) {
                    sendHeartbeat()
                }
            }
        }
    }

    /**
     * إرسال Heartbeat لجميع العملاء
     */
    private fun sendHeartbeat() {
        val heartbeatMessage = JSONObject().apply {
            put("type", "HEARTBEAT")
            put("timestamp", System.currentTimeMillis())
            put("status", "alive")
        }.toString()
        
        broadcastMessage(heartbeatMessage)
    }

    /**
     * قطع جميع الاتصالات
     */
    private fun disconnectAllClients() {
        activeConnections.values.forEach { webSocket ->
            try {
                webSocket.close(1000, "Server shutting down")
            } catch (e: Exception) {
                Log.e(TAG, "Error closing WebSocket", e)
            }
        }
        activeConnections.clear()
        _connectedClients.value = emptySet()
    }

    /**
     * إضافة عميل جديد
     */
    fun addClient(clientId: String, webSocket: WebSocket) {
        activeConnections[clientId] = webSocket
        _connectedClients.value = activeConnections.keys.toSet()
        Log.i(TAG, "➕ Client connected: $clientId (Total: ${activeConnections.size})")
    }

    /**
     * إزالة عميل
     */
    fun removeClient(clientId: String) {
        activeConnections.remove(clientId)
        _connectedClients.value = activeConnections.keys.toSet()
        Log.i(TAG, "➖ Client disconnected: $clientId (Total: ${activeConnections.size})")
    }

    /**
     * الحصول على معلومات الاتصال المحدثة
     */
    fun getConnectionInfo(): Map<String, Any> {
        val networkInfo = _networkInfo.value
        return mapOf(
            "isActive" to _isActive.value,
            "connectionUrl" to (_connectionUrl.value ?: ""),
            "connectedClients" to _connectedClients.value.size,
            "localIp" to (networkInfo?.localIp ?: "unknown"),
            "publicIp" to (networkInfo?.publicIp ?: "غير متاح"),
            "networkType" to (networkInfo?.networkType?.name ?: "UNKNOWN"),
            "isPublicAccessible" to (networkInfo?.isPublicAccessible ?: false),
            "port" to DEFAULT_PORT,
            "apiKey" to securityManager.getApiKey(),
            "availableUrls" to (_connectionUrls.value.map { url ->
                mapOf(
                    "type" to url.type,
                    "url" to "${url.url}?key=${securityManager.getApiKey()}",
                    "description" to url.description,
                    "accessible" to url.accessible.name
                )
            })
        )
    }

    /**
     * الحصول على أفضل رابط للاستخدام
     */
    fun getBestConnectionUrl(): String? {
        val urls = _connectionUrls.value
        
        // أولوية الاختيار: عام جاهز > محلي > يحتاج إعداد
        return urls.find { it.accessible == NetworkDetector.AccessibilityLevel.PUBLIC_READY }?.let {
            "${it.url}?key=${securityManager.getApiKey()}"
        } ?: urls.find { it.accessible == NetworkDetector.AccessibilityLevel.LOCAL_ONLY }?.let {
            "${it.url}?key=${securityManager.getApiKey()}"
        } ?: urls.firstOrNull()?.let {
            "${it.url}?key=${securityManager.getApiKey()}"
        }
    }

    /**
     * فحص إمكانية الوصول الخارجي
     */
    suspend fun checkExternalAccess(): Boolean {
        val networkInfo = _networkInfo.value ?: return false
        return networkInfo.isPublicAccessible
    }
}