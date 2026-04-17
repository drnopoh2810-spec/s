package com.sms.paymentgateway.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class Device(
    @PrimaryKey
    val deviceId: String,
    val deviceName: String,
    val phoneNumber: String,
    val isActive: Boolean = true,
    val lastSeen: Long = System.currentTimeMillis(),
    val registeredAt: Long = System.currentTimeMillis(),
    val dailyQuota: Int = 500,          // الحد اليومي للرسائل
    val dailySmsCount: Int = 0,         // عدد الرسائل المرسلة اليوم
    val quotaResetAt: Long = todayMidnight(), // وقت إعادة تعيين العداد
    val priority: Int = 5,              // الأولوية 1-10
    val totalSmsCount: Int = 0,         // إجمالي الرسائل
    val successCount: Int = 0,          // الرسائل الناجحة
    val failureCount: Int = 0,          // الرسائل الفاشلة
    val metadata: String? = null        // بيانات إضافية JSON
)

enum class DeviceStatus { ONLINE, OFFLINE, BUSY, QUOTA_EXCEEDED }

private fun todayMidnight(): Long {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis + 24 * 60 * 60 * 1000 // منتصف الليل القادم
}
