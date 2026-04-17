package com.sms.paymentgateway

import android.content.Context
import com.sms.paymentgateway.data.dao.PendingTransactionDao
import com.sms.paymentgateway.data.entities.PendingTransaction
import com.sms.paymentgateway.data.entities.TransactionStatus
import com.sms.paymentgateway.services.ExpirationChecker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExpirationCheckerTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var pendingTransactionDao: PendingTransactionDao

    private lateinit var expirationChecker: ExpirationChecker

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        expirationChecker = ExpirationChecker(context, pendingTransactionDao)
    }

    @Test
    fun `checkAndExpireTransactions should expire old transactions`() = runTest {
        // Arrange
        val currentTime = System.currentTimeMillis()
        `when`(pendingTransactionDao.getExpiredTransactionsCount()).thenReturn(3)

        // Act
        val expiredCount = expirationChecker.checkAndExpireTransactions()

        // Assert
        verify(pendingTransactionDao).expireOldTransactions(anyLong())
        verify(pendingTransactionDao).getExpiredTransactionsCount()
        assertEquals(3, expiredCount)
    }

    @Test
    fun `checkAndExpireTransactions should return 0 when no expired transactions`() = runTest {
        // Arrange
        `when`(pendingTransactionDao.getExpiredTransactionsCount()).thenReturn(0)

        // Act
        val expiredCount = expirationChecker.checkAndExpireTransactions()

        // Assert
        assertEquals(0, expiredCount)
    }

    @Test
    fun `checkAndExpireTransactions should handle errors gracefully`() = runTest {
        // Arrange
        `when`(pendingTransactionDao.expireOldTransactions(anyLong()))
            .thenThrow(RuntimeException("Database error"))

        // Act
        val expiredCount = expirationChecker.checkAndExpireTransactions()

        // Assert
        assertEquals(0, expiredCount)
    }

    @Test
    fun `startPeriodicCheck should start checking`() {
        // Act
        expirationChecker.startPeriodicCheck()

        // Assert
        assertTrue(expirationChecker.isRunning())
    }

    @Test
    fun `stopPeriodicCheck should stop checking`() {
        // Arrange
        expirationChecker.startPeriodicCheck()

        // Act
        expirationChecker.stopPeriodicCheck()

        // Assert
        assertFalse(expirationChecker.isRunning())
    }

    @Test
    fun `startPeriodicCheck should not start twice`() {
        // Arrange
        expirationChecker.startPeriodicCheck()
        val firstRunning = expirationChecker.isRunning()

        // Act
        expirationChecker.startPeriodicCheck()
        val secondRunning = expirationChecker.isRunning()

        // Assert
        assertTrue(firstRunning)
        assertTrue(secondRunning)
        
        // Cleanup
        expirationChecker.stopPeriodicCheck()
    }

    @Test
    fun `checkNow should trigger immediate check`() = runTest {
        // Arrange
        `when`(pendingTransactionDao.getExpiredTransactionsCount()).thenReturn(5)

        // Act
        val expiredCount = expirationChecker.checkNow()

        // Assert
        verify(pendingTransactionDao).expireOldTransactions(anyLong())
        assertEquals(5, expiredCount)
    }

    @Test
    fun `cleanup should stop checking and release resources`() {
        // Arrange
        expirationChecker.startPeriodicCheck()

        // Act
        expirationChecker.cleanup()

        // Assert
        assertFalse(expirationChecker.isRunning())
    }
}
