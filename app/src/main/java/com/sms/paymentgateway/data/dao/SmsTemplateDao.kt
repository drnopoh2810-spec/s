package com.sms.paymentgateway.data.dao

import androidx.room.*
import com.sms.paymentgateway.data.entities.SmsTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsTemplateDao {

    @Query("SELECT * FROM sms_templates ORDER BY isDefault DESC, usageCount DESC")
    fun getAllTemplates(): Flow<List<SmsTemplate>>

    @Query("SELECT * FROM sms_templates WHERE isActive = 1 ORDER BY isDefault DESC, usageCount DESC")
    suspend fun getActiveTemplates(): List<SmsTemplate>

    @Query("SELECT * FROM sms_templates WHERE isDefault = 1 AND isActive = 1 LIMIT 1")
    suspend fun getDefaultTemplate(): SmsTemplate?

    @Query("SELECT * FROM sms_templates WHERE id = :id")
    suspend fun getTemplateById(id: Long): SmsTemplate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: SmsTemplate): Long

    @Update
    suspend fun updateTemplate(template: SmsTemplate)

    @Query("UPDATE sms_templates SET usageCount = usageCount + 1, updatedAt = :now WHERE id = :id")
    suspend fun incrementUsage(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE sms_templates SET isDefault = 0")
    suspend fun clearDefaultFlag()

    @Query("UPDATE sms_templates SET isDefault = 1 WHERE id = :id")
    suspend fun setAsDefault(id: Long)

    @Delete
    suspend fun deleteTemplate(template: SmsTemplate)

    @Query("DELETE FROM sms_templates WHERE id = :id")
    suspend fun deleteTemplateById(id: Long)
}
