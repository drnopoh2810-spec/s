package com.sms.paymentgateway.services

import com.sms.paymentgateway.data.dao.SmsTemplateDao
import com.sms.paymentgateway.data.entities.SmsTemplate
import com.sms.paymentgateway.domain.models.ParsedSmsData
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * معالج قوالب الرسائل
 *
 * المتغيرات المدعومة:
 * {amount}      - المبلغ
 * {phone}       - رقم الهاتف
 * {wallet}      - نوع المحفظة
 * {tx_id}       - رقم العملية
 * {date}        - التاريخ
 * {time}        - الوقت
 * {transaction_id} - معرف المعاملة
 */
@Singleton
class TemplateProcessor @Inject constructor(
    private val smsTemplateDao: SmsTemplateDao
) {
    companion object {
        private const val TAG = "TemplateProcessor"

        // المتغيرات المدعومة
        val SUPPORTED_VARIABLES = listOf(
            "amount", "phone", "wallet", "tx_id",
            "date", "time", "transaction_id"
        )
    }

    /**
     * معالجة قالب باستبدال المتغيرات
     */
    fun processTemplate(template: String, variables: Map<String, String>): String {
        var result = template
        variables.forEach { (key, value) ->
            result = result.replace("{$key}", value)
        }
        // إزالة أي متغيرات غير مُعبأة
        result = result.replace(Regex("\\{[^}]+}"), "")
        return result.trim()
    }

    /**
     * بناء متغيرات من بيانات SMS
     */
    fun buildVariables(
        parsedData: ParsedSmsData,
        transactionId: String? = null
    ): Map<String, String> {
        val now = java.util.Date()
        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())

        return buildMap {
            put("amount", parsedData.amount?.let { "%.2f".format(it) } ?: "")
            put("phone", parsedData.senderPhone ?: parsedData.receiverPhone ?: "")
            put("wallet", parsedData.walletType.name)
            put("tx_id", parsedData.transactionId ?: "")
            put("date", dateFormat.format(now))
            put("time", timeFormat.format(now))
            put("transaction_id", transactionId ?: "")
        }
    }

    /**
     * معالجة القالب الافتراضي مع بيانات SMS
     */
    suspend fun processDefaultTemplate(
        parsedData: ParsedSmsData,
        transactionId: String? = null
    ): String? {
        val template = smsTemplateDao.getDefaultTemplate() ?: return null
        val variables = buildVariables(parsedData, transactionId)
        val result = processTemplate(template.template, variables)
        smsTemplateDao.incrementUsage(template.id)
        Timber.d("$TAG: Processed template '${template.name}'")
        return result
    }

    /**
     * معالجة قالب محدد بالـ ID
     */
    suspend fun processTemplateById(
        templateId: Long,
        parsedData: ParsedSmsData,
        transactionId: String? = null
    ): String? {
        val template = smsTemplateDao.getTemplateById(templateId) ?: return null
        val variables = buildVariables(parsedData, transactionId)
        val result = processTemplate(template.template, variables)
        smsTemplateDao.incrementUsage(template.id)
        return result
    }

    /**
     * استخراج المتغيرات من نص القالب
     */
    fun extractVariables(template: String): List<String> {
        val regex = Regex("\\{([^}]+)}")
        return regex.findAll(template).map { it.groupValues[1] }.distinct().toList()
    }

    /**
     * التحقق من صحة القالب
     */
    fun validateTemplate(template: String): TemplateValidationResult {
        if (template.isBlank()) {
            return TemplateValidationResult(false, listOf("Template cannot be empty"))
        }
        val variables = extractVariables(template)
        val unsupported = variables.filter { it !in SUPPORTED_VARIABLES }
        return if (unsupported.isEmpty()) {
            TemplateValidationResult(true, emptyList(), variables)
        } else {
            TemplateValidationResult(
                valid = false,
                errors = listOf("Unsupported variables: ${unsupported.joinToString(", ")}"),
                variables = variables
            )
        }
    }

    /**
     * معاينة القالب ببيانات تجريبية
     */
    fun previewTemplate(template: String): String {
        val sampleVars = mapOf(
            "amount" to "500.00",
            "phone" to "01012345678",
            "wallet" to "VODAFONE_CASH",
            "tx_id" to "VF123456",
            "date" to "17/04/2026",
            "time" to "14:30",
            "transaction_id" to "TX_001"
        )
        return processTemplate(template, sampleVars)
    }
}

data class TemplateValidationResult(
    val valid: Boolean,
    val errors: List<String>,
    val variables: List<String> = emptyList()
)
