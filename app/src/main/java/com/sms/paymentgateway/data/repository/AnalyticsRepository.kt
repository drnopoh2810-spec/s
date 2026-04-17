package com.sms.paymentgateway.data.repository

import com.sms.paymentgateway.data.dao.PendingTransactionDao
import com.sms.paymentgateway.data.dao.SmsLogDao
import com.sms.paymentgateway.data.dao.WebhookLogDao
import com.sms.paymentgateway.data.entities.TransactionStatus
import com.sms.paymentgateway.data.entities.WebhookStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مستودع التحليلات
 * يجمع البيانات من مصادر متعددة ويحسبها للعرض في لوحة التحكم
 */
@Singleton
class AnalyticsRepository @Inject constructor(
    private val smsLogDao: SmsLogDao,
    private val pendingTransactionDao: PendingTransactionDao,
    private val webhookLogDao: WebhookLogDao
) {
    /**
     * الحصول على إحصائيات لوحة التحكم الكاملة
     */
    suspend fun getDashboardAnalytics(
        startTime: Long = todayStart(),
        endTime: Long = System.currentTimeMillis()
    ): DashboardAnalytics = withContext(Dispatchers.IO) {

        val allLogs = smsLogDao.getAllLogs().first()
        val allTransactions = pendingTransactionDao.getPendingTransactions().first()

        // فلترة حسب النطاق الزمني
        val logsInRange = allLogs.filter {
            it.receivedAt.time in startTime..endTime
        }
        val txInRange = allTransactions.filter {
            it.createdAt.time in startTime..endTime
        }

        // إحصائيات SMS
        val totalSms = logsInRange.size
        val parsedSms = logsInRange.count { it.parsed }
        val matchedSms = logsInRange.count { it.matched }
        val parseRate = if (totalSms > 0) parsedSms * 100.0 / totalSms else 0.0
        val matchRate = if (parsedSms > 0) matchedSms * 100.0 / parsedSms else 0.0

        // إحصائيات المعاملات
        val totalTx = txInRange.size
        val matchedTx = txInRange.count { it.status == TransactionStatus.MATCHED }
        val expiredTx = txInRange.count { it.status == TransactionStatus.EXPIRED }
        val pendingTx = txInRange.count { it.status == TransactionStatus.PENDING }
        val totalAmount = txInRange
            .filter { it.status == TransactionStatus.MATCHED }
            .sumOf { it.amount }
        val avgConfidence = txInRange
            .mapNotNull { it.confidence }
            .average()
            .takeIf { !it.isNaN() } ?: 0.0

        // توزيع حسب نوع المحفظة
        val byWallet = logsInRange
            .filter { it.parsed }
            .groupBy { it.walletType }
            .mapValues { it.value.size }

        // توزيع حسب الساعة (آخر 24 ساعة)
        val byHour = logsInRange
            .groupBy { Calendar.getInstance().apply { time = it.receivedAt }.get(Calendar.HOUR_OF_DAY) }
            .mapValues { it.value.size }

        // إحصائيات Webhook
        val webhookLogs = webhookLogDao.getLogsByTimeRange(startTime, endTime)
        val webhookTotal = webhookLogs.size
        val webhookSuccess = webhookLogs.count { it.status == WebhookStatus.SUCCESS }
        val webhookFailed = webhookLogs.count { it.status == WebhookStatus.FAILED }
        val webhookSuccessRate = if (webhookTotal > 0) webhookSuccess * 100.0 / webhookTotal else 0.0
        val avgWebhookTime = webhookLogs
            .mapNotNull { it.processingTimeMs }
            .average()
            .takeIf { !it.isNaN() } ?: 0.0

        DashboardAnalytics(
            period = AnalyticsPeriod(startTime, endTime),
            sms = SmsAnalytics(
                total = totalSms,
                parsed = parsedSms,
                matched = matchedSms,
                parseRate = parseRate,
                matchRate = matchRate,
                byWallet = byWallet,
                byHour = byHour
            ),
            transactions = TransactionAnalytics(
                total = totalTx,
                matched = matchedTx,
                expired = expiredTx,
                pending = pendingTx,
                totalAmount = totalAmount,
                avgConfidence = avgConfidence
            ),
            webhooks = WebhookAnalytics(
                total = webhookTotal,
                success = webhookSuccess,
                failed = webhookFailed,
                successRate = webhookSuccessRate,
                avgProcessingTimeMs = avgWebhookTime
            )
        )
    }

    /**
     * إحصائيات مبسطة للـ API
     */
    suspend fun getApiStats(): Map<String, Any> = withContext(Dispatchers.IO) {
        val analytics = getDashboardAnalytics()
        mapOf(
            "sms" to mapOf(
                "total" to analytics.sms.total,
                "parsed" to analytics.sms.parsed,
                "matched" to analytics.sms.matched,
                "parseRate" to "%.1f%%".format(analytics.sms.parseRate),
                "matchRate" to "%.1f%%".format(analytics.sms.matchRate)
            ),
            "transactions" to mapOf(
                "total" to analytics.transactions.total,
                "matched" to analytics.transactions.matched,
                "expired" to analytics.transactions.expired,
                "pending" to analytics.transactions.pending,
                "totalAmount" to analytics.transactions.totalAmount,
                "avgConfidence" to "%.1f%%".format(analytics.transactions.avgConfidence)
            ),
            "webhooks" to mapOf(
                "total" to analytics.webhooks.total,
                "success" to analytics.webhooks.success,
                "failed" to analytics.webhooks.failed,
                "successRate" to "%.1f%%".format(analytics.webhooks.successRate),
                "avgProcessingTimeMs" to "%.0f".format(analytics.webhooks.avgProcessingTimeMs)
            ),
            "period" to mapOf(
                "startTime" to analytics.period.startTime,
                "endTime" to analytics.period.endTime
            )
        )
    }

    private fun todayStart(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

// ─── Data Classes ───────────────────────────────────────────────────────────

data class DashboardAnalytics(
    val period: AnalyticsPeriod,
    val sms: SmsAnalytics,
    val transactions: TransactionAnalytics,
    val webhooks: WebhookAnalytics
)

data class AnalyticsPeriod(val startTime: Long, val endTime: Long)

data class SmsAnalytics(
    val total: Int,
    val parsed: Int,
    val matched: Int,
    val parseRate: Double,
    val matchRate: Double,
    val byWallet: Map<String, Int>,
    val byHour: Map<Int, Int>
)

data class TransactionAnalytics(
    val total: Int,
    val matched: Int,
    val expired: Int,
    val pending: Int,
    val totalAmount: Double,
    val avgConfidence: Double
)

data class WebhookAnalytics(
    val total: Int,
    val success: Int,
    val failed: Int,
    val successRate: Double,
    val avgProcessingTimeMs: Double
)
