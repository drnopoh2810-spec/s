package com.sms.paymentgateway.services

import com.google.gson.Gson
import com.sms.paymentgateway.data.dao.PendingTransactionDao
import com.sms.paymentgateway.data.dao.SmsLogDao
import com.sms.paymentgateway.data.entities.PendingTransaction
import com.sms.paymentgateway.utils.security.SecurityManager
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiServer @Inject constructor(
    private val pendingTransactionDao: PendingTransactionDao,
    private val smsLogDao: SmsLogDao,
    private val securityManager: SecurityManager,
    private val rateLimiter: com.sms.paymentgateway.utils.security.RateLimiter,
    private val gson: Gson
) : NanoHTTPD(8080) {

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
            "service" to "SMS Payment Gateway"
        )
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            gson.toJson(health)
        )
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
