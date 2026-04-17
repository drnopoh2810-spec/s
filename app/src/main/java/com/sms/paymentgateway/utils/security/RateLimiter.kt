package com.sms.paymentgateway.utils.security

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rate Limiter per client (IP or API Key)
 * يدعم حدوداً مختلفة لكل نوع endpoint
 */
@Singleton
class RateLimiter @Inject constructor() {

    private val requests = ConcurrentHashMap<String, MutableList<Long>>()
    private val clientLimits = ConcurrentHashMap<String, ClientLimit>()

    // حدود افتراضية حسب نوع العملية
    companion object {
        const val LIMIT_DEFAULT    = 100  // طلبات عامة / دقيقة
        const val LIMIT_BULK       = 10   // bulk operations / دقيقة
        const val LIMIT_AUTH       = 20   // محاولات مصادقة / دقيقة
        const val LIMIT_ANALYTICS  = 60   // analytics / دقيقة
        const val WINDOW_MS        = 60_000L
    }

    fun isAllowed(ip: String, maxRequests: Int = LIMIT_DEFAULT, windowMs: Long = WINDOW_MS): Boolean {
        val key = ip
        val now = System.currentTimeMillis()
        val ipRequests = requests.getOrPut(key) { mutableListOf() }

        synchronized(ipRequests) {
            ipRequests.removeIf { it < now - windowMs }
            return if (ipRequests.size < maxRequests) {
                ipRequests.add(now)
                true
            } else {
                Timber.w("Rate limit exceeded for: $key (${ipRequests.size}/$maxRequests)")
                false
            }
        }
    }

    /** تحقق مع تحديد نوع العملية */
    fun isAllowedForOperation(clientKey: String, operation: RateLimitOperation): Boolean {
        val limit = when (operation) {
            RateLimitOperation.BULK       -> LIMIT_BULK
            RateLimitOperation.AUTH       -> LIMIT_AUTH
            RateLimitOperation.ANALYTICS  -> LIMIT_ANALYTICS
            RateLimitOperation.DEFAULT    -> LIMIT_DEFAULT
        }
        return isAllowed("$clientKey:${operation.name}", limit)
    }

    /** تعيين حد مخصص لعميل */
    fun setClientLimit(apiKey: String, maxRequests: Int, windowMs: Long = WINDOW_MS) {
        clientLimits[apiKey] = ClientLimit(maxRequests, windowMs)
        Timber.i("Custom limit set for client: $maxRequests req/${windowMs}ms")
    }

    /** تحقق بالحد المخصص للعميل إن وُجد */
    fun isAllowedForClient(apiKey: String): Boolean {
        val custom = clientLimits[apiKey]
        return if (custom != null) {
            isAllowed("client:$apiKey", custom.maxRequests, custom.windowMs)
        } else {
            isAllowed("client:$apiKey", LIMIT_DEFAULT)
        }
    }

    fun reset(ip: String) { requests.remove(ip); Timber.d("Rate limit reset for: $ip") }
    fun clearAll() { requests.clear(); clientLimits.clear(); Timber.i("All rate limits cleared") }

    fun getStats(): Map<String, Any> {
        val now = System.currentTimeMillis()
        return mapOf(
            "trackedClients" to requests.size,
            "customLimits" to clientLimits.size,
            "activeRequests" to requests.values.sumOf { list ->
                synchronized(list) { list.count { it > now - WINDOW_MS } }
            }
        )
    }

    fun getRequestCount(ip: String, windowMs: Long = WINDOW_MS): Int {
        val now = System.currentTimeMillis()
        val ipRequests = requests[ip] ?: return 0
        synchronized(ipRequests) {
            ipRequests.removeIf { it < now - windowMs }
            return ipRequests.size
        }
    }
}

enum class RateLimitOperation { DEFAULT, BULK, AUTH, ANALYTICS }
data class ClientLimit(val maxRequests: Int, val windowMs: Long)
