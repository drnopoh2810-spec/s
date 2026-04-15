package com.sms.paymentgateway.services

import com.sms.paymentgateway.data.dao.PendingTransactionDao
import com.sms.paymentgateway.data.dao.SmsLogDao
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CleanupManager @Inject constructor(
    private val pendingTransactionDao: PendingTransactionDao,
    private val smsLogDao: SmsLogDao
) {
    private var cleanupJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startPeriodicCleanup(intervalHours: Long = 1) {
        cleanupJob?.cancel()
        
        cleanupJob = scope.launch {
            while (isActive) {
                try {
                    performCleanup()
                    delay(intervalHours * 3600000) // Convert hours to milliseconds
                } catch (e: Exception) {
                    Timber.e(e, "Error in periodic cleanup")
                    delay(300000) // Wait 5 minutes before retry
                }
            }
        }
        
        Timber.i("Periodic cleanup started (every $intervalHours hours)")
    }

    suspend fun performCleanup() {
        Timber.i("Starting cleanup...")

        try {
            // Expire old pending transactions (older than 48 hours)
            val transactionCutoff = System.currentTimeMillis() - (48 * 3600000)
            pendingTransactionDao.expireOldTransactions(transactionCutoff)
            Timber.d("Expired old transactions")

            // Delete old SMS logs (older than 7 days)
            val smsCutoff = System.currentTimeMillis() - (7 * 24 * 3600000)
            smsLogDao.deleteOldLogs(smsCutoff)
            Timber.d("Deleted old SMS logs")

            Timber.i("Cleanup completed successfully")
        } catch (e: Exception) {
            Timber.e(e, "Cleanup failed")
            throw e
        }
    }

    fun stopCleanup() {
        cleanupJob?.cancel()
        Timber.i("Periodic cleanup stopped")
    }
}
