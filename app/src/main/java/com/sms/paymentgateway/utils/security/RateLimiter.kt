package com.sms.paymentgateway.utils.security

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RateLimiter @Inject constructor() {

    private val requests = ConcurrentHashMap<String, MutableList<Long>>()

    /**
     * Check if a request from the given IP is allowed
     * @param ip IP address
     * @param maxRequests Maximum requests allowed in the time window
     * @param windowMs Time window in milliseconds (default: 1 minute)
     * @return true if allowed, false if rate limit exceeded
     */
    fun isAllowed(
        ip: String,
        maxRequests: Int = 100,
        windowMs: Long = 60000
    ): Boolean {
        val now = System.currentTimeMillis()
        val ipRequests = requests.getOrPut(ip) { mutableListOf() }

        synchronized(ipRequests) {
            // Remove old requests outside the time window
            ipRequests.removeIf { it < now - windowMs }

            return if (ipRequests.size < maxRequests) {
                ipRequests.add(now)
                true
            } else {
                Timber.w("Rate limit exceeded for IP: $ip (${ipRequests.size}/$maxRequests)")
                false
            }
        }
    }

    /**
     * Reset rate limit for a specific IP
     */
    fun reset(ip: String) {
        requests.remove(ip)
        Timber.d("Rate limit reset for IP: $ip")
    }

    /**
     * Clear all rate limit data
     */
    fun clearAll() {
        requests.clear()
        Timber.i("All rate limits cleared")
    }

    /**
     * Get current request count for an IP
     */
    fun getRequestCount(ip: String, windowMs: Long = 60000): Int {
        val now = System.currentTimeMillis()
        val ipRequests = requests[ip] ?: return 0

        synchronized(ipRequests) {
            ipRequests.removeIf { it < now - windowMs }
            return ipRequests.size
        }
    }
}
