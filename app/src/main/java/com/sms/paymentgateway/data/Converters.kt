package com.sms.paymentgateway.data

import androidx.room.TypeConverter
import com.sms.paymentgateway.data.entities.WebhookStatus
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
    
    // WebhookStatus converters
    @TypeConverter
    fun fromWebhookStatus(status: WebhookStatus): String {
        return status.name
    }
    
    @TypeConverter
    fun toWebhookStatus(value: String): WebhookStatus {
        return WebhookStatus.valueOf(value)
    }
}
