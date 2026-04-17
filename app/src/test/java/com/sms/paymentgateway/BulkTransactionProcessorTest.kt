package com.sms.paymentgateway

import android.util.Base64
import com.sms.paymentgateway.data.dao.PendingTransactionDao
import com.sms.paymentgateway.data.entities.PendingTransaction
import com.sms.paymentgateway.services.BulkTransactionProcessor
import com.sms.paymentgateway.services.ColumnMapping
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BulkTransactionProcessorTest {

    @Mock
    private lateinit var pendingTransactionDao: PendingTransactionDao

    private lateinit var bulkTransactionProcessor: BulkTransactionProcessor

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        bulkTransactionProcessor = BulkTransactionProcessor(pendingTransactionDao)
    }

    @Test
    fun `processCsvFile should process valid CSV successfully`() = runTest {
        // Arrange
        val csvContent = """
            id,amount,phone,wallet_type
            TX001,500.00,01012345678,VODAFONE_CASH
            TX002,750.00,01098765432,ORANGE_MONEY
        """.trimIndent()
        
        val csvBase64 = android.util.Base64.encodeToString(
            csvContent.toByteArray(),
            android.util.Base64.NO_WRAP
        )
        
        val columnMapping = ColumnMapping(
            transactionId = "id",
            amount = "amount",
            phoneNumber = "phone",
            walletType = "wallet_type"
        )
        
        `when`(pendingTransactionDao.getTransactionById(anyString())).thenReturn(null)
        
        // Act
        val result = bulkTransactionProcessor.processCsvFile(csvBase64, columnMapping)
        
        // Assert
        assertTrue(result.success)
        assertEquals(2, result.totalRows)
        assertEquals(2, result.successCount)
        assertEquals(0, result.failureCount)
        verify(pendingTransactionDao, times(2)).insertTransaction(any())
    }

    @Test
    fun `processCsvFile should handle empty CSV`() = runTest {
        // Arrange
        val csvContent = "id,amount,phone"
        val csvBase64 = android.util.Base64.encodeToString(
            csvContent.toByteArray(),
            android.util.Base64.NO_WRAP
        )
        
        val columnMapping = ColumnMapping(
            transactionId = "id",
            amount = "amount",
            phoneNumber = "phone"
        )
        
        // Act
        val result = bulkTransactionProcessor.processCsvFile(csvBase64, columnMapping)
        
        // Assert
        assertFalse(result.success)
        assertEquals(0, result.totalRows)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `processCsvFile should handle duplicate transaction IDs`() = runTest {
        // Arrange
        val csvContent = """
            id,amount,phone
            TX001,500.00,01012345678
            TX001,750.00,01098765432
        """.trimIndent()
        
        val csvBase64 = android.util.Base64.encodeToString(
            csvContent.toByteArray(),
            android.util.Base64.NO_WRAP
        )
        
        val columnMapping = ColumnMapping(
            transactionId = "id",
            amount = "amount",
            phoneNumber = "phone"
        )
        
        // First transaction doesn't exist, second one does
        `when`(pendingTransactionDao.getTransactionById("TX001"))
            .thenReturn(null)
            .thenReturn(mock(PendingTransaction::class.java))
        
        // Act
        val result = bulkTransactionProcessor.processCsvFile(csvBase64, columnMapping)
        
        // Assert
        assertFalse(result.success)
        assertEquals(2, result.totalRows)
        assertEquals(1, result.successCount)
        assertEquals(1, result.failureCount)
    }

    @Test
    fun `processCsvFile should handle invalid amounts`() = runTest {
        // Arrange
        val csvContent = """
            id,amount,phone
            TX001,invalid,01012345678
            TX002,750.00,01098765432
        """.trimIndent()
        
        val csvBase64 = android.util.Base64.encodeToString(
            csvContent.toByteArray(),
            android.util.Base64.NO_WRAP
        )
        
        val columnMapping = ColumnMapping(
            transactionId = "id",
            amount = "amount",
            phoneNumber = "phone"
        )
        
        `when`(pendingTransactionDao.getTransactionById(anyString())).thenReturn(null)
        
        // Act
        val result = bulkTransactionProcessor.processCsvFile(csvBase64, columnMapping)
        
        // Assert
        assertTrue(result.success)
        assertEquals(1, result.totalRows) // Only valid row
        assertEquals(1, result.successCount)
    }

    @Test
    fun `validateCsvFile should validate correct CSV`() = runTest {
        // Arrange
        val csvContent = """
            id,amount,phone
            TX001,500.00,01012345678
            TX002,750.00,01098765432
        """.trimIndent()
        
        val csvBase64 = android.util.Base64.encodeToString(
            csvContent.toByteArray(),
            android.util.Base64.NO_WRAP
        )
        
        val columnMapping = ColumnMapping(
            transactionId = "id",
            amount = "amount",
            phoneNumber = "phone"
        )
        
        // Act
        val result = bulkTransactionProcessor.validateCsvFile(csvBase64, columnMapping)
        
        // Assert
        assertTrue(result.valid)
        assertEquals(2, result.rowCount)
        assertEquals(listOf("id", "amount", "phone"), result.headers)
    }

    @Test
    fun `validateCsvFile should detect missing columns`() = runTest {
        // Arrange
        val csvContent = """
            id,phone
            TX001,01012345678
        """.trimIndent()
        
        val csvBase64 = android.util.Base64.encodeToString(
            csvContent.toByteArray(),
            android.util.Base64.NO_WRAP
        )
        
        val columnMapping = ColumnMapping(
            transactionId = "id",
            amount = "amount", // Missing in CSV
            phoneNumber = "phone"
        )
        
        // Act
        val result = bulkTransactionProcessor.validateCsvFile(csvBase64, columnMapping)
        
        // Assert
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("Missing required columns") })
    }

    @Test
    fun `validateCsvFile should detect empty CSV`() = runTest {
        // Arrange
        val csvContent = ""
        val csvBase64 = android.util.Base64.encodeToString(
            csvContent.toByteArray(),
            android.util.Base64.NO_WRAP
        )
        
        val columnMapping = ColumnMapping(
            transactionId = "id",
            amount = "amount",
            phoneNumber = "phone"
        )
        
        // Act
        val result = bulkTransactionProcessor.validateCsvFile(csvBase64, columnMapping)
        
        // Assert
        assertFalse(result.valid)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `processCsvFile should handle CSV with quoted values`() = runTest {
        // Arrange
        val csvContent = """
            id,amount,phone,note
            TX001,500.00,01012345678,"Test, with comma"
            TX002,750.00,01098765432,"Another note"
        """.trimIndent()
        
        val csvBase64 = android.util.Base64.encodeToString(
            csvContent.toByteArray(),
            android.util.Base64.NO_WRAP
        )
        
        val columnMapping = ColumnMapping(
            transactionId = "id",
            amount = "amount",
            phoneNumber = "phone"
        )
        
        `when`(pendingTransactionDao.getTransactionById(anyString())).thenReturn(null)
        
        // Act
        val result = bulkTransactionProcessor.processCsvFile(csvBase64, columnMapping)
        
        // Assert
        assertTrue(result.success)
        assertEquals(2, result.totalRows)
        assertEquals(2, result.successCount)
    }
}
