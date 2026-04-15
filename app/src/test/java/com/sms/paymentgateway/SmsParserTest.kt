package com.sms.paymentgateway

import com.sms.paymentgateway.domain.models.TransactionType
import com.sms.paymentgateway.domain.models.WalletType
import com.sms.paymentgateway.utils.parser.SmsParser
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SmsParserTest {

    private lateinit var smsParser: SmsParser

    @Before
    fun setup() {
        smsParser = SmsParser()
    }

    @Test
    fun `parse Vodafone Cash received message`() {
        val sender = "Vodafone"
        val message = "تم استلام مبلغ 500 جنيه من 01012345678 رقم العملية VC123456789"

        val result = smsParser.parse(sender, message)

        assertNotNull(result)
        assertEquals(WalletType.VODAFONE_CASH, result?.walletType)
        assertEquals(500.0, result?.amount, 0.01)
        assertEquals("VC123456789", result?.transactionId)
        assertEquals("01012345678", result?.senderPhone)
        assertEquals(TransactionType.RECEIVED, result?.transactionType)
        assertTrue(result?.confidence ?: 0.0 > 0.8)
    }

    @Test
    fun `parse Vodafone Cash sent message`() {
        val sender = "VodafoneCash"
        val message = "تم إرسال 250.50 جنيه إلى 01098765432 رقم العملية VC987654321"

        val result = smsParser.parse(sender, message)

        assertNotNull(result)
        assertEquals(250.5, result?.amount, 0.01)
        assertEquals("VC987654321", result?.transactionId)
        assertEquals("01098765432", result?.receiverPhone)
        assertEquals(TransactionType.SENT, result?.transactionType)
    }

    @Test
    fun `parse Orange Money message`() {
        val sender = "Orange"
        val message = "تم استلام 1000 جنيه من 01112345678 رقم OM123456"

        val result = smsParser.parse(sender, message)

        assertNotNull(result)
        assertEquals(WalletType.ORANGE_MONEY, result?.walletType)
        assertEquals(1000.0, result?.amount, 0.01)
        assertEquals("OM123456", result?.transactionId)
    }

    @Test
    fun `parse InstaPay message`() {
        val sender = "InstaPay"
        val message = "استلمت مبلغ: 750 جنيه من 01212345678 رقم: IP789012"

        val result = smsParser.parse(sender, message)

        assertNotNull(result)
        assertEquals(WalletType.INSTAPAY, result?.walletType)
        assertEquals(750.0, result?.amount, 0.01)
        assertEquals("IP789012", result?.transactionId)
        assertEquals("01212345678", result?.senderPhone)
    }

    @Test
    fun `parse message with English amount`() {
        val sender = "Vodafone"
        val message = "You received amount: 500 EGP from 01012345678 ref: VC123456"

        val result = smsParser.parse(sender, message)

        assertNotNull(result)
        assertEquals(500.0, result?.amount, 0.01)
        assertEquals("VC123456", result?.transactionId)
    }

    @Test
    fun `parse message with decimal amount`() {
        val sender = "Vodafone"
        val message = "تم استلام 99.99 جنيه من 01012345678"

        val result = smsParser.parse(sender, message)

        assertNotNull(result)
        assertEquals(99.99, result?.amount, 0.01)
    }

    @Test
    fun `return null for unknown sender`() {
        val sender = "UnknownBank"
        val message = "Some random message"

        val result = smsParser.parse(sender, message)

        assertNull(result)
    }

    @Test
    fun `parse message without transaction ID`() {
        val sender = "Vodafone"
        val message = "تم استلام 500 جنيه من 01012345678"

        val result = smsParser.parse(sender, message)

        assertNotNull(result)
        assertEquals(500.0, result?.amount, 0.01)
        assertNull(result?.transactionId)
        assertTrue(result?.confidence ?: 0.0 < 1.0)
    }

    @Test
    fun `confidence score calculation`() {
        // Message with all fields
        val fullMessage = "تم استلام 500 جنيه من 01012345678 رقم VC123456"
        val fullResult = smsParser.parse("Vodafone", fullMessage)
        
        // Message with only amount
        val partialMessage = "تم استلام 500 جنيه"
        val partialResult = smsParser.parse("Vodafone", partialMessage)

        assertTrue((fullResult?.confidence ?: 0.0) > (partialResult?.confidence ?: 0.0))
    }
}
