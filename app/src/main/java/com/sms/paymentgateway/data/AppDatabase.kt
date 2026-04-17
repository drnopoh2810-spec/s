package com.sms.paymentgateway.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sms.paymentgateway.data.dao.DeviceDao
import com.sms.paymentgateway.data.dao.PendingTransactionDao
import com.sms.paymentgateway.data.dao.SmsLogDao
import com.sms.paymentgateway.data.dao.SmsTemplateDao
import com.sms.paymentgateway.data.dao.WebhookLogDao
import com.sms.paymentgateway.data.entities.Device
import com.sms.paymentgateway.data.entities.PendingTransaction
import com.sms.paymentgateway.data.entities.SmsLog
import com.sms.paymentgateway.data.entities.SmsTemplate
import com.sms.paymentgateway.data.entities.WebhookLog

@Database(
    entities = [
        SmsLog::class,
        PendingTransaction::class,
        WebhookLog::class,
        Device::class,
        SmsTemplate::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun smsLogDao(): SmsLogDao
    abstract fun pendingTransactionDao(): PendingTransactionDao
    abstract fun webhookLogDao(): WebhookLogDao
    abstract fun deviceDao(): DeviceDao
    abstract fun smsTemplateDao(): SmsTemplateDao
    
    companion object {
        /**
         * Migration من الإصدار 1 إلى 2
         * إضافة جدول webhook_logs
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // إنشاء جدول webhook_logs
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS webhook_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        transactionId TEXT NOT NULL,
                        webhookUrl TEXT NOT NULL,
                        attempt INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        httpStatusCode INTEGER,
                        errorMessage TEXT,
                        requestPayload TEXT NOT NULL,
                        responseBody TEXT,
                        timestamp INTEGER NOT NULL,
                        processingTimeMs INTEGER
                    )
                """.trimIndent())
                
                // إنشاء فهرس على transactionId لتسريع البحث
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_webhook_logs_transactionId 
                    ON webhook_logs(transactionId)
                """.trimIndent())
                
                // إنشاء فهرس على status
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_webhook_logs_status 
                    ON webhook_logs(status)
                """.trimIndent())
                
                // إنشاء فهرس على timestamp
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_webhook_logs_timestamp 
                    ON webhook_logs(timestamp)
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS devices (
                        deviceId TEXT PRIMARY KEY NOT NULL,
                        deviceName TEXT NOT NULL,
                        phoneNumber TEXT NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        lastSeen INTEGER NOT NULL,
                        registeredAt INTEGER NOT NULL,
                        dailyQuota INTEGER NOT NULL DEFAULT 500,
                        dailySmsCount INTEGER NOT NULL DEFAULT 0,
                        quotaResetAt INTEGER NOT NULL,
                        priority INTEGER NOT NULL DEFAULT 5,
                        totalSmsCount INTEGER NOT NULL DEFAULT 0,
                        successCount INTEGER NOT NULL DEFAULT 0,
                        failureCount INTEGER NOT NULL DEFAULT 0,
                        metadata TEXT
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_devices_isActive ON devices(isActive)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_devices_priority ON devices(priority)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS sms_templates (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        template TEXT NOT NULL,
                        description TEXT,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        isDefault INTEGER NOT NULL DEFAULT 0,
                        usageCount INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                // إدراج قالب افتراضي
                val now = System.currentTimeMillis()
                database.execSQL("""
                    INSERT INTO sms_templates (name, template, description, isActive, isDefault, usageCount, createdAt, updatedAt)
                    VALUES ('القالب الافتراضي', 'تم استلام {amount} جنيه من {phone} عبر {wallet} - رقم العملية: {tx_id}',
                    'القالب الافتراضي للإشعارات', 1, 1, 0, $now, $now)
                """.trimIndent())
            }
        }
    }
}
