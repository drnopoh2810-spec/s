package com.sms.paymentgateway.services

import android.util.Log
import com.sms.paymentgateway.data.dao.DeviceDao
import com.sms.paymentgateway.data.entities.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * موزع الحمل بين الأجهزة المتعددة
 *
 * خوارزمية الاختيار:
 * 1. تصفية الأجهزة النشطة التي لم تتجاوز حصتها اليومية
 * 2. ترتيب حسب نسبة الاستخدام (الأقل استخداماً أولاً)
 * 3. عند التساوي، الأولوية الأعلى تُقدَّم
 */
@Singleton
class DeviceLoadBalancer @Inject constructor(
    private val deviceDao: DeviceDao
) {
    companion object {
        private const val TAG = "DeviceLoadBalancer"
        private const val OFFLINE_THRESHOLD_MS = 5 * 60 * 1000L // 5 دقائق
    }

    /**
     * اختيار أفضل جهاز لإرسال رسالة
     */
    suspend fun selectDevice(): Device? = withContext(Dispatchers.IO) {
        try {
            // إعادة تعيين الحصص اليومية المنتهية
            resetExpiredQuotas()

            val activeDevices = deviceDao.getActiveDevicesList()

            if (activeDevices.isEmpty()) {
                Timber.w("$TAG: No active devices available")
                return@withContext null
            }

            val now = System.currentTimeMillis()

            // تصفية: نشط + لم يتجاوز الحصة + ظهر مؤخراً
            val eligible = activeDevices.filter { device ->
                device.dailySmsCount < device.dailyQuota &&
                (now - device.lastSeen) < OFFLINE_THRESHOLD_MS
            }

            if (eligible.isEmpty()) {
                Timber.w("$TAG: No eligible devices (all offline or quota exceeded)")
                return@withContext null
            }

            // الترتيب: الأقل استخداماً نسبياً → الأعلى أولوية
            val selected = eligible.minByOrNull { device ->
                val usageRatio = device.dailySmsCount.toDouble() / device.dailyQuota
                usageRatio - (device.priority / 100.0) // طرح الأولوية لتفضيل الأعلى
            }

            selected?.let {
                Timber.d("$TAG: Selected device ${it.deviceId} (${it.dailySmsCount}/${it.dailyQuota})")
            }

            selected
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error selecting device")
            null
        }
    }

    /**
     * تسجيل نجاح إرسال رسالة
     */
    suspend fun recordSuccess(deviceId: String) = withContext(Dispatchers.IO) {
        deviceDao.incrementSmsCount(deviceId)
        deviceDao.incrementSuccessCount(deviceId)
        deviceDao.updateLastSeen(deviceId)
        Timber.d("$TAG: Recorded success for device $deviceId")
    }

    /**
     * تسجيل فشل إرسال رسالة
     */
    suspend fun recordFailure(deviceId: String) = withContext(Dispatchers.IO) {
        deviceDao.incrementFailureCount(deviceId)
        Timber.w("$TAG: Recorded failure for device $deviceId")
    }

    /**
     * تحديث حالة الجهاز (heartbeat)
     */
    suspend fun heartbeat(deviceId: String) = withContext(Dispatchers.IO) {
        deviceDao.updateLastSeen(deviceId)
    }

    /**
     * الحصول على حالة جميع الأجهزة
     */
    suspend fun getDevicesStatus(): List<DeviceStatusInfo> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        deviceDao.getActiveDevicesList().map { device ->
            val isOnline = (now - device.lastSeen) < OFFLINE_THRESHOLD_MS
            val quotaUsed = if (device.dailyQuota > 0)
                device.dailySmsCount * 100 / device.dailyQuota else 0
            val successRate = if (device.totalSmsCount > 0)
                device.successCount * 100 / device.totalSmsCount else 100

            DeviceStatusInfo(
                deviceId = device.deviceId,
                deviceName = device.deviceName,
                isOnline = isOnline,
                isActive = device.isActive,
                dailySmsCount = device.dailySmsCount,
                dailyQuota = device.dailyQuota,
                quotaUsedPercent = quotaUsed,
                priority = device.priority,
                successRate = successRate,
                lastSeenMs = now - device.lastSeen
            )
        }
    }

    private suspend fun resetExpiredQuotas() {
        val now = System.currentTimeMillis()
        val nextMidnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        deviceDao.resetDailyQuotas(now, nextMidnight)
    }
}

data class DeviceStatusInfo(
    val deviceId: String,
    val deviceName: String,
    val isOnline: Boolean,
    val isActive: Boolean,
    val dailySmsCount: Int,
    val dailyQuota: Int,
    val quotaUsedPercent: Int,
    val priority: Int,
    val successRate: Int,
    val lastSeenMs: Long
)
