package com.sms.paymentgateway.utils.security

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RateLimiter @Inject constructor() {

    private val requests = ConcurrentHashMap<String, MutableList<Long>>()

    fun isAllowed(ip: String, maxRequests: Int = 100, windowMs: Long = 60_000): Boolean {
        val now = System.currentTimeMillis()
        val ipRequests = requests.getOrPut(ip) { mutableListOf() }
        synchronized(ipRequests) {
            ipRequests.removeIf { it < now - windowMs }
            return if (ipRequests.size < maxRequests) {
                ipRequests.add(now)
                true
            } else {
                Timber.w("تم تجاوز حد الطلبات لـ IP: $ip")
                false
            }
        }
    }

    fun reset(ip: String) { requests.remove(ip) }
    fun clearAll() { requests.clear() }

    fun getRequestCount(ip: String, windowMs: Long = 60_000): Int {
        val now = System.currentTimeMillis()
        val ipRequests = requests[ip] ?: return 0
        synchronized(ipRequests) {
            ipRequests.removeIf { it < now - windowMs }
            return ipRequests.size
        }
    }
}
