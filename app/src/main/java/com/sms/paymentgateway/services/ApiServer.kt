package com.sms.paymentgateway.services

import android.content.Context
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
    private val context: Context,
    private val pendingTransactionDao: PendingTransactionDao,
    private val smsLogDao: SmsLogDao,
    private val securityManager: SecurityManager,
    private val rateLimiter: com.sms.paymentgateway.utils.security.RateLimiter,
    private val webSocketHandler: WebSocketHandler,
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

        // IP Whitelist check
        if (!securityManager.isIpAllowed(clientIp)) {
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

        // Security check
        val apiKey = session.headers["authorization"]?.removePrefix("Bearer ")
        if (!securityManager.validateApiKey(apiKey)) {
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
            uri == "/api/v1/sms/logs" && method == Method.GET -> handleGetSmsLogs()
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

    private fun getRequestBody(session: IHTTPSession): String {
        val map = mutableMapOf<String, String>()
        session.parseBody(map)
        return map["postData"] ?: ""
    }

    data class CreateTransactionRequest(
        val id: String,
        val amount: Double,
        val phoneNumber: String,
        val expectedTxId: String? = null,
        val walletType: String? = null,
        val expiresInMinutes: Int? = 30
    )
}
