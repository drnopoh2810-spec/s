package com.sms.paymentgateway.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.PowerManager
import android.util.Log
import com.sms.paymentgateway.BuildConfig
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RelayClient(private val context: Context) {
    private val TAG = "RelayClient"
    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var reconnectAttempts = 0
    private val MAX_RECONNECT_ATTEMPTS = 50 // زيادة عدد المحاولات
    private var isManualDisconnect = false
    private var heartbeatRunnable: Runnable? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isStarted = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var reconnectHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // استخدام التوكن من BuildConfig
    private val SERVER_URL = "wss://nopoh22-sms-relay-server.hf.space/device"
    private val AUTH_TOKEN = BuildConfig.RELAY_API_KEY

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
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, 
            "RelayClient::WakeLock"
        )
        registerNetworkCallback()
    }

    /**
     * بدء خدمة RelayClient
     */
    fun start() {
        Log.d(TAG, "🚀 بدء RelayClient...")
        isStarted = true
        isManualDisconnect = false
        reconnectAttempts = 0
        connect()
    }

    /**
     * إيقاف خدمة RelayClient
     */
    fun stop() {
        Log.d(TAG, "⏹️ إيقاف RelayClient...")
        isStarted = false
        disconnect()
        unregisterNetworkCallback()
    }

    fun connect() {
        if (!isStarted) {
            Log.w(TAG, "RelayClient غير مبدء، تجاهل محاولة الاتصال")
            return
        }
        
        isManualDisconnect = false
        Log.d(TAG, "محاولة الاتصال بالخادم... (المحاولة ${reconnectAttempts + 1})")
        
        // التحقق من حالة الشبكة أولاً
        if (!isNetworkAvailable()) {
            Log.w(TAG, "لا توجد شبكة متاحة، سيتم إعادة المحاولة لاحقاً")
            scheduleReconnect()
            return
        }
        
        val request = Request.Builder()
            .url(SERVER_URL)
            .addHeader("Authorization", "Bearer $AUTH_TOKEN")
            .addHeader("X-Api-Key", AUTH_TOKEN)
            .addHeader("User-Agent", "SMS-Gateway-Android/1.0")
            .build()
            
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                isConnected = true
                reconnectAttempts = 0
                Log.d(TAG, "✅ WebSocket متصل بنجاح")
                
                // الحصول على WakeLock لمنع النوم
                wakeLock?.let { 
                    if (!it.isHeld) {
                        it.acquire(10 * 60 * 1000L) // 10 دقائق
                        Log.d(TAG, "🔋 WakeLock مُفعل")
                    }
                }
                
                startManualHeartbeat()
                
                // إرسال رسالة تسجيل الدخول
                sendRegistrationMessage()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                Log.d(TAG, "📨 رسالة واردة: $text")
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosing(webSocket, code, reason)
                Log.w(TAG, "⚠️ WebSocket يتم إغلاقه: $code - $reason")
                isConnected = false
                releaseWakeLock()
                if (isStarted && !isManualDisconnect) {
                    scheduleReconnect()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                Log.w(TAG, "❌ WebSocket مغلق: $code - $reason")
                isConnected = false
                releaseWakeLock()
                if (isStarted && !isManualDisconnect) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                Log.e(TAG, "💥 فشل الاتصال: ${t.message}", t)
                isConnected = false
                releaseWakeLock()
                if (isStarted && !isManualDisconnect) {
                    scheduleReconnect()
                }
            }
        })
    }

    fun disconnect() {
        isManualDisconnect = true
        stopManualHeartbeat()
        
        // إلغاء أي محاولات إعادة اتصال مجدولة
        reconnectHandler.removeCallbacksAndMessages(null)
        
        webSocket?.close(1000, "Manual disconnect")
        webSocket = null
        isConnected = false
        releaseWakeLock()
        Log.d(TAG, "🔌 تم قطع الاتصال يدويًا")
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
        if (isManualDisconnect || !isStarted) return
        
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "🚫 وصلت محاولات إعادة الاتصال إلى الحد الأقصى ($MAX_RECONNECT_ATTEMPTS)، سيتم إعادة تعيين العداد والمحاولة مرة أخرى")
            reconnectAttempts = 0 // إعادة تعيين العداد للمحاولة مرة أخرى
        }
        
        // تأخير متزايد: 2^n ثانية، بحد أقصى 5 دقائق
        val baseDelay = (1 shl reconnectAttempts.coerceAtMost(8)).coerceAtMost(300).toLong()
        val jitter = (Math.random() * 10).toLong() // إضافة عشوائية لتجنب thundering herd
        val delay = baseDelay + jitter
        
        Log.d(TAG, "⏰ سيتم إعادة الاتصال بعد $delay ثانية (المحاولة ${reconnectAttempts + 1}/$MAX_RECONNECT_ATTEMPTS)")
        
        reconnectHandler.postDelayed({
            if (isStarted && !isManualDisconnect) {
                connect()
            }
        }, delay * 1000)
        
        reconnectAttempts++
    }

    private fun startManualHeartbeat() {
        stopManualHeartbeat()
        heartbeatRunnable = object : Runnable {
            override fun run() {
                if (isConnected && webSocket != null && isStarted) {
                    val heartbeat = JSONObject().apply {
                        put("type", "ping")
                        put("timestamp", System.currentTimeMillis())
                    }.toString()
                    
                    val sent = webSocket?.send(heartbeat) ?: false
                    if (sent) {
                        Log.v(TAG, "💓 Heartbeat sent")
                    } else {
                        Log.w(TAG, "⚠️ فشل إرسال Heartbeat، قد يكون الاتصال مقطوع")
                        isConnected = false
                        if (isStarted && !isManualDisconnect) {
                            scheduleReconnect()
                        }
                        return
                    }
                }
                
                if (isConnected && isStarted) {
                    reconnectHandler.postDelayed(this, 20000) // كل 20 ثانية
                }
            }
        }
        heartbeatRunnable?.let { reconnectHandler.postDelayed(it, 20000) }
    }

    private fun stopManualHeartbeat() {
        heartbeatRunnable?.let { reconnectHandler.removeCallbacks(it) }
        heartbeatRunnable = null
    }

    private fun releaseWakeLock() {
        wakeLock?.let { 
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "🔋 WakeLock مُحرر")
            }
        }
    }

    private fun handleIncomingMessage(message: String) {
        try {
            val json = JSONObject(message)
            when (json.optString("type")) {
                "pong" -> {
                    Log.v(TAG, "💓 Pong received - الاتصال سليم")
                }
                "request" -> {
                    val requestId = json.getString("requestId")
                    val method = json.getString("method")
                    val path = json.getString("path")
                    val body = json.optString("body")
                    Log.d(TAG, "📥 طلب وارد: $method $path")
                    
                    // هنا يمكنك إضافة منطق معالجة الطلب (مثل إرسال SMS)
                    val responseBody = """{"status":"ok","message":"Request processed"}"""
                    sendMessage(JSONObject().apply {
                        put("type", "response")
                        put("requestId", requestId)
                        put("status", 200)
                        put("body", responseBody)
                    }.toString())
                }
                "welcome" -> {
                    Log.d(TAG, "🎉 رسالة ترحيب من الخادم")
                }
                else -> Log.d(TAG, "❓ رسالة غير معروفة: $json")
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 خطأ في معالجة الرسالة: ${e.message}", e)
        }
    }

    /**
     * إرسال رسالة تسجيل الجهاز
     */
    private fun sendRegistrationMessage() {
        val registrationMessage = JSONObject().apply {
            put("type", "register")
            put("deviceId", android.provider.Settings.Secure.getString(
                context.contentResolver, 
                android.provider.Settings.Secure.ANDROID_ID
            ))
            put("deviceType", "android")
            put("appVersion", "1.0")
            put("timestamp", System.currentTimeMillis())
        }.toString()
        
        sendMessage(registrationMessage)
        Log.d(TAG, "📝 تم إرسال رسالة تسجيل الجهاز")
    }

    /**
     * التحقق من توفر الشبكة
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun registerNetworkCallback() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
            
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.d(TAG, "🌐 الشبكة متاحة، إعادة محاولة الاتصال إن لزم")
                if (!isConnected && !isManualDisconnect && isStarted) {
                    // تأخير قصير للتأكد من استقرار الاتصال
                    reconnectHandler.postDelayed({
                        if (!isConnected && isStarted && !isManualDisconnect) {
                            reconnectAttempts = 0 // إعادة تعيين العداد عند توفر شبكة جديدة
                            connect()
                        }
                    }, 2000)
                }
            }
            
            override fun onLost(network: Network) {
                super.onLost(network)
                Log.w(TAG, "📵 فقدان الشبكة")
                isConnected = false
            }
            
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                Log.v(TAG, "🔄 تغيير في قدرات الشبكة - Internet: $hasInternet")
            }
        }
        
        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
            Log.d(TAG, "📡 تم تسجيل مراقب الشبكة")
        } catch (e: Exception) {
            Log.e(TAG, "❌ فشل تسجيل مراقب الشبكة: ${e.message}")
        }
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let { callback ->
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(callback)
                Log.d(TAG, "📡 تم إلغاء تسجيل مراقب الشبكة")
            } catch (e: Exception) {
                Log.e(TAG, "❌ فشل إلغاء تسجيل مراقب الشبكة: ${e.message}")
            }
        }
        networkCallback = null
    }

    /**
     * الحصول على حالة الاتصال
     */
    fun isConnected(): Boolean = isConnected && webSocket != null

    /**
     * الحصول على حالة الخدمة
     */
    fun isStarted(): Boolean = isStarted
}
