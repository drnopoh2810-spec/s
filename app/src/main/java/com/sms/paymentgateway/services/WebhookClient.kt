package com.sms.paymentgateway.services

import com.google.gson.Gson
import com.sms.paymentgateway.domain.models.ParsedSmsData
import com.sms.paymentgateway.utils.security.SecurityManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebhookClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val securityManager: SecurityManager,
    private val gson: Gson
) {

    private var webhookUrl: String? = null

    fun setWebhookUrl(url: String) {
        webhookUrl = url
    }

    suspend fun sendPaymentConfirmation(
        transactionId: String,
        parsedData: ParsedSmsData,
        confidence: Double
    ) {
        val url = webhookUrl ?: run {
            Timber.w("Webhook URL not configured")
            return
        }

        try {
            val payload = PaymentConfirmationPayload(
                event = "PAYMENT_CONFIRMED",
                transactionId = transactionId,
                smsData = SmsDataPayload(
                    walletType = parsedData.walletType.name,
                    walletTxId = parsedData.transactionId,
                    amount = parsedData.amount,
                    senderPhone = parsedData.senderPhone,
                    timestamp = parsedData.timestamp.time
                ),
                confidence = confidence,
                processedAt = Date().time
            )

            val json = gson.toJson(payload)
            val signature = securityManager.generateHmacSignature(json)

            val request = Request.Builder()
                .url(url)
                .post(json.toRequestBody("application/json".toMediaType()))
                .addHeader("X-Signature", signature)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                Timber.i("Webhook sent successfully for transaction: $transactionId")
            } else {
                Timber.e("Webhook failed: ${response.code} - ${response.message}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error sending webhook")
        }
    }

    data class PaymentConfirmationPayload(
        val event: String,
        val transactionId: String,
        val smsData: SmsDataPayload,
        val confidence: Double,
        val processedAt: Long
    )

    data class SmsDataPayload(
        val walletType: String,
        val walletTxId: String?,
        val amount: Double?,
        val senderPhone: String?,
        val timestamp: Long
    )
}
