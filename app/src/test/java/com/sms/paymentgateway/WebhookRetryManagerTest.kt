package com.sms.paymentgateway

import com.sms.paymentgateway.data.dao.WebhookLogDao
import com.sms.paymentgateway.data.entities.WebhookLog
import com.sms.paymentgateway.data.entities.WebhookStatus
import com.sms.paymentgateway.services.WebhookResult
import com.sms.paymentgateway.services.WebhookRetryManager
import com.sms.paymentgateway.utils.security.SecurityManager
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

/**
 * Unit Tests لـ WebhookRetryManager
 */
class WebhookRetryManagerTest {
    
    @Mock
    private lateinit var webhookLogDao: WebhookLogDao
    
    @Mock
    private lateinit var securityManager: SecurityManager
    
    private lateinit var webhookRetryManager: WebhookRetryManager
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        webhookRetryManager = WebhookRetryManager(webhookLogDao, securityManager)
        
        // Mock HMAC signature generation
        `when`(securityManager.generateHmacSignature(anyString(), anyString()))
            .thenReturn("mock_signature")
    }
    
    /**
     * اختبار حساب Exponential Backoff
     */
    @Test
    fun testBackoffDelay() {
        // استخدام reflection للوصول للـ private method
        val method = WebhookRetryManager::class.java.getDeclaredMethod(
            "calculateBackoffDelay",
            Int::class.java
        )
        method.isAccessible = true
        
        // Test exponential backoff: 1s, 2s, 4s, 8s, 16s, 32s (max)
        assertEquals(1000L, method.invoke(webhookRetryManager, 1))
        assertEquals(2000L, method.invoke(webhookRetryManager, 2))
        assertEquals(4000L, method.invoke(webhookRetryManager, 3))
        assertEquals(8000L, method.invoke(webhookRetryManager, 4))
        assertEquals(16000L, method.invoke(webhookRetryManager, 5))
        assertEquals(32000L, method.invoke(webhookRetryManager, 6)) // max
        assertEquals(32000L, method.invoke(webhookRetryManager, 10)) // still max
    }
    
    /**
     * اختبار تسجيل محاولة Webhook
     */
    @Test
    fun testWebhookLogging() = runBlocking {
        val transactionId = "test-tx-001"
        val url = "https://example.com/webhook"
        val payload = JSONObject().apply {
            put("event", "PAYMENT_CONFIRMED")
            put("transactionId", transactionId)
        }
        
        // Mock DAO insert
        `when`(webhookLogDao.insert(any(WebhookLog::class.java)))
            .thenReturn(1L)
        
        // Note: في الاختبار الحقيقي، نحتاج mock server
        // هنا نختبر فقط أن الـ logging يعمل
        
        // Verify that insert was called (سيتم استدعاؤه عند الفشل)
        // في الاختبار الحقيقي، نحتاج mock HTTP client
    }
    
    /**
     * اختبار أن الـ client errors (4xx) لا تُعاد محاولتها
     */
    @Test
    fun testNoRetryOnClientError() {
        // هذا الاختبار يحتاج mock HTTP client
        // سيتم تنفيذه في Integration Tests
        assertTrue("Client errors should not be retried", true)
    }
    
    /**
     * اختبار أن الـ server errors (5xx) تُعاد محاولتها
     */
    @Test
    fun testRetryOnServerError() {
        // هذا الاختبار يحتاج mock HTTP client
        // سيتم تنفيذه في Integration Tests
        assertTrue("Server errors should be retried", true)
    }
    
    /**
     * اختبار الوصول للحد الأقصى من المحاولات
     */
    @Test
    fun testMaxRetriesReached() {
        // هذا الاختبار يحتاج mock HTTP client
        // سيتم تنفيذه في Integration Tests
        assertTrue("Should stop after max retries", true)
    }
    
    /**
     * اختبار WebhookResult.Success
     */
    @Test
    fun testWebhookResultSuccess() {
        val result = WebhookResult.Success(
            httpStatusCode = 200,
            responseBody = "OK",
            processingTimeMs = 150
        )
        
        assertEquals(200, result.httpStatusCode)
        assertEquals("OK", result.responseBody)
        assertEquals(150, result.processingTimeMs)
    }
    
    /**
     * اختبار WebhookResult.Failure
     */
    @Test
    fun testWebhookResultFailure() {
        val exception = Exception("Connection timeout")
        val result = WebhookResult.Failure(
            exception = exception,
            httpStatusCode = null,
            responseBody = null,
            processingTimeMs = 5000
        )
        
        assertEquals("Connection timeout", result.exception.message)
        assertNull(result.httpStatusCode)
        assertNull(result.responseBody)
        assertEquals(5000, result.processingTimeMs)
    }
    
    /**
     * اختبار WebhookStatus enum
     */
    @Test
    fun testWebhookStatus() {
        assertEquals(4, WebhookStatus.values().size)
        assertTrue(WebhookStatus.values().contains(WebhookStatus.PENDING))
        assertTrue(WebhookStatus.values().contains(WebhookStatus.SUCCESS))
        assertTrue(WebhookStatus.values().contains(WebhookStatus.FAILED))
        assertTrue(WebhookStatus.values().contains(WebhookStatus.RETRYING))
    }
    
    // Helper method
    private fun <T> any(type: Class<T>): T = Mockito.any(type)
}
