package com.sms.paymentgateway.data.dao

import androidx.room.*
import com.sms.paymentgateway.data.entities.Device
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {

    @Query("SELECT * FROM devices ORDER BY priority DESC, lastSeen DESC")
    fun getAllDevices(): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE isActive = 1 ORDER BY priority DESC")
    fun getActiveDevices(): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE isActive = 1 ORDER BY priority DESC")
    suspend fun getActiveDevicesList(): List<Device>

    @Query("SELECT * FROM devices WHERE deviceId = :deviceId")
    suspend fun getDeviceById(deviceId: String): Device?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: Device)

    @Update
    suspend fun updateDevice(device: Device)

    @Query("UPDATE devices SET lastSeen = :timestamp WHERE deviceId = :deviceId")
    suspend fun updateLastSeen(deviceId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE devices SET isActive = :active WHERE deviceId = :deviceId")
    suspend fun setDeviceActive(deviceId: String, active: Boolean)

    @Query("UPDATE devices SET dailySmsCount = dailySmsCount + 1, totalSmsCount = totalSmsCount + 1 WHERE deviceId = :deviceId")
    suspend fun incrementSmsCount(deviceId: String)

    @Query("UPDATE devices SET successCount = successCount + 1 WHERE deviceId = :deviceId")
    suspend fun incrementSuccessCount(deviceId: String)

    @Query("UPDATE devices SET failureCount = failureCount + 1 WHERE deviceId = :deviceId")
    suspend fun incrementFailureCount(deviceId: String)

    @Query("UPDATE devices SET dailySmsCount = 0, quotaResetAt = :nextMidnight WHERE quotaResetAt < :now")
    suspend fun resetDailyQuotas(now: Long = System.currentTimeMillis(), nextMidnight: Long)

    @Query("SELECT COUNT(*) FROM devices WHERE isActive = 1")
    suspend fun getActiveDeviceCount(): Int

    @Delete
    suspend fun deleteDevice(device: Device)

    @Query("DELETE FROM devices WHERE deviceId = :deviceId")
    suspend fun deleteDeviceById(deviceId: String)
}
