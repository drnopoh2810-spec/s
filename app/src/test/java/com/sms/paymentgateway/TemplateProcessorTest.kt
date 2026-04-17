package com.sms.paymentgateway

import com.sms.paymentgateway.data.dao.SmsTemplateDao
import com.sms.paymentgateway.domain.models.ParsedSmsData
import com.sms.paymentgateway.domain.models.WalletType
import com.sms.paymentgateway.services.TemplateProcessor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TemplateProcessorTest {

    @Mock private lateinit var smsTemplateDao: SmsTemplateDao
    private lateinit var processor: TemplateProcessor

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        processor = TemplateProcessor(smsTemplateDao)
    }

    @Test
    fun `processTemplate replaces all variables`() {
        val template = "تم استلام {amount} جنيه من {phone} عبر {wallet}"
        val vars = mapOf("amount" to "500.00", "phone" to "01012345678", "wallet" to "VODAFONE_CASH")
        val result = processor.processTemplate(template, vars)
        assertEquals("تم استلام 500.00 جنيه من 01012345678 عبر VODAFONE_CASH", result)
    }

    @Test
    fun `processTemplate removes unfilled variables`() {
        val template = "مبلغ {amount} من {phone} - {unknown_var}"
        val vars = mapOf("amount" to "100", "phone" to "01012345678")
        val result = processor.processTemplate(template, vars)
        assertFalse(result.contains("{"))
        assertFalse(result.contains("}"))
    }

    @Test
    fun `processTemplate handles empty variables map`() {
        val template = "رسالة بدون متغيرات"
        val result = processor.processTemplate(template, emptyMap())
        assertEquals("رسالة بدون متغيرات", result)
    }

    @Test
    fun `extractVariables returns all variables in template`() {
        val template = "مبلغ {amount} من {phone} عبر {wallet} رقم {tx_id}"
        val vars = processor.extractVariables(template)
        assertEquals(4, vars.size)
        assertTrue(vars.containsAll(listOf("amount", "phone", "wallet", "tx_id")))
    }

    @Test
    fun `extractVariables returns empty list for template without variables`() {
        val template = "رسالة ثابتة بدون متغيرات"
        val vars = processor.extractVariables(template)
        assertTrue(vars.isEmpty())
    }

    @Test
    fun `validateTemplate returns valid for supported variables`() {
        val template = "تم استلام {amount} من {phone} عبر {wallet}"
        val result = processor.validateTemplate(template)
        assertTrue(result.valid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validateTemplate returns invalid for unsupported variables`() {
        val template = "مبلغ {amount} من {unknown_field}"
        val result = processor.validateTemplate(template)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("unknown_field") })
    }

    @Test
    fun `validateTemplate returns invalid for empty template`() {
        val result = processor.validateTemplate("")
        assertFalse(result.valid)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `previewTemplate fills sample data`() {
        val template = "تم استلام {amount} جنيه من {phone}"
        val preview = processor.previewTemplate(template)
        assertFalse(preview.contains("{"))
        assertTrue(preview.contains("500.00"))
        assertTrue(preview.contains("01012345678"))
    }

    @Test
    fun `buildVariables creates correct map from ParsedSmsData`() {
        val parsedData = ParsedSmsData(
            walletType = WalletType.VODAFONE_CASH,
            amount = 750.0,
            senderPhone = "01098765432",
            receiverPhone = null,
            transactionId = "VF999",
            transactionType = com.sms.paymentgateway.domain.models.TransactionType.RECEIVED,
            timestamp = Date(),
            rawMessage = "test message",
            confidence = 0.95
        )
        val vars = processor.buildVariables(parsedData, "TX_TEST")
        assertEquals("750.00", vars["amount"])
        assertEquals("01098765432", vars["phone"])
        assertEquals("VODAFONE_CASH", vars["wallet"])
        assertEquals("VF999", vars["tx_id"])
        assertEquals("TX_TEST", vars["transaction_id"])
    }
}
