package com.sms.paymentgateway

import com.sms.paymentgateway.utils.security.RateLimiter
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RateLimiterTest {

    private lateinit var rateLimiter: RateLimiter

    @Before
    fun setup() {
        rateLimiter = RateLimiter()
    }

    @Test
    fun `allows requests under limit`() {
        val ip = "192.168.1.1"

        repeat(50) {
            assertTrue(rateLimiter.isAllowed(ip, maxRequests = 100))
        }
    }

    @Test
    fun `blocks requests over limit`() {
        val ip = "192.168.1.1"
        val maxRequests = 10

        // Make max requests
        repeat(maxRequests) {
            assertTrue(rateLimiter.isAllowed(ip, maxRequests = maxRequests))
        }

        // Next request should be blocked
        assertFalse(rateLimiter.isAllowed(ip, maxRequests = maxRequests))
    }

    @Test
    fun `different IPs have separate limits`() {
        val ip1 = "192.168.1.1"
        val ip2 = "192.168.1.2"
        val maxRequests = 5

        // Max out ip1
        repeat(maxRequests) {
            assertTrue(rateLimiter.isAllowed(ip1, maxRequests = maxRequests))
        }
        assertFalse(rateLimiter.isAllowed(ip1, maxRequests = maxRequests))

        // ip2 should still be allowed
        assertTrue(rateLimiter.isAllowed(ip2, maxRequests = maxRequests))
    }

    @Test
    fun `reset clears limit for IP`() {
        val ip = "192.168.1.1"
        val maxRequests = 5

        // Max out requests
        repeat(maxRequests) {
            rateLimiter.isAllowed(ip, maxRequests = maxRequests)
        }
        assertFalse(rateLimiter.isAllowed(ip, maxRequests = maxRequests))

        // Reset and try again
        rateLimiter.reset(ip)
        assertTrue(rateLimiter.isAllowed(ip, maxRequests = maxRequests))
    }

    @Test
    fun `clearAll resets all IPs`() {
        val ip1 = "192.168.1.1"
        val ip2 = "192.168.1.2"
        val maxRequests = 5

        // Max out both IPs
        repeat(maxRequests) {
            rateLimiter.isAllowed(ip1, maxRequests = maxRequests)
            rateLimiter.isAllowed(ip2, maxRequests = maxRequests)
        }

        rateLimiter.clearAll()

        // Both should be allowed again
        assertTrue(rateLimiter.isAllowed(ip1, maxRequests = maxRequests))
        assertTrue(rateLimiter.isAllowed(ip2, maxRequests = maxRequests))
    }

    @Test
    fun `getRequestCount returns correct count`() {
        val ip = "192.168.1.1"

        repeat(5) {
            rateLimiter.isAllowed(ip)
        }

        assertEquals(5, rateLimiter.getRequestCount(ip))
    }

    @Test
    fun `old requests are removed from window`() {
        val ip = "192.168.1.1"
        val windowMs = 100L // 100ms window

        // Make some requests
        repeat(5) {
            rateLimiter.isAllowed(ip, windowMs = windowMs)
        }

        // Wait for window to expire
        Thread.sleep(150)

        // Count should be 0 now
        assertEquals(0, rateLimiter.getRequestCount(ip, windowMs = windowMs))

        // Should be able to make requests again
        assertTrue(rateLimiter.isAllowed(ip, maxRequests = 5, windowMs = windowMs))
    }
}
