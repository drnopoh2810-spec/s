package com.sms.paymentgateway.data.dao

import androidx.room.*
import com.sms.paymentgateway.data.entities.SmsLog
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsLogDao {
    @Query("SELECT * FROM sms_logs ORDER BY receivedAt DESC")
    fun getAllLogs(): Flow<List<SmsLog>>
    
    @Query("SELECT * FROM sms_logs WHERE matched = 0 ORDER BY receivedAt DESC")
    fun getUnmatchedLogs(): Flow<List<SmsLog>>
    
    @Query("SELECT * FROM sms_logs WHERE id = :id")
    suspend fun getLogById(id: Long): SmsLog?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SmsLog): Long
    
    @Update
    suspend fun updateLog(log: SmsLog)
    
    @Query("DELETE FROM sms_logs WHERE receivedAt < :cutoffDate")
    suspend fun deleteOldLogs(cutoffDate: Long)
}
