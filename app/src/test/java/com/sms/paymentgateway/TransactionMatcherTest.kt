package com.sms.paymentgateway

import com.sms.paymentgateway.data.entities.PendingTransaction
import com.sms.paymentgateway.domain.models.ParsedSmsData
import com.sms.paymentgateway.domain.models.TransactionType
import com.sms.paymentgateway.domain.models.WalletType
import com.sms.paymentgateway.utils.matcher.TransactionMatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Date

class TransactionMatcherTest {

    private lateinit var matcher: TransactionMatcher

    @Before
    fun setup() {
        matcher = TransactionMatcher()
    }

    @Test
    fun `exact transaction ID match`() = runBlocking {
        val parsedSms = ParsedSmsData(
            walletType = WalletType.VODAFONE_CASH,
            transactionId = "VC123456",
            amount = 500.0,
            senderPhone = "01012345678",
            receiverPhone = null,
            transactionType = TransactionType.RECEIVED,
            timestamp = Date(),
            rawMessage = "test",
            confidence = 0.9
        )

        val pending = PendingTransaction(
            id = "order-001",
            amount = 500.0,
            phoneNumber = "01012345678",
            expectedTxId = "VC123456",
            expiresAt = Date(System.currentTimeMillis() + 3600000)
        )

        val result = matcher.findMatch(parsedSms, listOf(pending))

        assertTrue(result.matched)
        assertEquals(pending, result.transaction)
        assertEquals(1.0, result.confidence, 0.01)
    }

    @Test
    fun `amount and phone match`() = runBlocking {
        val parsedSms = ParsedSmsData(
            walletType = WalletType.VODAFONE_CASH,
            transactionId = null,
            amount = 750.0,
            senderPhone = "01012345678",
            receiverPhone = null,
            transactionType = TransactionType.RECEIVED,
            timestamp = Date(),
            rawMessage = "test",
            confidence = 0.8
        )

        val pending = PendingTransaction(
            id = "order-002",
            amount = 750.0,
            phoneNumber = "01012345678",
            expiresAt = Date(System.currentTimeMillis() + 3600000)
        )

        val result = matcher.findMatch(parsedSms, listOf(pending))

        assertTrue(result.matched)
        assertTrue(result.confidence >= 0.7)
    }

    @Test
    fun `no match for different amount`() = runBlocking {
        val parsedSms = ParsedSmsData(
            walletType = WalletType.VODAFONE_CASH,
            transactionId = null,
            amount = 500.0,
            senderPhone = "01012345678",
            receiverPhone = null,
            transactionType = TransactionType.RECEIVED,
            timestamp = Date(),
            rawMessage = "test",
            confidence = 0.8
        )

        val pending = PendingTransaction(
            id = "order-003",
            amount = 600.0,
            phoneNumber = "01012345678",
            expiresAt = Date(System.currentTimeMillis() + 3600000)
        )

        val result = matcher.findMatch(parsedSms, listOf(pending))

        assertFalse(result.matched)
    }

    @Test
    fun `no match for different phone`() = runBlocking {
        val parsedSms = ParsedSmsData(
            walletType = WalletType.VODAFONE_CASH,
            transactionId = null,
            amount = 500.0,
            senderPhone = "01012345678",
            receiverPhone = null,
            transactionType = TransactionType.RECEIVED,
            timestamp = Date(),
            rawMessage = "test",
            confidence = 0.8
        )

        val pending = PendingTransaction(
            id = "order-004",
            amount = 500.0,
            phoneNumber = "01098765432",
            expiresAt = Date(System.currentTimeMillis() + 3600000)
        )

        val result = matcher.findMatch(parsedSms, listOf(pending))

        assertFalse(result.matched)
    }

    @Test
    fun `match within time window`() = runBlocking {
        val now = System.currentTimeMillis()
        
        val parsedSms = ParsedSmsData(
            walletType = WalletType.VODAFONE_CASH,
            transactionId = null,
            amount = 500.0,
            senderPhone = "01012345678",
            receiverPhone = null,
            transactionType = TransactionType.RECEIVED,
            timestamp = Date(now),
            rawMessage = "test",
            confidence = 0.8
        )

        val pending = PendingTransaction(
            id = "order-005",
            amount = 500.0,
            phoneNumber = "01012345678",
            createdAt = Date(now - 2 * 60 * 1000), // 2 minutes ago
            expiresAt = Date(now + 3600000)
        )

        val result = matcher.findMatch(parsedSms, listOf(pending))

        assertTrue(result.matched)
    }

    @Test
    fun `no match outside time window`() = runBlocking {
        val now = System.currentTimeMillis()
        
        val parsedSms = ParsedSmsData(
            walletType = WalletType.VODAFONE_CASH,
            transactionId = null,
            amount = 500.0,
            senderPhone = "01012345678",
            receiverPhone = null,
            transactionType = TransactionType.RECEIVED,
            timestamp = Date(now),
            rawMessage = "test",
            confidence = 0.8
        )

        val pending = PendingTransaction(
            id = "order-006",
            amount = 500.0,
            phoneNumber = "01012345678",
            createdAt = Date(now - 10 * 60 * 1000), // 10 minutes ago
            expiresAt = Date(now + 3600000)
        )

        val result = matcher.findMatch(parsedSms, listOf(pending))

        assertFalse(result.matched)
    }

    @Test
    fun `select best match from multiple candidates`() = runBlocking {
        val parsedSms = ParsedSmsData(
            walletType = WalletType.VODAFONE_CASH,
            transactionId = "VC123456",
            amount = 500.0,
            senderPhone = "01012345678",
            receiverPhone = null,
            transactionType = TransactionType.RECEIVED,
            timestamp = Date(),
            rawMessage = "test",
            confidence = 0.9
        )

        val pending1 = PendingTransaction(
            id = "order-007",
            amount = 500.0,
            phoneNumber = "01012345678",
            expectedTxId = null,
            expiresAt = Date(System.currentTimeMillis() + 3600000)
        )

        val pending2 = PendingTransaction(
            id = "order-008",
            amount = 500.0,
            phoneNumber = "01012345678",
            expectedTxId = "VC123456",
            expiresAt = Date(System.currentTimeMillis() + 3600000)
        )

        val result = matcher.findMatch(parsedSms, listOf(pending1, pending2))

        assertTrue(result.matched)
        assertEquals("order-008", result.transaction?.id)
        assertEquals(1.0, result.confidence, 0.01)
    }
}
