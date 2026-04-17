package com.sms.paymentgateway.services

import android.util.Log
import com.sms.paymentgateway.data.dao.WebhookLogDao
import com.sms.paymentgateway.data.entities.WebhookLog
import com.sms.paymentgateway.data.entities.WebhookStatus
import com.sms.paymentgateway.utils.security.SecurityManager
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدير إعادة محاولة Webhook
 * يتعامل مع إرسال Webhook مع إعادة المحاولة التلقائية عند الفشل
 */
@Singleton
class WebhookRetryManager @Inject constructor(
    private val webhookLogDao: WebhookLogDao,
    private val securityManager: SecurityManager
) {
    companion object {
        private const val TAG = "WebhookRetryManager"
        
        /** الحد الأقصى لعدد المحاولات */
        private const val MAX_RETRIES = 5
        
        /** التأخير الأساسي (1 ثانية) */
        private const val BASE_DELAY_MS = 1000L
        
        /** الحد الأقصى للتأخير (32 ثانية) */
        private const val MAX_DELAY_MS = 32000L
        
        /** Timeout للاتصال */
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        
        /** Timeout للقراءة */
        private const val READ_TIMEOUT_SECONDS = 10L
        
        /** Timeout للكتابة */
        private const val WRITE_TIMEOUT_SECONDS = 10L
    }
    
    /** HTTP Client مُحسّن للـ webhooks */
    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    /**
     * إرسال Webhook مع إعادة المحاولة التلقائية
     * 
     * @param url عنوان Webhook
     * @param payload البيانات المرسلة
     * @param transactionId معرف المعاملة
     * @param apiKey مفتاح API للتوقيع
     * @return نتيجة الإرسال
     */
    suspend fun sendWithRetry(
        url: String,
        payload: JSONObject,
        transactionId: String,
        apiKey: String
    ): WebhookResult {
        var attempt = 0
        var lastError: Exception? = null
        
        while (attempt < MAX_RETRIES) {
            attempt++
            
            Log.d(TAG, "Sending webhook for transaction $transactionId (attempt $attempt/$MAX_RETRIES)")
            
            val result = sendWebhook(
                url = url,
                payload = payload,
                transactionId = transactionId,
                apiKey = apiKey,
                attempt = attempt
            )
            
            when (result) {
                is WebhookResult.Success -> {
                    Log.i(TAG, "✅ Webhook sent successfully for transaction $transactionId on attempt $attempt")
                    return result
                }
                is WebhookResult.Failure -> {
                    lastError = result.exception
                    
                    // تسجيل الفشل
                    logWebhookAttempt(
                        transactionId = transactionId,
                        url = url,
                        attempt = attempt,
                        status = if (attempt < MAX_RETRIES) WebhookStatus.RETRYING else WebhookStatus.FAILED,
                        httpStatusCode = result.httpStatusCode,
                        errorMessage = result.exception.message,
                        requestPayload = payload.toString(),
                        responseBody = result.responseBody,
                        processingTimeMs = result.processingTimeMs
                    )
                    
                    // إذا كان الخطأ 4xx (client error)، لا نعيد المحاولة
                    if (result.httpStatusCode in 400..499) {
                        Log.w(TAG, "⚠️ Client error (${result.httpStatusCode}), not retrying for transaction $transactionId")
                        return result
                    }
                    
                    // إذا لم نصل للحد الأقصى، ننتظر ثم نعيد المحاولة
                    if (attempt < MAX_RETRIES) {
                        val delayMs = calculateBackoffDelay(attempt)
                        Log.i(TAG, "⏳ Retrying webhook for transaction $transactionId in ${delayMs}ms (attempt $attempt/$MAX_RETRIES)")
                        delay(delayMs)
                    }
                }
            }
        }
        
        // جميع المحاولات فشلت
        Log.e(TAG, "❌ All webhook attempts failed for transaction $transactionId", lastError)
        return WebhookResult.Failure(
            exception = lastError ?: Exception("Unknown error after $MAX_RETRIES attempts"),
            httpStatusCode = null,
            responseBody = null,
            processingTimeMs = 0
        )
    }
    
    /**
     * إرسال Webhook مرة واحدة
     */
    private suspend fun sendWebhook(
        url: String,
        payload: JSONObject,
        transactionId: String,
        apiKey: String,
        attempt: Int
    ): WebhookResult {
        val startTime = System.currentTimeMillis()
        
        try {
            // إضافة timestamp و signature
            val timestamp = System.currentTimeMillis() / 1000
            val signature = securityManager.generateHmacSignature(
                data = payload.toString(),
                secret = apiKey
            )
            
            // بناء الطلب
            val requestBody = payload.toString()
                .toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Webhook-Signature", signature)
                .addHeader("X-Webhook-Timestamp", timestamp.toString())
                .addHeader("X-Transaction-Id", transactionId)
                .addHeader("X-Attempt", attempt.toString())
                .addHeader("User-Agent", "SMS-Payment-Gateway/2.0")
                .build()
            
            // إرسال الطلب
            val response = client.newCall(request).execute()
            val processingTime = System.currentTimeMillis() - startTime
            val responseBody = response.body?.string()
            
            return if (response.isSuccessful) {
                // نجح الإرسال
                logWebhookAttempt(
                    transactionId = transactionId,
                    url = url,
                    attempt = attempt,
                    status = WebhookStatus.SUCCESS,
                    httpStatusCode = response.code,
                    errorMessage = null,
                    requestPayload = payload.toString(),
                    responseBody = responseBody,
                    processingTimeMs = processingTime
                )
                
                WebhookResult.Success(
                    httpStatusCode = response.code,
                    responseBody = responseBody,
                    processingTimeMs = processingTime
                )
            } else {
                // فشل الإرسال (status code غير ناجح)
                WebhookResult.Failure(
                    exception = Exception("HTTP ${response.code}: ${response.message}"),
                    httpStatusCode = response.code,
                    responseBody = responseBody,
                    processingTimeMs = processingTime
                )
            }
            
        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "Error sending webhook for transaction $transactionId", e)
            
            return WebhookResult.Failure(
                exception = e,
                httpStatusCode = null,
                responseBody = null,
                processingTimeMs = processingTime
            )
        }
    }
    
    /**
     * حساب وقت الانتظار باستخدام Exponential Backoff
     * 1s, 2s, 4s, 8s, 16s, 32s (max)
     */
    private fun calculateBackoffDelay(attempt: Int): Long {
        val delay = BASE_DELAY_MS * (1 shl (attempt - 1))
        return minOf(delay, MAX_DELAY_MS)
    }
    
    /**
     * تسجيل محاولة Webhook
     */
    private suspend fun logWebhookAttempt(
        transactionId: String,
        url: String,
        attempt: Int,
        status: WebhookStatus,
        httpStatusCode: Int?,
        errorMessage: String?,
        requestPayload: String,
        responseBody: String?,
        processingTimeMs: Long
    ) {
        try {
            val log = WebhookLog(
                transactionId = transactionId,
                webhookUrl = url,
                attempt = attempt,
                status = status,
                httpStatusCode = httpStatusCode,
                errorMessage = errorMessage,
                requestPayload = requestPayload,
                responseBody = responseBody,
                processingTimeMs = processingTimeMs
            )
            
            webhookLogDao.insert(log)
            
            Log.d(TAG, "📝 Logged webhook attempt: $transactionId - attempt $attempt - status $status")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging webhook attempt", e)
        }
    }
}

/**
 * نتيجة إرسال Webhook
 */
sealed class WebhookResult {
    /**
     * نجح الإرسال
     */
    data class Success(
        val httpStatusCode: Int,
        val responseBody: String?,
        val processingTimeMs: Long
    ) : WebhookResult()
    
    /**
     * فشل الإرسال
     */
    data class Failure(
        val exception: Exception,
        val httpStatusCode: Int?,
        val responseBody: String?,
        val processingTimeMs: Long
    ) : WebhookResult()
}
