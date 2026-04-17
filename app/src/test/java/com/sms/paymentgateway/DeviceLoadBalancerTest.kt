package com.sms.paymentgateway

import com.sms.paymentgateway.data.dao.DeviceDao
import com.sms.paymentgateway.data.entities.Device
import com.sms.paymentgateway.services.DeviceLoadBalancer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceLoadBalancerTest {

    @Mock private lateinit var deviceDao: DeviceDao
    private lateinit var loadBalancer: DeviceLoadBalancer

    private fun makeDevice(
        id: String,
        priority: Int = 5,
        dailyCount: Int = 0,
        quota: Int = 500,
        lastSeen: Long = System.currentTimeMillis()
    ) = Device(
        deviceId = id,
        deviceName = "Device $id",
        phoneNumber = "0100000000$id",
        isActive = true,
        lastSeen = lastSeen,
        dailyQuota = quota,
        dailySmsCount = dailyCount,
        priority = priority
    )

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        loadBalancer = DeviceLoadBalancer(deviceDao)
    }

    @Test
    fun `selectDevice returns null when no active devices`() = runTest {
        `when`(deviceDao.getActiveDevicesList()).thenReturn(emptyList())
        assertNull(loadBalancer.selectDevice())
    }

    @Test
    fun `selectDevice returns single available device`() = runTest {
        val device = makeDevice("D1")
        `when`(deviceDao.getActiveDevicesList()).thenReturn(listOf(device))
        assertNotNull(loadBalancer.selectDevice())
    }

    @Test
    fun `selectDevice skips device that exceeded quota`() = runTest {
        val full = makeDevice("D1", dailyCount = 500, quota = 500)
        val ok   = makeDevice("D2", dailyCount = 10,  quota = 500)
        `when`(deviceDao.getActiveDevicesList()).thenReturn(listOf(full, ok))
        val selected = loadBalancer.selectDevice()
        assertEquals("D2", selected?.deviceId)
    }

    @Test
    fun `selectDevice skips offline device`() = runTest {
        val offline = makeDevice("D1", lastSeen = System.currentTimeMillis() - 10 * 60 * 1000)
        val online  = makeDevice("D2")
        `when`(deviceDao.getActiveDevicesList()).thenReturn(listOf(offline, online))
        val selected = loadBalancer.selectDevice()
        assertEquals("D2", selected?.deviceId)
    }

    @Test
    fun `selectDevice prefers less-used device`() = runTest {
        val busy = makeDevice("D1", dailyCount = 400, quota = 500)
        val free = makeDevice("D2", dailyCount = 10,  quota = 500)
        `when`(deviceDao.getActiveDevicesList()).thenReturn(listOf(busy, free))
        val selected = loadBalancer.selectDevice()
        assertEquals("D2", selected?.deviceId)
    }

    @Test
    fun `selectDevice returns null when all offline`() = runTest {
        val old = System.currentTimeMillis() - 10 * 60 * 1000
        val d1 = makeDevice("D1", lastSeen = old)
        val d2 = makeDevice("D2", lastSeen = old)
        `when`(deviceDao.getActiveDevicesList()).thenReturn(listOf(d1, d2))
        assertNull(loadBalancer.selectDevice())
    }

    @Test
    fun `recordSuccess increments counts`() = runTest {
        loadBalancer.recordSuccess("D1")
        verify(deviceDao).incrementSmsCount("D1")
        verify(deviceDao).incrementSuccessCount("D1")
        verify(deviceDao).updateLastSeen("D1")
    }

    @Test
    fun `recordFailure increments failure count`() = runTest {
        loadBalancer.recordFailure("D1")
        verify(deviceDao).incrementFailureCount("D1")
    }

    @Test
    fun `getDevicesStatus returns status for all devices`() = runTest {
        val devices = listOf(makeDevice("D1"), makeDevice("D2"))
        `when`(deviceDao.getActiveDevicesList()).thenReturn(devices)
        val status = loadBalancer.getDevicesStatus()
        assertEquals(2, status.size)
    }
}
