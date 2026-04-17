package com.sms.paymentgateway.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.gson.Gson
import com.sms.paymentgateway.data.dao.PendingTransactionDao
import com.sms.paymentgateway.data.dao.SmsLogDao
import com.sms.paymentgateway.data.entities.PendingTransaction
import com.sms.paymentgateway.utils.security.SecurityManager
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.IOException
import java.net.NetworkInterface
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiServer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pendingTransactionDao: PendingTransactionDao,
    private val smsLogDao: SmsLogDao,
    private val webhookLogDao: com.sms.paymentgateway.data.dao.WebhookLogDao,
    private val securityManager: SecurityManager,
    private val encryptionManager: com.sms.paymentgateway.utils.security.EncryptionManager,
    private val bulkTransactionProcessor: BulkTransactionProcessor,
    private val analyticsRepository: com.sms.paymentgateway.data.repository.AnalyticsRepository,
    private val deviceDao: com.sms.paymentgateway.data.dao.DeviceDao,
    private val deviceLoadBalancer: DeviceLoadBalancer,
    private val smsTemplateDao: com.sms.paymentgateway.data.dao.SmsTemplateDao,
    private val templateProcessor: TemplateProcessor,
    private val rateLimiter: com.sms.paymentgateway.utils.security.RateLimiter,
    private val webSocketHandler: WebSocketHandler,
    private val smartTunnelManager: SmartTunnelManager,
    private val gson: Gson
) : NanoWSD(8080) {

    companion object {
        private const val TAG = "ApiServer"
    }

    // معالج إضافي للاتصال المباشر
    private var directConnectionHandler: ((Any) -> String)? = null

    fun addDirectConnectionHandler(handler: (Any) -> String) {
        this.directConnectionHandler = handler
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        val clientIp = session.headers["http-client-ip"] ?: session.headers["x-forwarded-for"] ?: "unknown"

        // IP Whitelist check - يدعم CIDR وتواريخ الانتهاء
        if (!securityManager.isIpAllowedEnhanced(clientIp)) {
            Timber.w("Request from non-whitelisted IP: $clientIp")
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                "application/json",
                gson.toJson(mapOf("error" to "IP not allowed"))
            )
        }

        // Rate limiting check
        if (!rateLimiter.isAllowed(clientIp)) {
            return newFixedLengthResponse(
                Response.Status.TOO_MANY_REQUESTS,
                "application/json",
                gson.toJson(mapOf("error" to "Rate limit exceeded"))
            )
        }

        // Security check - يدعم grace period بعد تدوير المفتاح
        val apiKey = session.headers["authorization"]?.removePrefix("Bearer ")
        if (!securityManager.validateApiKeyWithGrace(apiKey)) {
            return newFixedLengthResponse(
                Response.Status.UNAUTHORIZED,
                "application/json",
                gson.toJson(mapOf("error" to "Unauthorized"))
            )
        }

        return when {
            uri == "/api/v1/health" && method == Method.GET -> handleHealth()
            uri == "/api/v1/connect" && method == Method.GET -> handleDirectConnect(session)
            uri == "/api/v1/connection-info" && method == Method.GET -> handleConnectionInfo()
            uri == "/api/v1/transactions" && method == Method.POST -> handleCreateTransaction(session)
            uri.startsWith("/api/v1/transactions/") && method == Method.GET -> handleGetTransaction(uri)
            uri == "/api/v1/transactions" && method == Method.GET -> handleListTransactions()
            uri == "/api/v1/transactions/expired" && method == Method.GET -> handleGetExpiredTransactions(session)
            uri == "/api/v1/sms/logs" && method == Method.GET -> handleGetSmsLogs()
            uri == "/api/v1/webhooks/logs" && method == Method.GET -> handleGetWebhookLogs(session)
            uri == "/api/v1/webhooks/stats" && method == Method.GET -> handleGetWebhookStats(session)
            uri == "/api/v1/encryption/status" && method == Method.GET -> handleGetEncryptionStatus()
            uri == "/api/v1/encryption/enable" && method == Method.POST -> handleEnableEncryption(session)
            uri == "/api/v1/encryption/disable" && method == Method.POST -> handleDisableEncryption()
            uri == "/api/v1/encryption/rotate-key" && method == Method.POST -> handleRotateEncryptionKey()
            uri == "/api/v1/transactions/bulk" && method == Method.POST -> handleBulkTransactions(session)
            uri == "/api/v1/transactions/bulk/validate" && method == Method.POST -> handleValidateBulkCsv(session)
            uri == "/api/v1/analytics" && method == Method.GET -> handleGetAnalytics(session)
            uri == "/api/v1/devices" && method == Method.GET -> handleListDevices()
            uri == "/api/v1/devices" && method == Method.POST -> handleRegisterDevice(session)
            uri.startsWith("/api/v1/devices/") && method == Method.DELETE -> handleDeleteDevice(uri)
            uri == "/api/v1/devices/status" && method == Method.GET -> handleDevicesStatus()
            uri.startsWith("/api/v1/devices/") && method == Method.PUT -> handleUpdateDevice(uri, session)
            uri == "/api/v1/templates" && method == Method.GET -> handleListTemplates()
            uri == "/api/v1/templates" && method == Method.POST -> handleCreateTemplate(session)
            uri.startsWith("/api/v1/templates/") && uri.endsWith("/preview") && method == Method.GET -> handlePreviewTemplate(uri)
            uri.startsWith("/api/v1/templates/") && method == Method.PUT -> handleUpdateTemplate(uri, session)
            uri.startsWith("/api/v1/templates/") && method == Method.DELETE -> handleDeleteTemplate(uri)
            // Security endpoints
            uri == "/api/v1/security/rotate-key" && method == Method.POST -> handleRotateApiKey()
            uri == "/api/v1/security/rate-limit/stats" && method == Method.GET -> handleRateLimitStats()
            uri == "/api/v1/security/ip-whitelist" && method == Method.GET -> handleGetIpWhitelist()
            uri == "/api/v1/security/api-key/info" && method == Method.GET -> handleGetApiKeyInfo()
            uri == "/api/v1/security/api-key/set" && method == Method.POST -> handleSetManualApiKey(session)
            uri == "/api/v1/security/api-key/reset" && method == Method.POST -> handleResetApiKey()
            // Relay Server config
            uri == "/api/v1/relay/config" && method == Method.GET -> handleGetRelayConfig()
            uri == "/api/v1/relay/config" && method == Method.POST -> handleSetRelayConfig(session)
            uri == "/api/v1/relay/status" && method == Method.GET -> handleGetRelayStatus()
            uri == "/api/v1/security/ip-whitelist" && method == Method.POST -> handleAddIpWhitelist(session)
            uri.startsWith("/api/v1/security/ip-whitelist/") && method == Method.DELETE -> handleRemoveIpWhitelist(uri)
            // Transaction history with filters
            uri == "/api/v1/transactions/history" && method == Method.GET -> handleTransactionHistory(session)
            // Transaction history with filters
            uri == "/api/v1/transactions/history" && method == Method.GET -> handleTransactionHistory(session)
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "application/json",
                gson.toJson(mapOf("error" to "Not found"))
            )
        }
    }

    private fun handleHealth(): Response {
        val health = mapOf(
            "status" to "ok",
            "timestamp" to Date().time,
            "service" to "SMS Payment Gateway",
            "version" to "2.0.0",
            "connectionType" to "direct",
            "localIp" to getLocalIpAddress()
        )
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            gson.toJson(health)
        )
    }

    /**
     * معالج الاتصال المباشر - يوفر معلومات الاتصال للمواقع الخارجية
     */
    private fun handleDirectConnect(session: IHTTPSession): Response {
        return try {
            val connectionInfo = mapOf(
                "status" to "connected",
                "serverTime" to System.currentTimeMillis(),
                "apiVersion" to "2.0.0",
                "connectionType" to "direct",
                "websocketUrl" to "ws://${getLocalIpAddress()}:8080/websocket",
                "httpUrl" to "http://${getLocalIpAddress()}:8080/api/v1",
                "heartbeatInterval" to 30000,
                "features" to listOf(
                    "real-time-notifications",
                    "transaction-tracking",
                    "sms-processing",
                    "webhook-callbacks"
                )
            )

            // استدعاء المعالج الإضافي إذا كان موجوداً
            directConnectionHandler?.invoke(session)

            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                gson.toJson(connectionInfo)
            )
        } catch (e: Exception) {
            Timber.e(e, "Error handling direct connection")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                gson.toJson(mapOf("error" to "Connection failed"))
            )
        }
    }

    /**
     * معلومات الاتصال الحالية
     */
    private fun handleConnectionInfo(): Response {
        val localIp = getLocalIpAddress()
        val connectionInfo = mapOf(
            "localIp" to localIp,
            "port" to 8080,
            "directUrl" to "http://$localIp:8080/api/v1/connect?key=${securityManager.getApiKey()}",
            "websocketUrl" to "ws://$localIp:8080/websocket",
            "apiKey" to securityManager.getApiKey(),
            "isActive" to isAlive,
            "timestamp" to System.currentTimeMillis()
        )

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            gson.toJson(connectionInfo)
        )
    }

    /**
     * الحصول على عنوان IP المحلي
     */
    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    
                    if (!address.isLoopbackAddress && 
                        !address.isLinkLocalAddress && 
                        address is java.net.Inet4Address) {
                        return address.hostAddress ?: "localhost"
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting local IP address")
        }
        return "localhost"
    }

    /**
     * معالج WebSocket للاتصالات المباشرة
     */
    override fun openWebSocket(handshake: IHTTPSession): WebSocket? {
        return object : WebSocket(handshake) {
            override fun onOpen() {
                Timber.i("$TAG: WebSocket connection opened")
                webSocketHandler.addClient(this)
            }

            override fun onClose(code: WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean) {
                Timber.i("$TAG: WebSocket connection closed: $reason")
                webSocketHandler.removeClient(this)
            }

            override fun onMessage(message: WebSocketFrame) {
                try {
                    val textMessage = message.textPayload
                    Timber.d("$TAG: Received WebSocket message: $textMessage")
                    
                    // معالجة الرسائل الواردة
                    handleWebSocketMessage(textMessage, this)
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Error handling WebSocket message")
                }
            }

            override fun onPong(pong: WebSocketFrame) {
                Timber.d("$TAG: Received WebSocket pong")
            }

            override fun onException(exception: IOException) {
                Timber.e(exception, "$TAG: WebSocket exception")
                webSocketHandler.removeClient(this)
            }
        }
    }

    /**
     * معالجة رسائل WebSocket
     */
    private fun handleWebSocketMessage(message: String, webSocket: WebSocket) {
        try {
            val json = gson.fromJson(message, Map::class.java)
            val type = json["type"] as? String

            when (type) {
                "ping" -> {
                    // إرسال pong
                    val pongResponse = gson.toJson(mapOf(
                        "type" to "pong",
                        "timestamp" to System.currentTimeMillis()
                    ))
                    webSocket.send(pongResponse)
                }
                "subscribe" -> {
                    // اشتراك في الإشعارات
                    val subscribeResponse = gson.toJson(mapOf(
                        "type" to "subscribed",
                        "status" to "success",
                        "timestamp" to System.currentTimeMillis()
                    ))
                    webSocket.send(subscribeResponse)
                }
                else -> {
                    Timber.w("$TAG: Unknown WebSocket message type: $type")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error parsing WebSocket message")
        }
    }

    private fun handleCreateTransaction(session: IHTTPSession): Response {
        return try {
            val body = getRequestBody(session)
            val request = gson.fromJson(body, CreateTransactionRequest::class.java)

            val transaction = PendingTransaction(
                id = request.id,
                amount = request.amount,
                phoneNumber = request.phoneNumber,
                expectedTxId = request.expectedTxId,
                walletType = request.walletType,
                expiresAt = Date(System.currentTimeMillis() + (request.expiresInMinutes ?: 30) * 60 * 1000)
            )

            runBlocking {
                pendingTransactionDao.insertTransaction(transaction)
            }

            newFixedLengthResponse(
                Response.Status.CREATED,
                "application/json",
                gson.toJson(mapOf("success" to true, "transaction" to transaction))
            )
        } catch (e: Exception) {
            Timber.e(e, "Error creating transaction")
            newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                gson.toJson(mapOf("error" to e.message))
            )
        }
    }

    private fun handleGetTransaction(uri: String): Response {
        val id = uri.substringAfterLast("/")
        return runBlocking {
            val transaction = pendingTransactionDao.getTransactionById(id)
            if (transaction != null) {
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    gson.toJson(transaction)
                )
            } else {
                newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "application/json",
                    gson.toJson(mapOf("error" to "Transaction not found"))
                )
            }
        }
    }

    private fun handleListTransactions(): Response {
        return runBlocking {
            val transactions = pendingTransactionDao.getPendingTransactions().first()
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                gson.toJson(mapOf("transactions" to transactions))
            )
        }
    }

    private fun handleGetSmsLogs(): Response {
        return runBlocking {
            val logs = smsLogDao.getAllLogs().first()
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                gson.toJson(mapOf("logs" to logs))
            )
        }
    }
    
    /**
     * الحصول على سجلات Webhook
     * GET /api/v1/webhooks/logs?transactionId=X&limit=100
     */
    private fun handleGetWebhookLogs(session: IHTTPSession): Response {
        return try {
            val params = session.parms
            val transactionId = params["transactionId"]
            val limit = params["limit"]?.toIntOrNull() ?: 100
            
            runBlocking {
                val logs = if (transactionId != null) {
                    // سجلات معاملة محددة
                    webhookLogDao.getLogsByTransaction(transactionId).first()
                } else {
                    // جميع السجلات
                    webhookLogDao.getAllLogs(limit).first()
                }
                
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    gson.toJson(mapOf(
                        "logs" to logs,
                        "count" to logs.size,
                        "transactionId" to transactionId,
                        "limit" to limit
                    ))
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling webhook logs request")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                gson.toJson(mapOf("error" to e.message))
            )
        }
    }
    
    /**
     * الحصول على إحصائيات Webhook
     * GET /api/v1/webhooks/stats?startTime=X&endTime=Y
     */
    private fun handleGetWebhookStats(session: IHTTPSession): Response {
        return try {
            val params = session.parms
            val startTime = params["startTime"]?.toLongOrNull() 
                ?: (System.currentTimeMillis() - 24 * 60 * 60 * 1000) // آخر 24 ساعة
            val endTime = params["endTime"]?.toLongOrNull() 
                ?: System.currentTimeMillis()
            
            runBlocking {
                val logs = webhookLogDao.getLogsByTimeRange(startTime, endTime)
                
                // حساب الإحصائيات
                val totalAttempts = logs.size
                val successCount = logs.count { it.status == com.sms.paymentgateway.data.entities.WebhookStatus.SUCCESS }
                val failedCount = logs.count { it.status == com.sms.paymentgateway.data.entities.WebhookStatus.FAILED }
                val retryingCount = logs.count { it.status == com.sms.paymentgateway.data.entities.WebhookStatus.RETRYING }
                val pendingCount = logs.count { it.status == com.sms.paymentgateway.data.entities.WebhookStatus.PENDING }
                
                val avgProcessingTime = logs
                    .mapNotNull { it.processingTimeMs }
                    .average()
                    .takeIf { !it.isNaN() } ?: 0.0
                
                val successRate = if (totalAttempts > 0) {
                    (successCount.toDouble() / totalAttempts * 100)
                } else 0.0
                
                // تجميع حسب transaction
                val uniqueTransactions = logs.map { it.transactionId }.distinct().size
                val retriesCount = logs.count { it.attempt > 1 }
                
                val stats = mapOf(
                    "period" to mapOf(
                        "startTime" to startTime,
                        "endTime" to endTime,
                        "durationMs" to (endTime - startTime)
                    ),
                    "attempts" to mapOf(
                        "total" to totalAttempts,
                        "success" to successCount,
                        "failed" to failedCount,
                        "retrying" to retryingCount,
                        "pending" to pendingCount
                    ),
                    "transactions" to mapOf(
                        "unique" to uniqueTransactions,
                        "withRetries" to retriesCount
                    ),
                    "performance" to mapOf(
                        "successRate" to String.format("%.2f%%", successRate),
                        "averageProcessingTimeMs" to String.format("%.2f", avgProcessingTime)
                    )
                )
                
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    gson.toJson(stats)
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling webhook stats request")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                gson.toJson(mapOf("error" to e.message))
            )
        }
    }
    
    /**
     * معالج طلب عرض المعاملات المنتهية
     * GET /api/v1/transactions/expired?limit=100
     */
    private fun handleGetExpiredTransactions(session: IHTTPSession): Response {
        return try {
            val params = session.parms
            val limit = params["limit"]?.toIntOrNull() ?: 100
            
            runBlocking {
                val expiredTransactions = pendingTransactionDao.getExpiredTransactions(limit)
                val totalExpired = pendingTransactionDao.getExpiredTransactionsCount()
                
                val response = mapOf(
                    "success" to true,
                    "data" to mapOf(
                        "transactions" to expiredTransactions.map { transaction ->
                            mapOf(
                                "id" to transaction.id,
                                "amount" to transaction.amount,
                                "phoneNumber" to transaction.phoneNumber,
                                "walletType" to transaction.walletType,
                                "status" to transaction.status.name,
                                "createdAt" to transaction.createdAt.time,
                                "expiresAt" to transaction.expiresAt.time,
                                "expectedTxId" to transaction.expectedTxId
                            )
                        },
                        "total" to totalExpired,
                        "limit" to limit,
                        "returned" to expiredTransactions.size
                    ),
                    "timestamp" to System.currentTimeMillis()
                )
                
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    gson.toJson(response)
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling expired transactions request")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                gson.toJson(mapOf(
                    "success" to false,
                    "error" to e.message
                ))
            )
        }
    }

    private fun getRequestBody(session: IHTTPSession): String {
        val map = mutableMapOf<String, String>()
        session.parseBody(map)
        return map["postData"] ?: ""
    }

    /**
     * معالجة طلب HTTP وارد عبر WebSocket Tunnel (Relay أو SmartTunnel)
     * يحاكي IHTTPSession بدون اتصال HTTP حقيقي
     */
    fun handleTunnelRequest(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: String?
    ): TunnelResponse {
        return try {
            // استخراج API key من headers
            val apiKey = headers["authorization"]?.removePrefix("Bearer ")
                ?: headers["x-api-key"]

            // التحقق من المصادقة
            if (!securityManager.validateApiKeyWithGrace(apiKey)) {
                return TunnelResponse(401, gson.toJson(mapOf("error" to "Unauthorized")))
            }

            // Rate limiting
            val clientIp = headers["x-forwarded-for"] ?: headers["x-real-ip"] ?: "tunnel"
            if (!rateLimiter.isAllowed(clientIp)) {
                return TunnelResponse(429, gson.toJson(mapOf("error" to "Rate limit exceeded")))
            }

            // تنظيف الـ path (إزالة query string)
            val cleanPath = path.substringBefore("?")
            val queryString = if (path.contains("?")) path.substringAfter("?") else ""

            // بناء params من query string
            val params = mutableMapOf<String, String>()
            queryString.split("&").forEach { pair ->
                val kv = pair.split("=")
                if (kv.size == 2) params[kv[0]] = kv[1]
            }

            // تنفيذ الطلب
            val responseBody = when {
                cleanPath == "/api/v1/health" && method == "GET" ->
                    gson.toJson(mapOf("status" to "ok", "tunnel" to true, "timestamp" to System.currentTimeMillis()))

                cleanPath == "/api/v1/transactions" && method == "POST" ->
                    handleTunnelCreateTransaction(body)

                cleanPath == "/api/v1/transactions" && method == "GET" ->
                    handleTunnelListTransactions()

                cleanPath.startsWith("/api/v1/transactions/") && method == "GET" ->
                    handleTunnelGetTransaction(cleanPath)

                cleanPath == "/api/v1/transactions/history" && method == "GET" ->
                    handleTunnelTransactionHistory(params)

                cleanPath == "/api/v1/analytics" && method == "GET" ->
                    handleTunnelAnalytics()

                cleanPath == "/api/v1/webhooks/logs" && method == "GET" ->
                    handleTunnelWebhookLogs(params)

                cleanPath == "/api/v1/devices" && method == "GET" ->
                    handleTunnelListDevices()

                cleanPath == "/api/v1/devices" && method == "POST" ->
                    handleTunnelRegisterDevice(body)

                cleanPath == "/api/v1/devices/status" && method == "GET" ->
                    handleTunnelDevicesStatus()

                cleanPath == "/api/v1/templates" && method == "GET" ->
                    handleTunnelListTemplates()

                cleanPath == "/api/v1/encryption/status" && method == "GET" ->
                    gson.toJson(mapOf("success" to true, "data" to mapOf(
                        "enabled" to encryptionManager.isEncryptionEnabled(),
                        "hasKey" to encryptionManager.hasEncryptionKey()
                    )))

                else -> gson.toJson(mapOf("error" to "Not found", "path" to cleanPath))
            }

            val statusCode = if (responseBody.contains("\"error\"") &&
                !responseBody.contains("\"success\":true")) 404 else 200

            TunnelResponse(statusCode, responseBody)

        } catch (e: Exception) {
            Timber.e(e, "Error handling tunnel request: $method $path")
            TunnelResponse(500, gson.toJson(mapOf("error" to e.message)))
        }
    }

    // ─── Tunnel-specific handlers (بدون IHTTPSession) ───────────────────────

    private fun handleTunnelCreateTransaction(body: String?): String = runBlocking {
        try {
            val req = gson.fromJson(body ?: "{}", CreateTransactionRequest::class.java)
            val existing = pendingTransactionDao.getTransactionById(req.id)
            if (existing != null) {
                return@runBlocking gson.toJson(mapOf("error" to "Transaction already exists"))
            }
            val expiresAt = java.util.Date(System.currentTimeMillis() + (req.expiresInMinutes ?: 30) * 60 * 1000L)
            val transaction = com.sms.paymentgateway.data.entities.PendingTransaction(
                id = req.id, amount = req.amount, phoneNumber = req.phoneNumber,
                expectedTxId = req.expectedTxId, walletType = req.walletType,
                expiresAt = expiresAt
            )
            pendingTransactionDao.insertTransaction(transaction)
            gson.toJson(mapOf("success" to true, "id" to req.id, "timestamp" to System.currentTimeMillis()))
        } catch (e: Exception) {
            gson.toJson(mapOf("error" to e.message))
        }
    }

    private fun handleTunnelListTransactions(): String = runBlocking {
        try {
            val transactions = pendingTransactionDao.getPendingTransactions().first()
            gson.toJson(mapOf("success" to true, "data" to transactions, "count" to transactions.size))
        } catch (e: Exception) { gson.toJson(mapOf("error" to e.message)) }
    }

    private fun handleTunnelGetTransaction(path: String): String = runBlocking {
        try {
            val id = path.removePrefix("/api/v1/transactions/")
            val tx = pendingTransactionDao.getTransactionById(id)
            if (tx != null) gson.toJson(mapOf("success" to true, "data" to tx))
            else gson.toJson(mapOf("error" to "Transaction not found"))
        } catch (e: Exception) { gson.toJson(mapOf("error" to e.message)) }
    }

    private fun handleTunnelTransactionHistory(params: Map<String, String>): String = runBlocking {
        try {
            val limit = params["limit"]?.toIntOrNull() ?: 50
            val offset = params["offset"]?.toIntOrNull() ?: 0
            val transactions = pendingTransactionDao.getFilteredTransactions(
                status = params["status"], walletType = params["walletType"],
                limit = limit, offset = offset
            )
            val total = pendingTransactionDao.countFilteredTransactions(
                status = params["status"], walletType = params["walletType"]
            )
            gson.toJson(mapOf("success" to true, "data" to transactions,
                "pagination" to mapOf("total" to total, "limit" to limit, "offset" to offset)))
        } catch (e: Exception) { gson.toJson(mapOf("error" to e.message)) }
    }

    private fun handleTunnelAnalytics(): String = runBlocking {
        try {
            val stats = analyticsRepository.getApiStats()
            gson.toJson(mapOf("success" to true, "data" to stats))
        } catch (e: Exception) { gson.toJson(mapOf("error" to e.message)) }
    }

    private fun handleTunnelWebhookLogs(params: Map<String, String>): String = runBlocking {
        try {
            val limit = params["limit"]?.toIntOrNull() ?: 50
            val logs = webhookLogDao.getLogsByStatus(
                com.sms.paymentgateway.data.entities.WebhookStatus.SUCCESS, limit
            )
            gson.toJson(mapOf("success" to true, "data" to logs, "count" to logs.size))
        } catch (e: Exception) { gson.toJson(mapOf("error" to e.message)) }
    }

    private fun handleTunnelListDevices(): String = runBlocking {
        try {
            val devices = deviceDao.getActiveDevicesList()
            gson.toJson(mapOf("success" to true, "data" to devices, "count" to devices.size))
        } catch (e: Exception) { gson.toJson(mapOf("error" to e.message)) }
    }

    private fun handleTunnelRegisterDevice(body: String?): String = runBlocking {
        try {
            val req = gson.fromJson(body ?: "{}", RegisterDeviceRequest::class.java)
            val device = com.sms.paymentgateway.data.entities.Device(
                deviceId = req.deviceId, deviceName = req.deviceName,
                phoneNumber = req.phoneNumber, dailyQuota = req.dailyQuota ?: 500,
                priority = req.priority ?: 5
            )
            deviceDao.insertDevice(device)
            gson.toJson(mapOf("success" to true, "data" to device))
        } catch (e: Exception) { gson.toJson(mapOf("error" to e.message)) }
    }

    private fun handleTunnelDevicesStatus(): String = runBlocking {
        try {
            val status = deviceLoadBalancer.getDevicesStatus()
            gson.toJson(mapOf("success" to true, "data" to status))
        } catch (e: Exception) { gson.toJson(mapOf("error" to e.message)) }
    }

    private fun handleTunnelListTemplates(): String = runBlocking {
        try {
            val templates = smsTemplateDao.getActiveTemplates()
            gson.toJson(mapOf("success" to true, "data" to templates))
        } catch (e: Exception) { gson.toJson(mapOf("error" to e.message)) }
    }
    
    /**
     * معالج طلب حالة التشفير
     * GET /api/v1/encryption/status
     */
    private fun handleGetEncryptionStatus(): Response {
        return try {
            val keyInfo = encryptionManager.getKeyInfo()
            val isEnabled = encryptionManager.isEncryptionEnabled()
            
            val response = mapOf(
                "success" to true,
                "data" to mapOf(
                    "enabled" to isEnabled,
                    "hasKey" to keyInfo.hasKey,
                    "hasBackup" to keyInfo.hasBackup,
                    "algorithm" to keyInfo.algorithm,
                    "keySize" to keyInfo.keySize
                ),
                "timestamp" to System.currentTimeMillis()
            )
            
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                gson.toJson(response)
            )
        } catch (e: Exception) {
            Timber.e(e, "Error getting encryption status")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                gson.toJson(mapOf(
                    "success" to false,
                    "error" to e.message
                ))
            )
        }
    }
    
    /**
     * معالج تفعيل التشفير
     * POST /api/v1/encryption/enable
     */
    private fun handleEnableEncryption(session: IHTTPSession): Response {
        return try {
            encryptionManager.setEncryptionEnabled(true)
            
            val response = mapOf(
                "success" to true,
                "message" to "Encryption enabled successfully",
                "timestamp" to System.currentTimeMillis()
            )
            
            Timber.i("🔐 Encryption enabled")
            
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                gson.toJson(response)
            )
        } catch (e: Exception) {
            Timber.e(e, "Error enabling encryption")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                gson.toJson(mapOf(
                    "success" to false,
                    "error" to e.message
                ))
            )
        }
    }
    
    /**
     * معالج تعطيل التشفير
     * POST /api/v1/encryption/disable
     */
    private fun handleDisableEncryption(): Response {
        return try {
            encryptionManager.setEncryptionEnabled(false)
            
            val response = mapOf(
                "success" to true,
                "message" to "Encryption disabled successfully",
                "warning" to "Data will be sent unencrypted",
                "timestamp" to System.currentTimeMillis()
            )
            
            Timber.w("⚠️ Encryption disabled")
            
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                gson.toJson(response)
            )
        } catch (e: Exception) {
            Timber.e(e, "Error disabling encryption")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                gson.toJson(mapOf(
                    "success" to false,
                    "error" to e.message
                ))
            )
        }
    }
    
    /**
     * معالج تدوير مفتاح التشفير
     * POST /api/v1/encryption/rotate-key
     */
    private fun handleRotateEncryptionKey(): Response {
        return try {
            val newKey = encryptionManager.rotateKey()
            
            val response = mapOf(
                "success" to true,
                "message" to "Encryption key rotated successfully",
                "warning" to "Old encrypted data cannot be decrypted with new key",
                "timestamp" to System.currentTimeMillis()
            )
            
            Timber.i("🔄 Encryption key rotated")
            
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                gson.toJson(response)
            )
        } catch (e: Exception) {
            Timber.e(e, "Error rotating encryption key")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                gson.toJson(mapOf(
                    "success" to false,
                    "error" to e.message
                ))
            )
        }
    }
    
    /**
     * معالج إنشاء معاملات جماعية
     * POST /api/v1/transactions/bulk
     */
    private fun handleBulkTransactions(session: IHTTPSession): Response {
        // rate limit أشد للـ bulk operations (10/دقيقة)
        val clientIp = session.headers["http-client-ip"] ?: session.headers["x-forwarded-for"] ?: "unknown"
        if (!rateLimiter.isAllowedForOperation(clientIp, com.sms.paymentgateway.utils.security.RateLimitOperation.BULK)) {
            return newFixedLengthResponse(Response.Status.TOO_MANY_REQUESTS, "application/json",
                gson.toJson(mapOf("success" to false, "error" to "Bulk rate limit exceeded (10/min)")))
        }
        return try {
            val body = getRequestBody(session)
            val request = gson.fromJson(body, BulkTransactionRequest::class.java)
            
            Timber.i("📦 Processing bulk transactions")
            
            runBlocking {
                val result = bulkTransactionProcessor.processCsvFile(
                    csvBase64 = request.csvFile,
                    columnMapping = ColumnMapping(
                        transactionId = request.columns.transactionId,
                        amount = request.columns.amount,
                        phoneNumber = request.columns.phoneNumber,
                        walletType = request.columns.walletType,
                        expectedTxId = request.columns.expectedTxId,
                        expiryMinutes = request.columns.expiryMinutes
                    ),
                    defaultExpiryMinutes = request.defaultExpiryMinutes ?: 30
                )
                
                val response = mapOf(
                    "success" to result.success,
                    "data" to mapOf(
                        "totalRows" to result.totalRows,
                        "successCount" to result.successCount,
                        "failureCount" to result.failureCount,
                        "transactions" to result.transactions
                    ),
                    "errors" to result.errors,
                    "timestamp" to System.currentTimeMillis()
                )
                
                Timber.i("✅ Bulk processing completed: ${result.successCount}/${result.totalRows} successful")
                
                newFixedLengthResponse(
                    if (result.success) Response.Status.OK else Response.Status.BAD_REQUEST,
                    "application/json",
                    gson.toJson(response)
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error processing bulk transactions")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                gson.toJson(mapOf(
                    "success" to false,
                    "error" to e.message
                ))
            )
        }
    }
    
    /**
     * معالج التحقق من ملف CSV
     * POST /api/v1/transactions/bulk/validate
     */
    private fun handleValidateBulkCsv(session: IHTTPSession): Response {
        return try {
            val body = getRequestBody(session)
            val request = gson.fromJson(body, ValidateCsvRequest::class.java)
            
            Timber.d("🔍 Validating CSV file")
            
            runBlocking {
                val result = bulkTransactionProcessor.validateCsvFile(
                    csvBase64 = request.csvFile,
                    columnMapping = ColumnMapping(
                        transactionId = request.columns.transactionId,
                        amount = request.columns.amount,
                        phoneNumber = request.columns.phoneNumber,
                        walletType = request.columns.walletType,
                        expectedTxId = request.columns.expectedTxId,
                        expiryMinutes = request.columns.expiryMinutes
                    )
                )
                
                val response = mapOf(
                    "success" to result.valid,
                    "data" to mapOf(
                        "valid" to result.valid,
                        "rowCount" to result.rowCount,
                        "headers" to result.headers
                    ),
                    "errors" to result.errors,
                    "timestamp" to System.currentTimeMillis()
                )
                
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    gson.toJson(response)
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error validating CSV file")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                gson.toJson(mapOf(
                    "success" to false,
                    "error" to e.message
                ))
            )
        }
    }

    data class CreateTransactionRequest(
        val id: String,
        val amount: Double,
        val phoneNumber: String,
        val expectedTxId: String? = null,
        val walletType: String? = null,
        val expiresInMinutes: Int? = 30
    )
    
    data class BulkTransactionRequest(
        val csvFile: String, // Base64 encoded CSV
        val columns: BulkColumnMapping,
        val defaultExpiryMinutes: Int? = 30
    )
    
    data class BulkColumnMapping(
        val transactionId: String,
        val amount: String,
        val phoneNumber: String,
        val walletType: String? = null,
        val expectedTxId: String? = null,
        val expiryMinutes: String? = null
    )
    
    data class ValidateCsvRequest(
        val csvFile: String, // Base64 encoded CSV
        val columns: BulkColumnMapping
    )
    
    /**
     * معالج طلب التحليلات
     * GET /api/v1/analytics?startTime=X&endTime=Y
     */
    private fun handleGetAnalytics(session: IHTTPSession): Response {
        return try {
            val params = session.parms
            val startTime = params["startTime"]?.toLongOrNull()
                ?: run {
                    val cal = java.util.Calendar.getInstance()
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    cal.set(java.util.Calendar.MINUTE, 0)
                    cal.set(java.util.Calendar.SECOND, 0)
                    cal.set(java.util.Calendar.MILLISECOND, 0)
                    cal.timeInMillis
                }
            val endTime = params["endTime"]?.toLongOrNull() ?: System.currentTimeMillis()
            
            runBlocking {
                val stats = analyticsRepository.getApiStats()
                val response = mapOf(
                    "success" to true,
                    "data" to stats,
                    "timestamp" to System.currentTimeMillis()
                )
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    gson.toJson(response)
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting analytics")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                gson.toJson(mapOf("success" to false, "error" to e.message))
            )
        }
    }

    // ─── Device Handlers ────────────────────────────────────────────────────

    /** GET /api/v1/devices */
    private fun handleListDevices(): Response = runBlocking {
        try {
            val devices = deviceDao.getActiveDevicesList()
            newFixedLengthResponse(
                Response.Status.OK, "application/json",
                gson.toJson(mapOf("success" to true, "data" to devices,
                    "count" to devices.size, "timestamp" to System.currentTimeMillis()))
            )
        } catch (e: Exception) {
            errorResponse(e)
        }
    }

    /** POST /api/v1/devices */
    private fun handleRegisterDevice(session: IHTTPSession): Response = runBlocking {
        try {
            val body = getRequestBody(session)
            val req = gson.fromJson(body, RegisterDeviceRequest::class.java)

            val existing = deviceDao.getDeviceById(req.deviceId)
            if (existing != null) {
                return@runBlocking newFixedLengthResponse(
                    Response.Status.CONFLICT, "application/json",
                    gson.toJson(mapOf("success" to false, "error" to "Device already registered"))
                )
            }

            val device = com.sms.paymentgateway.data.entities.Device(
                deviceId = req.deviceId,
                deviceName = req.deviceName,
                phoneNumber = req.phoneNumber,
                dailyQuota = req.dailyQuota ?: 500,
                priority = req.priority ?: 5
            )
            deviceDao.insertDevice(device)
            Timber.i("📱 Device registered: ${req.deviceId}")

            newFixedLengthResponse(
                Response.Status.CREATED, "application/json",
                gson.toJson(mapOf("success" to true, "data" to device,
                    "timestamp" to System.currentTimeMillis()))
            )
        } catch (e: Exception) {
            errorResponse(e)
        }
    }

    /** DELETE /api/v1/devices/{deviceId} */
    private fun handleDeleteDevice(uri: String): Response = runBlocking {
        try {
            val deviceId = uri.removePrefix("/api/v1/devices/")
            deviceDao.deleteDeviceById(deviceId)
            Timber.i("🗑️ Device deleted: $deviceId")
            newFixedLengthResponse(
                Response.Status.OK, "application/json",
                gson.toJson(mapOf("success" to true, "message" to "Device deleted",
                    "timestamp" to System.currentTimeMillis()))
            )
        } catch (e: Exception) {
            errorResponse(e)
        }
    }

    /** GET /api/v1/devices/status */
    private fun handleDevicesStatus(): Response = runBlocking {
        try {
            val statusList = deviceLoadBalancer.getDevicesStatus()
            newFixedLengthResponse(
                Response.Status.OK, "application/json",
                gson.toJson(mapOf("success" to true, "data" to statusList,
                    "count" to statusList.size, "timestamp" to System.currentTimeMillis()))
            )
        } catch (e: Exception) {
            errorResponse(e)
        }
    }

    /** PUT /api/v1/devices/{deviceId} */
    private fun handleUpdateDevice(uri: String, session: IHTTPSession): Response = runBlocking {
        try {
            val deviceId = uri.removePrefix("/api/v1/devices/")
            val body = getRequestBody(session)
            val req = gson.fromJson(body, UpdateDeviceRequest::class.java)

            val existing = deviceDao.getDeviceById(deviceId)
                ?: return@runBlocking newFixedLengthResponse(
                    Response.Status.NOT_FOUND, "application/json",
                    gson.toJson(mapOf("success" to false, "error" to "Device not found"))
                )

            val updated = existing.copy(
                deviceName = req.deviceName ?: existing.deviceName,
                isActive = req.isActive ?: existing.isActive,
                dailyQuota = req.dailyQuota ?: existing.dailyQuota,
                priority = req.priority ?: existing.priority
            )
            deviceDao.updateDevice(updated)

            newFixedLengthResponse(
                Response.Status.OK, "application/json",
                gson.toJson(mapOf("success" to true, "data" to updated,
                    "timestamp" to System.currentTimeMillis()))
            )
        } catch (e: Exception) {
            errorResponse(e)
        }
    }

    private fun errorResponse(e: Exception): Response {
        Timber.e(e, "API error")
        return newFixedLengthResponse(
            Response.Status.INTERNAL_ERROR, "application/json",
            gson.toJson(mapOf("success" to false, "error" to e.message))
        )
    }

    // ─── Template Handlers ──────────────────────────────────────────────────

    /** GET /api/v1/templates */
    private fun handleListTemplates(): Response = runBlocking {
        try {
            val templates = smsTemplateDao.getActiveTemplates()
            newFixedLengthResponse(Response.Status.OK, "application/json",
                gson.toJson(mapOf("success" to true, "data" to templates,
                    "count" to templates.size, "timestamp" to System.currentTimeMillis())))
        } catch (e: Exception) { errorResponse(e) }
    }

    /** POST /api/v1/templates */
    private fun handleCreateTemplate(session: IHTTPSession): Response = runBlocking {
        try {
            val body = getRequestBody(session)
            val req = gson.fromJson(body, TemplateRequest::class.java)

            val validation = templateProcessor.validateTemplate(req.template)
            if (!validation.valid) {
                return@runBlocking newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
                    gson.toJson(mapOf("success" to false, "errors" to validation.errors)))
            }

            if (req.isDefault == true) smsTemplateDao.clearDefaultFlag()

            val template = com.sms.paymentgateway.data.entities.SmsTemplate(
                name = req.name,
                template = req.template,
                description = req.description,
                isDefault = req.isDefault ?: false
            )
            val id = smsTemplateDao.insertTemplate(template)
            Timber.i("📝 Template created: ${req.name}")

            newFixedLengthResponse(Response.Status.CREATED, "application/json",
                gson.toJson(mapOf("success" to true, "id" to id,
                    "variables" to validation.variables, "timestamp" to System.currentTimeMillis())))
        } catch (e: Exception) { errorResponse(e) }
    }

    /** GET /api/v1/templates/{id}/preview */
    private fun handlePreviewTemplate(uri: String): Response = runBlocking {
        try {
            val id = uri.removePrefix("/api/v1/templates/").removeSuffix("/preview").toLongOrNull()
                ?: return@runBlocking newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
                    gson.toJson(mapOf("success" to false, "error" to "Invalid template ID")))

            val template = smsTemplateDao.getTemplateById(id)
                ?: return@runBlocking newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json",
                    gson.toJson(mapOf("success" to false, "error" to "Template not found")))

            val preview = templateProcessor.previewTemplate(template.template)
            newFixedLengthResponse(Response.Status.OK, "application/json",
                gson.toJson(mapOf("success" to true,
                    "data" to mapOf("original" to template.template, "preview" to preview),
                    "timestamp" to System.currentTimeMillis())))
        } catch (e: Exception) { errorResponse(e) }
    }

    /** PUT /api/v1/templates/{id} */
    private fun handleUpdateTemplate(uri: String, session: IHTTPSession): Response = runBlocking {
        try {
            val id = uri.removePrefix("/api/v1/templates/").toLongOrNull()
                ?: return@runBlocking newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
                    gson.toJson(mapOf("success" to false, "error" to "Invalid ID")))

            val existing = smsTemplateDao.getTemplateById(id)
                ?: return@runBlocking newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json",
                    gson.toJson(mapOf("success" to false, "error" to "Template not found")))

            val req = gson.fromJson(getRequestBody(session), TemplateRequest::class.java)

            if (req.isDefault == true) smsTemplateDao.clearDefaultFlag()

            val updated = existing.copy(
                name = req.name.takeIf { it.isNotBlank() } ?: existing.name,
                template = req.template.takeIf { it.isNotBlank() } ?: existing.template,
                description = req.description ?: existing.description,
                isDefault = req.isDefault ?: existing.isDefault,
                isActive = req.isActive ?: existing.isActive,
                updatedAt = System.currentTimeMillis()
            )
            smsTemplateDao.updateTemplate(updated)

            newFixedLengthResponse(Response.Status.OK, "application/json",
                gson.toJson(mapOf("success" to true, "data" to updated,
                    "timestamp" to System.currentTimeMillis())))
        } catch (e: Exception) { errorResponse(e) }
    }

    /** DELETE /api/v1/templates/{id} */
    private fun handleDeleteTemplate(uri: String): Response = runBlocking {
        try {
            val id = uri.removePrefix("/api/v1/templates/").toLongOrNull()
                ?: return@runBlocking newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
                    gson.toJson(mapOf("success" to false, "error" to "Invalid ID")))
            smsTemplateDao.deleteTemplateById(id)
            newFixedLengthResponse(Response.Status.OK, "application/json",
                gson.toJson(mapOf("success" to true, "message" to "Template deleted",
                    "timestamp" to System.currentTimeMillis())))
        } catch (e: Exception) { errorResponse(e) }
    }

    data class TemplateRequest(
        val name: String = "",
        val template: String = "",
        val description: String? = null,
        val isDefault: Boolean? = null,
        val isActive: Boolean? = null
    )

    // ─── Security Handlers ──────────────────────────────────────────────────

    /** POST /api/v1/security/rotate-key */
    private fun handleRotateApiKey(): Response {
        return try {
            val rotated = securityManager.rotateApiKey()
            newFixedLengthResponse(Response.Status.OK, "application/json",
                gson.toJson(mapOf(
                    "success" to true,
                    "newKey" to rotated.newKey,
                    "gracePeriodEnd" to rotated.gracePeriodEnd,
                    "message" to "Old key valid for 24 hours",
                    "timestamp" to System.currentTimeMillis()
                )))
        } catch (e: Exception) { errorResponse(e) }
    }

    /** GET /api/v1/security/api-key/info */
    private fun handleGetApiKeyInfo(): Response {
        return try {
            val info = securityManager.getApiKeyInfo()
            newFixedLengthResponse(Response.Status.OK, "application/json",
                gson.toJson(mapOf(
                    "success" to true,
                    "data" to mapOf(
                        "key" to info.key,
                        "source" to info.source.name,
                        "isPersistent" to info.isPersistent,
                        "androidId" to info.androidId,
                        "description" to when (info.source) {
                            com.sms.paymentgateway.utils.security.ApiKeySource.DERIVED ->
                                "مشتق من Android ID - ثابت بعد إعادة التثبيت ✅"
                            com.sms.paymentgateway.utils.security.ApiKeySource.MANUAL ->
                                "مفتاح يدوي - أنت عيّنته"
                        }
                    ),
                    "timestamp" to System.currentTimeMillis()
                )))
        } catch (e: Exception) { errorResponse(e) }
    }

    /** POST /api/v1/security/api-key/set - تعيين مفتاح يدوي */
    private fun handleSetManualApiKey(session: IHTTPSession): Response {
        return try {
            val body = getRequestBody(session)
            val req = gson.fromJson(body, SetApiKeyRequest::class.java)
            if (req.key.length < 16) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
                    gson.toJson(mapOf("success" to false, "error" to "Key must be at least 16 characters")))
            }
            securityManager.setManualApiKey(req.key)
            newFixedLengthResponse(Response.Status.OK, "application/json",
                gson.toJson(mapOf(
                    "success" to true,
                    "message" to "Manual API key set. Restart the service to apply.",
                    "timestamp" to System.currentTimeMillis()
                )))
        } catch (e: Exception) { errorResponse(e) }
    }

    /** POST /api/v1/security/api-key/reset - العودة للمفتاح المشتق */
    private fun handleResetApiKey(): Response {
        return try {
            securityManager.clearManualApiKey()
            val newKey = securityManager.getApiKey()
            newFixedLengthResponse(Response.Status.OK, "application/json",
                gson.toJson(mapOf(
                    "success" to true,
                    "message" to "Reverted to derived key (stable after reinstall)",
                    "key" to newKey,
                    "timestamp" to System.currentTimeMillis()
                )))
        } catch (e: Exception) { errorResponse(e) }
    }

    data class SetApiKeyRequest(val key: String = "")

    /** GET /api/v1/security/rate-limit/stats */
    private fun handleRateLimitStats(): Response {
        return try {
            val stats = rateLimiter.getStats()
            newFixedLengthResponse(Response.Status.OK, "application/json",
                gson.toJson(mapOf("success" to true, "data" to stats,
                    "timestamp" to System.currentTimeMillis())))
        } catch (e: Exception) { errorResponse(e) }
    }

    /** GET /api/v1/security/ip-whitelist */
    private fun handleGetIpWhitelist(): Response {
        return try {
            val list = securityManager.getIpWhitelistEnhanced()
            newFixedLengthResponse(Response.Status.OK, "application/json",
                gson.toJson(mapOf("success" to true, "data" to list,
                    "count" to list.size, "timestamp" to System.currentTimeMillis())))
        } catch (e: Exception) { errorResponse(e) }
    }

    /** POST /api/v1/security/ip-whitelist */
    private fun handleAddIpWhitelist(session: IHTTPSession): Response {
        return try {
            val body = getRequestBody(session)
            val req = gson.fromJson(body, IpWhitelistRequest::class.java)
            securityManager.addIpToWhitelistEnhanced(req.ip, req.description ?: "", req.expiresAt)
            newFixedLengthResponse(Response.Status.OK, "application/json",
                gson.toJson(mapOf("success" to true, "message" to "IP added",
                    "timestamp" to System.currentTimeMillis())))
        } catch (e: Exception) { errorResponse(e) }
    }

    /** DELETE /api/v1/security/ip-whitelist/{ip} */
    private fun handleRemoveIpWhitelist(uri: String): Response {
        return try {
            val ip = uri.removePrefix("/api/v1/security/ip-whitelist/")
            securityManager.removeIpFromWhitelistEnhanced(ip)
            newFixedLengthResponse(Response.Status.OK, "application/json",
                gson.toJson(mapOf("success" to true, "message" to "IP removed",
                    "timestamp" to System.currentTimeMillis())))
        } catch (e: Exception) { errorResponse(e) }
    }

    // ─── Transaction History Handler ─────────────────────────────────────────

    /** GET /api/v1/transactions/history?status=X&walletType=Y&limit=50&offset=0 */
    private fun handleTransactionHistory(session: IHTTPSession): Response = runBlocking {
        try {
            val p = session.parms
            val status = p["status"]
            val walletType = p["walletType"]
            val limit = p["limit"]?.toIntOrNull() ?: 50
            val offset = p["offset"]?.toIntOrNull() ?: 0

            val transactions = pendingTransactionDao.getFilteredTransactions(
                status = status, walletType = walletType, limit = limit, offset = offset
            )
            val total = pendingTransactionDao.countFilteredTransactions(
                status = status, walletType = walletType
            )

            newFixedLengthResponse(Response.Status.OK, "application/json",
                gson.toJson(mapOf(
                    "success" to true,
                    "data" to transactions,
                    "pagination" to mapOf("total" to total, "limit" to limit,
                        "offset" to offset, "hasMore" to (offset + limit < total)),
                    "timestamp" to System.currentTimeMillis()
                )))
        } catch (e: Exception) { errorResponse(e) }
    }

    // ─── Relay Config Handlers ───────────────────────────────────────────────

    /** GET /api/v1/relay/config */
    private fun handleGetRelayConfig(): Response {
        return try {
            val tunnelState = smartTunnelManager.state.value
            newFixedLengthResponse(Response.Status.OK, "application/json",
                gson.toJson(mapOf(
                    "success" to true,
                    "data" to mapOf(
                        "wsUrl" to securityManager.getRelayUrl(),
                        "httpBase" to securityManager.getRelayHttpBase(),
                        "isDefault" to securityManager.isUsingDefaultRelayUrl(),
                        "tunnelStatus" to tunnelState.status.name,
                        "publicUrl" to tunnelState.publicUrl,
                        "deviceId" to tunnelState.deviceId,
                        "requestsHandled" to tunnelState.requestsHandled
                    ),
                    "timestamp" to System.currentTimeMillis()
                )))
        } catch (e: Exception) { errorResponse(e) }
    }

    /** POST /api/v1/relay/config - تغيير الـ Relay Server */
    private fun handleSetRelayConfig(session: IHTTPSession): Response {
        return try {
            val body = getRequestBody(session)
            val req = gson.fromJson(body, RelayConfigRequest::class.java)

            securityManager.setCustomRelayServer(req.wsUrl, req.httpBase)

            // إعادة تشغيل SmartTunnel بالـ server الجديد
            smartTunnelManager.stop()
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.delay(1000)
            }
            smartTunnelManager.start()

            newFixedLengthResponse(Response.Status.OK, "application/json",
                gson.toJson(mapOf(
                    "success" to true,
                    "message" to "Relay server updated. Reconnecting...",
                    "wsUrl" to req.wsUrl,
                    "httpBase" to req.httpBase,
                    "timestamp" to System.currentTimeMillis()
                )))
        } catch (e: Exception) { errorResponse(e) }
    }

    /** GET /api/v1/relay/status */
    private fun handleGetRelayStatus(): Response {
        return try {
            val tunnelState = smartTunnelManager.state.value
            val isActive = tunnelState.status == SmartTunnelManager.TunnelStatus.ACTIVE

            newFixedLengthResponse(Response.Status.OK, "application/json",
                gson.toJson(mapOf(
                    "success" to true,
                    "data" to mapOf(
                        "active" to isActive,
                        "status" to tunnelState.status.name,
                        "publicUrl" to tunnelState.publicUrl,
                        "fullApiUrl" to smartTunnelManager.getFullApiUrl(),
                        "deviceId" to tunnelState.deviceId,
                        "relayServer" to tunnelState.relayServer,
                        "requestsHandled" to tunnelState.requestsHandled,
                        "error" to tunnelState.error,
                        "instructions" to if (isActive) mapOf(
                            "createTransaction" to "POST ${tunnelState.publicUrl}/api/v1/transactions",
                            "getTransactions" to "GET ${tunnelState.publicUrl}/api/v1/transactions",
                            "analytics" to "GET ${tunnelState.publicUrl}/api/v1/analytics",
                            "health" to "GET ${tunnelState.publicUrl}/api/v1/health"
                        ) else null
                    ),
                    "timestamp" to System.currentTimeMillis()
                )))
        } catch (e: Exception) { errorResponse(e) }
    }

    data class RelayConfigRequest(
        val wsUrl: String,
        val httpBase: String
    )

    data class IpWhitelistRequest(
        val ip: String,
        val description: String? = null,
        val expiresAt: Long? = null
    )

    data class RegisterDeviceRequest(
        val deviceId: String,
        val deviceName: String,
        val phoneNumber: String,
        val dailyQuota: Int? = 500,
        val priority: Int? = 5
    )

    data class UpdateDeviceRequest(
        val deviceName: String? = null,
        val isActive: Boolean? = null,
        val dailyQuota: Int? = null,
        val priority: Int? = null
    )
}
