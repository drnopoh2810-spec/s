package com.sms.paymentgateway.data.dao

import androidx.room.*
import com.sms.paymentgateway.data.entities.WebhookLog
import com.sms.paymentgateway.data.entities.WebhookStatus
import kotlinx.coroutines.flow.Flow

/**
 * DAO للوصول إلى سجلات Webhook
 */
@Dao
interface WebhookLogDao {
    
    /**
     * إضافة سجل جديد
     */
    @Insert
    suspend fun insert(log: WebhookLog): Long
    
    /**
     * إضافة عدة سجلات
     */
    @Insert
    suspend fun insertAll(logs: List<WebhookLog>)
    
    /**
     * الحصول على سجلات معاملة محددة
     */
    @Query("SELECT * FROM webhook_logs WHERE transactionId = :transactionId ORDER BY timestamp DESC")
    fun getLogsByTransaction(transactionId: String): Flow<List<WebhookLog>>
    
    /**
     * الحصول على سجلات حسب الحالة
     */
    @Query("SELECT * FROM webhook_logs WHERE status = :status ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLogsByStatus(status: WebhookStatus, limit: Int = 100): List<WebhookLog>
    
    /**
     * الحصول على جميع السجلات
     */
    @Query("SELECT * FROM webhook_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getAllLogs(limit: Int = 100): Flow<List<WebhookLog>>
    
    /**
     * الحصول على السجلات في نطاق زمني
     */
    @Query("""
        SELECT * FROM webhook_logs 
        WHERE timestamp BETWEEN :startTime AND :endTime 
        ORDER BY timestamp DESC
    """)
    suspend fun getLogsByTimeRange(startTime: Long, endTime: Long): List<WebhookLog>
    
    /**
     * عد السجلات حسب الحالة
     */
    @Query("SELECT COUNT(*) FROM webhook_logs WHERE status = :status")
    suspend fun countByStatus(status: WebhookStatus): Int
    
    /**
     * حذف السجلات القديمة
     */
    @Query("DELETE FROM webhook_logs WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldLogs(beforeTimestamp: Long): Int
    
    /**
     * متوسط وقت المعالجة
     */
    @Query("""
        SELECT AVG(processingTimeMs) FROM webhook_logs 
        WHERE status = :status AND processingTimeMs IS NOT NULL
    """)
    suspend fun getAverageProcessingTime(status: WebhookStatus = WebhookStatus.SUCCESS): Double?
    
    /**
     * الحصول على آخر محاولة لمعاملة
     */
    @Query("""
        SELECT * FROM webhook_logs 
        WHERE transactionId = :transactionId 
        ORDER BY timestamp DESC 
        LIMIT 1
    """)
    suspend fun getLastAttempt(transactionId: String): WebhookLog?
    
    /**
     * عد المحاولات الفاشلة لمعاملة
     */
    @Query("""
        SELECT COUNT(*) FROM webhook_logs 
        WHERE transactionId = :transactionId 
        AND status IN ('FAILED', 'RETRYING')
    """)
    suspend fun countFailedAttempts(transactionId: String): Int
}
