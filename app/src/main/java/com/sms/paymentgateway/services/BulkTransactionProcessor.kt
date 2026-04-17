package com.sms.paymentgateway.services

import android.util.Base64
import android.util.Log
import com.sms.paymentgateway.data.dao.PendingTransactionDao
import com.sms.paymentgateway.data.entities.PendingTransaction
import com.sms.paymentgateway.data.entities.TransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.StringReader
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * معالج المعاملات الجماعية
 * 
 * يقوم بمعالجة ملفات CSV لإنشاء معاملات متعددة دفعة واحدة
 */
@Singleton
class BulkTransactionProcessor @Inject constructor(
    private val pendingTransactionDao: PendingTransactionDao
) {
    companion object {
        private const val TAG = "BulkTransactionProcessor"
        private const val MAX_BATCH_SIZE = 1000 // الحد الأقصى للمعاملات في دفعة واحدة
    }
    
    /**
     * معالجة ملف CSV
     * 
     * @param csvBase64 ملف CSV مشفر بـ Base64
     * @param columnMapping تعيين الأعمدة
     * @param defaultExpiryMinutes مدة الصلاحية الافتراضية
     * @return نتيجة المعالجة
     */
    suspend fun processCsvFile(
        csvBase64: String,
        columnMapping: ColumnMapping,
        defaultExpiryMinutes: Int = 30
    ): BulkProcessResult = withContext(Dispatchers.IO) {
        try {
            Timber.i("$TAG: Starting bulk transaction processing")
            
            // فك تشفير CSV
            val csvContent = String(Base64.decode(csvBase64, Base64.NO_WRAP))
            
            // تحليل CSV
            val transactions = parseCsv(csvContent, columnMapping, defaultExpiryMinutes)
            
            if (transactions.isEmpty()) {
                return@withContext BulkProcessResult(
                    success = false,
                    totalRows = 0,
                    successCount = 0,
                    failureCount = 0,
                    errors = listOf("No valid transactions found in CSV")
                )
            }
            
            if (transactions.size > MAX_BATCH_SIZE) {
                return@withContext BulkProcessResult(
                    success = false,
                    totalRows = transactions.size,
                    successCount = 0,
                    failureCount = transactions.size,
                    errors = listOf("Batch size exceeds maximum limit of $MAX_BATCH_SIZE")
                )
            }
            
            // إدراج المعاملات في قاعدة البيانات
            val result = insertTransactions(transactions)
            
            Timber.i("$TAG: Bulk processing completed - Success: ${result.successCount}, Failed: ${result.failureCount}")
            
            result
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error processing bulk transactions")
            BulkProcessResult(
                success = false,
                totalRows = 0,
                successCount = 0,
                failureCount = 0,
                errors = listOf("Processing error: ${e.message}")
            )
        }
    }
    
    /**
     * تحليل ملف CSV
     */
    private fun parseCsv(
        csvContent: String,
        columnMapping: ColumnMapping,
        defaultExpiryMinutes: Int
    ): List<PendingTransaction> {
        val transactions = mutableListOf<PendingTransaction>()
        val reader = BufferedReader(StringReader(csvContent))
        
        // قراءة الصف الأول (headers)
        val headers = reader.readLine()?.split(",")?.map { it.trim() } ?: emptyList()
        
        if (headers.isEmpty()) {
            Timber.w("$TAG: CSV file has no headers")
            return emptyList()
        }
        
        // تحديد مواقع الأعمدة
        val idIndex = headers.indexOf(columnMapping.transactionId)
        val amountIndex = headers.indexOf(columnMapping.amount)
        val phoneIndex = headers.indexOf(columnMapping.phoneNumber)
        val walletTypeIndex = headers.indexOf(columnMapping.walletType)
        val expectedTxIdIndex = if (columnMapping.expectedTxId != null) {
            headers.indexOf(columnMapping.expectedTxId)
        } else -1
        val expiryMinutesIndex = if (columnMapping.expiryMinutes != null) {
            headers.indexOf(columnMapping.expiryMinutes)
        } else -1
        
        // التحقق من الأعمدة المطلوبة
        if (idIndex == -1 || amountIndex == -1 || phoneIndex == -1) {
            Timber.e("$TAG: Required columns not found in CSV")
            return emptyList()
        }
        
        // قراءة الصفوف
        var lineNumber = 1
        reader.forEachLine { line ->
            lineNumber++
            
            if (line.isBlank()) return@forEachLine
            
            try {
                val values = parseCsvLine(line)
                
                if (values.size <= maxOf(idIndex, amountIndex, phoneIndex)) {
                    Timber.w("$TAG: Line $lineNumber has insufficient columns")
                    return@forEachLine
                }
                
                val id = values[idIndex].trim()
                val amount = values[amountIndex].trim().toDoubleOrNull()
                val phoneNumber = values[phoneIndex].trim()
                val walletType = if (walletTypeIndex != -1 && walletTypeIndex < values.size) {
                    values[walletTypeIndex].trim()
                } else null
                val expectedTxId = if (expectedTxIdIndex != -1 && expectedTxIdIndex < values.size) {
                    values[expectedTxIdIndex].trim().takeIf { it.isNotEmpty() }
                } else null
                val expiryMinutes = if (expiryMinutesIndex != -1 && expiryMinutesIndex < values.size) {
                    values[expiryMinutesIndex].trim().toIntOrNull() ?: defaultExpiryMinutes
                } else defaultExpiryMinutes
                
                // التحقق من البيانات
                if (id.isEmpty() || amount == null || amount <= 0 || phoneNumber.isEmpty()) {
                    Timber.w("$TAG: Line $lineNumber has invalid data")
                    return@forEachLine
                }
                
                // إنشاء معاملة
                val transaction = PendingTransaction(
                    id = id,
                    amount = amount,
                    phoneNumber = phoneNumber,
                    expectedTxId = expectedTxId,
                    walletType = walletType,
                    status = TransactionStatus.PENDING,
                    createdAt = Date(),
                    expiresAt = Date(System.currentTimeMillis() + expiryMinutes * 60 * 1000)
                )
                
                transactions.add(transaction)
                
            } catch (e: Exception) {
                Timber.w(e, "$TAG: Error parsing line $lineNumber")
            }
        }
        
        return transactions
    }
    
    /**
     * تحليل سطر CSV (يدعم القيم المحاطة بعلامات اقتباس)
     */
    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        var currentValue = StringBuilder()
        var insideQuotes = false
        
        for (char in line) {
            when {
                char == '"' -> insideQuotes = !insideQuotes
                char == ',' && !insideQuotes -> {
                    values.add(currentValue.toString())
                    currentValue = StringBuilder()
                }
                else -> currentValue.append(char)
            }
        }
        
        values.add(currentValue.toString())
        return values
    }
    
    /**
     * إدراج المعاملات في قاعدة البيانات
     */
    private suspend fun insertTransactions(
        transactions: List<PendingTransaction>
    ): BulkProcessResult {
        var successCount = 0
        var failureCount = 0
        val errors = mutableListOf<String>()
        
        transactions.forEach { transaction ->
            try {
                // التحقق من عدم وجود معاملة بنفس الـ ID
                val existing = pendingTransactionDao.getTransactionById(transaction.id)
                if (existing != null) {
                    failureCount++
                    errors.add("Transaction ${transaction.id} already exists")
                } else {
                    pendingTransactionDao.insertTransaction(transaction)
                    successCount++
                }
            } catch (e: Exception) {
                failureCount++
                errors.add("Failed to insert transaction ${transaction.id}: ${e.message}")
                Timber.e(e, "$TAG: Error inserting transaction ${transaction.id}")
            }
        }
        
        return BulkProcessResult(
            success = failureCount == 0,
            totalRows = transactions.size,
            successCount = successCount,
            failureCount = failureCount,
            errors = errors,
            transactions = transactions.map { it.id }
        )
    }
    
    /**
     * التحقق من صحة ملف CSV
     */
    suspend fun validateCsvFile(
        csvBase64: String,
        columnMapping: ColumnMapping
    ): ValidationResult = withContext(Dispatchers.IO) {
        try {
            val csvContent = String(Base64.decode(csvBase64, Base64.NO_WRAP))
            val reader = BufferedReader(StringReader(csvContent))
            
            // قراءة الصف الأول
            val headers = reader.readLine()?.split(",")?.map { it.trim() } ?: emptyList()
            
            if (headers.isEmpty()) {
                return@withContext ValidationResult(
                    valid = false,
                    errors = listOf("CSV file is empty or has no headers")
                )
            }
            
            // التحقق من الأعمدة المطلوبة
            val missingColumns = mutableListOf<String>()
            if (!headers.contains(columnMapping.transactionId)) {
                missingColumns.add(columnMapping.transactionId)
            }
            if (!headers.contains(columnMapping.amount)) {
                missingColumns.add(columnMapping.amount)
            }
            if (!headers.contains(columnMapping.phoneNumber)) {
                missingColumns.add(columnMapping.phoneNumber)
            }
            
            if (missingColumns.isNotEmpty()) {
                return@withContext ValidationResult(
                    valid = false,
                    errors = listOf("Missing required columns: ${missingColumns.joinToString(", ")}")
                )
            }
            
            // عد الصفوف
            var rowCount = 0
            reader.forEachLine { line ->
                if (line.isNotBlank()) rowCount++
            }
            
            if (rowCount == 0) {
                return@withContext ValidationResult(
                    valid = false,
                    errors = listOf("CSV file has no data rows")
                )
            }
            
            if (rowCount > MAX_BATCH_SIZE) {
                return@withContext ValidationResult(
                    valid = false,
                    errors = listOf("CSV file exceeds maximum batch size of $MAX_BATCH_SIZE rows")
                )
            }
            
            ValidationResult(
                valid = true,
                rowCount = rowCount,
                headers = headers
            )
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error validating CSV file")
            ValidationResult(
                valid = false,
                errors = listOf("Validation error: ${e.message}")
            )
        }
    }
}

/**
 * تعيين الأعمدة
 */
data class ColumnMapping(
    val transactionId: String,
    val amount: String,
    val phoneNumber: String,
    val walletType: String? = null,
    val expectedTxId: String? = null,
    val expiryMinutes: String? = null
)

/**
 * نتيجة المعالجة الجماعية
 */
data class BulkProcessResult(
    val success: Boolean,
    val totalRows: Int,
    val successCount: Int,
    val failureCount: Int,
    val errors: List<String> = emptyList(),
    val transactions: List<String> = emptyList()
)

/**
 * نتيجة التحقق
 */
data class ValidationResult(
    val valid: Boolean,
    val rowCount: Int = 0,
    val headers: List<String> = emptyList(),
    val errors: List<String> = emptyList()
)
