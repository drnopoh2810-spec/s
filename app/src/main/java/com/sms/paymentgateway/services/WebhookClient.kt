package com.sms.paymentgateway.services

import android.util.Log
import com.google.gson.Gson
import com.sms.paymentgateway.domain.models.ParsedSmsData
import com.sms.paymentgateway.utils.security.SecurityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * عميل Webhook مع دعم إعادة المحاولة التلقائية
 */
@Singleton
class WebhookClient @Inject constructor(
    private val webhookRetryManager: WebhookRetryManager,
    private val securityManager: SecurityManager,
    private val encryptionManager: com.sms.paymentgateway.utils.security.EncryptionManager,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "WebhookClient"
    }

    private var webhookUrl: String? = null
    private var apiKey: String? = null

    /**
     * تعيين عنوان Webhook
     */
    fun setWebhookUrl(url: String) {
        webhookUrl = url
        Log.d(TAG, "Webhook URL configured: $url")
    }
    
    /**
     * تعيين API Key للتوقيع
     */
    fun setApiKey(key: String) {
        apiKey = key
        Log.d(TAG, "API Key configured")
    }

    /**
     * إرسال إشعار تأكيد الدفع مع إعادة المحاولة التلقائية
     */
    suspend fun sendPaymentConfirmation(
        transactionId: String,
        parsedData: ParsedSmsData,
        confidence: Double
    ) {
        val url = webhookUrl ?: run {
            Timber.w("⚠️ Webhook URL not configured")
            Log.w(TAG, "Webhook URL not configured, skipping webhook for transaction $transactionId")
            return
        }
        
        val key = apiKey ?: run {
            Timber.w("⚠️ API Key not configured")
            Log.w(TAG, "API Key not configured, skipping webhook for transaction $transactionId")
            return
        }

        // إرسال في background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // بناء Payload
                val payload = buildPayload(transactionId, parsedData, confidence)
                
                Log.d(TAG, "📤 Sending webhook for transaction $transactionId")
                
                // إرسال مع إعادة المحاولة
                val result = webhookRetryManager.sendWithRetry(
                    url = url,
                    payload = payload,
                    transactionId = transactionId,
                    apiKey = key
                )
                
                // معالجة النتيجة
                when (result) {
                    is WebhookResult.Success -> {
                        Timber.i("✅ Webhook delivered successfully for transaction: $transactionId")
                        Log.i(TAG, "✅ Webhook delivered: $transactionId - HTTP ${result.httpStatusCode} - ${result.processingTimeMs}ms")
                    }
                    is WebhookResult.Failure -> {
                        Timber.e("❌ Failed to deliver webhook for transaction: $transactionId after all retries")
                        Log.e(TAG, "❌ Webhook failed: $transactionId - ${result.exception.message}", result.exception)
                        
                        // يمكن إضافة إشعار للمستخدم هنا
                        notifyWebhookFailure(transactionId, result)
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error in sendPaymentConfirmation")
                Log.e(TAG, "Error in sendPaymentConfirmation for transaction $transactionId", e)
            }
        }
    }
    
    /**
     * بناء Payload للـ webhook
     */
    private fun buildPayload(
        transactionId: String,
        parsedData: ParsedSmsData,
        confidence: Double
    ): JSONObject {
        val basePayload = JSONObject().apply {
            put("event", "PAYMENT_CONFIRMED")
            put("transactionId", transactionId)
            put("smsData", JSONObject().apply {
                put("walletType", parsedData.walletType.name)
                put("walletTxId", parsedData.transactionId)
                put("amount", parsedData.amount)
                put("senderPhone", parsedData.senderPhone)
                put("timestamp", parsedData.timestamp.time)
            })
            put("confidence", confidence)
            put("processedAt", Date().time)
        }
        
        // تشفير البيانات الحساسة إذا كان التشفير مفعّلاً
        return if (encryptionManager.isEncryptionEnabled()) {
            try {
                val encryptedData = encryptionManager.encryptJson(basePayload.toString())
                JSONObject().apply {
                    put("encrypted", true)
                    put("data", encryptedData)
                    put("algorithm", "AES-256-GCM")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to encrypt webhook payload, sending unencrypted")
                basePayload.put("encrypted", false)
            }
        } else {
            basePayload.put("encrypted", false)
        }
    }
    
    /**
     * إشعار بفشل Webhook
     */
    private fun notifyWebhookFailure(transactionId: String, result: WebhookResult.Failure) {
        // يمكن إضافة notification للمستخدم
        // أو إرسال alert
        Log.w(TAG, "⚠️ Webhook failure notification for transaction $transactionId")
    }
    
    // Data classes للتوافق مع الكود القديم (deprecated)
    @Deprecated("Use JSONObject payload instead")
    data class PaymentConfirmationPayload(
        val event: String,
        val transactionId: String,
        val smsData: SmsDataPayload,
        val confidence: Double,
        val processedAt: Long
    )

    @Deprecated("Use JSONObject payload instead")
    data class SmsDataPayload(
        val walletType: String,
        val walletTxId: String?,
        val amount: Double?,
        val senderPhone: String?,
        val timestamp: Long
    )
}
