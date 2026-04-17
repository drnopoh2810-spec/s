package com.sms.paymentgateway.services

import android.content.Context
import android.util.Log
import com.sms.paymentgateway.data.dao.PendingTransactionDao
import com.sms.paymentgateway.data.entities.TransactionStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدير فحص انتهاء صلاحية المعاملات
 * 
 * يقوم بفحص المعاملات المعلقة دورياً وتحديث حالتها إلى EXPIRED
 * عند انتهاء صلاحيتها
 */
@Singleton
class ExpirationChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pendingTransactionDao: PendingTransactionDao
) {
    private val TAG = "ExpirationChecker"
    private var checkJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private const val CHECK_INTERVAL_MS = 60_000L // فحص كل دقيقة
    }
    
    /**
     * بدء الفحص الدوري
     */
    fun startPeriodicCheck() {
        if (checkJob?.isActive == true) {
            Log.d(TAG, "⏰ Expiration checker already running")
            return
        }
        
        checkJob = scope.launch {
            Log.i(TAG, "🚀 Starting expiration checker")
            
            while (isActive) {
                try {
                    checkAndExpireTransactions()
                    delay(CHECK_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in expiration check", e)
                    delay(CHECK_INTERVAL_MS)
                }
            }
        }
    }
    
    /**
     * إيقاف الفحص الدوري
     */
    fun stopPeriodicCheck() {
        checkJob?.cancel()
        checkJob = null
        Log.i(TAG, "⏹️ Expiration checker stopped")
    }
    
    /**
     * فحص وتحديث المعاملات المنتهية
     */
    suspend fun checkAndExpireTransactions(): Int {
        val currentTime = Date().time
        
        return try {
            // تحديث المعاملات المنتهية في قاعدة البيانات
            pendingTransactionDao.expireOldTransactions(currentTime)
            
            // الحصول على عدد المعاملات المنتهية (للإحصائيات)
            val expiredCount = getExpiredTransactionsCount()
            
            if (expiredCount > 0) {
                Log.i(TAG, "⏰ Expired $expiredCount transaction(s)")
            }
            
            expiredCount
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error expiring transactions", e)
            0
        }
    }
    
    /**
     * الحصول على عدد المعاملات المنتهية
     */
    private suspend fun getExpiredTransactionsCount(): Int {
        return try {
            pendingTransactionDao.getExpiredTransactionsCount()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting expired count", e)
            0
        }
    }
    
    /**
     * فحص فوري (للاختبار أو الاستخدام اليدوي)
     */
    suspend fun checkNow(): Int {
        Log.d(TAG, "🔍 Manual expiration check triggered")
        return checkAndExpireTransactions()
    }
    
    /**
     * الحصول على حالة الفحص
     */
    fun isRunning(): Boolean {
        return checkJob?.isActive == true
    }
    
    /**
     * تنظيف الموارد
     */
    fun cleanup() {
        stopPeriodicCheck()
        scope.cancel()
        Log.i(TAG, "🧹 Expiration checker cleaned up")
    }
}
