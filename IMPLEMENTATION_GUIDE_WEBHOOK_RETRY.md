# دليل التنفيذ: نظام إعادة محاولة Webhook

## 📋 نظرة عامة

هذا الدليل يشرح خطوة بخطوة كيفية تنفيذ نظام إعادة محاولة Webhook ذكي مع Exponential Backoff.

---

## 🎯 الأهداف

1. ضمان وصول إشعارات الدفع حتى في حالة فشل الخادم المستقبل
2. تطبيق Exponential Backoff لتجنب إغراق الخادم
3. تسجيل جميع محاولات الإرسال للمراجعة
4. إشعار المستخدم بالفشل النهائي

---

## 📁 الملفات المطلوب إنشاؤها/تعديلها

### 1. إنشاء Entity للسجلات

**الملف**: `app/src/main/java/com/sms/paymentgateway/data/entities/WebhookLog.kt`

```kotlin
package com.sms.paymentgateway.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "webhook_logs")
data class WebhookLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val transactionId: String,
    val webhookUrl: String,
    val attempt: Int,
    val status: WebhookStatus,
    val httpStatusCode: Int?,
    val errorMessage: String?,
    val requestPayload: String,
    val responseBody: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val processingTimeMs: Long?
)

enum class WebhookStatus {
    PENDING,
    SUCCESS,
    FAILED,
    RETRYING
}
```

---

### 2. إنشاء DAO

**الملف**: `app/src/main/java/com/sms/paymentgateway/data/dao/WebhookLogDao.kt`

```kotlin
package com.sms.paymentgateway.data.dao

import androidx.room.*
import com.sms.paymentgateway.data.entities.WebhookLog
import com.sms.paymentgateway.data.entities.WebhookStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface WebhookLogDao {
    
    @Insert
    suspend fun insert(log: WebhookLog): Long
    
    @Query("SELECT * FROM webhook_logs WHERE transactionId = :transactionId ORDER BY timestamp DESC")
    fun getLogsByTransaction(transactionId: String): Flow<List<WebhookLog>>
    
    @Query("SELECT * FROM webhook_logs WHERE status = :status ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLogsByStatus(status: WebhookStatus, limit: Int = 100): List<WebhookLog>
    
    @Query("SELECT * FROM webhook_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getAllLogs(limit: Int = 100): Flow<List<WebhookLog>>
    
    @Query("""
        SELECT * FROM webhook_logs 
        WHERE timestamp BETWEEN :startTime AND :endTime 
        ORDER BY timestamp DESC
    """)
    suspend fun getLogsByTimeRange(startTime: Long, endTime: Long): List<WebhookLog>
    
    @Query("SELECT COUNT(*) FROM webhook_logs WHERE status = :status")
    suspend fun countByStatus(status: WebhookStatus): Int
    
    @Query("DELETE FROM webhook_logs WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldLogs(beforeTimestamp: Long): Int
    
    @Query("""
        SELECT AVG(processingTimeMs) FROM webhook_logs 
        WHERE status = :status AND processingTimeMs IS NOT NULL
    """)
    suspend fun getAverageProcessingTime(status: WebhookStatus = WebhookStatus.SUCCESS): Double?
}
```

---

### 3. تحديث AppDatabase

**الملف**: `app/src/main/java/com/sms/paymentgateway/data/AppDatabase.kt`

```kotlin
@Database(
    entities = [
        PendingTransaction::class,
        SmsLog::class,
        WebhookLog::class  // إضافة هذا السطر
    ],
    version = 2,  // زيادة رقم الإصدار
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pendingTransactionDao(): PendingTransactionDao
    abstract fun smsLogDao(): SmsLogDao
    abstract fun webhookLogDao(): WebhookLogDao  // إضافة هذا السطر
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "payment_gateway_database"
                )
                .addMigrations(MIGRATION_1_2)  // إضافة Migration
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        // إضافة Migration
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
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
                """)
                
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_webhook_logs_transactionId 
                    ON webhook_logs(transactionId)
                """)
                
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_webhook_logs_status 
                    ON webhook_logs(status)
                """)
            }
        }
    }
}
```

---

### 4. إنشاء WebhookRetryManager

**الملف**: `app/src/main/java/com/sms/paymentgateway/services/WebhookRetryManager.kt`

```kotlin
package com.sms.paymentgateway.services

import android.util.Log
import com.sms.paymentgateway.data.AppDatabase
import com.sms.paymentgateway.data.entities.WebhookLog
import com.sms.paymentgateway.data.entities.WebhookStatus
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebhookRetryManager @Inject constructor(
    private val database: AppDatabase,
    private val securityManager: SecurityManager
) {
    companion object {
        private const val TAG = "WebhookRetryManager"
        private const val MAX_RETRIES = 5
        private const val BASE_DELAY_MS = 1000L // 1 second
        private const val MAX_DELAY_MS = 32000L // 32 seconds
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    /**
     * إرسال Webhook مع إعادة المحاولة التلقائية
     */
    suspend fun sendWithRetry(
        url: String,
        payload: JSONObject,
        transactionId: String,
        apiKey: String
    ): WebhookResult {
        var attempt = 0
        var lastError: Exception? = null
        
        while (attempt < MAX_RETRIES) {
            attempt++
            
            val result = sendWebhook(
                url = url,
                payload = payload,
                transactionId = transactionId,
                apiKey = apiKey,
                attempt = attempt
            )
            
            when (result) {
                is WebhookResult.Success -> {
                    Log.i(TAG, "Webhook sent successfully for transaction $transactionId on attempt $attempt")
                    return result
                }
                is WebhookResult.Failure -> {
                    lastError = result.exception
                    
                    // Log the failure
                    logWebhookAttempt(
                        transactionId = transactionId,
                        url = url,
                        attempt = attempt,
                        status = if (attempt < MAX_RETRIES) WebhookStatus.RETRYING else WebhookStatus.FAILED,
                        httpStatusCode = result.httpStatusCode,
                        errorMessage = result.exception.message,
                        requestPayload = payload.toString(),
                        responseBody = result.responseBody,
                        processingTimeMs = result.processingTimeMs
                    )
                    
                    // إذا كان الخطأ 4xx (client error)، لا نعيد المحاولة
                    if (result.httpStatusCode in 400..499) {
                        Log.w(TAG, "Client error (${result.httpStatusCode}), not retrying")
                        return result
                    }
                    
                    // إذا لم نصل للحد الأقصى، ننتظر ثم نعيد المحاولة
                    if (attempt < MAX_RETRIES) {
                        val delayMs = calculateBackoffDelay(attempt)
                        Log.i(TAG, "Retrying webhook for transaction $transactionId in ${delayMs}ms (attempt $attempt/$MAX_RETRIES)")
                        delay(delayMs)
                    }
                }
            }
        }
        
        // جميع المحاولات فشلت
        Log.e(TAG, "All webhook attempts failed for transaction $transactionId", lastError)
        return WebhookResult.Failure(
            exception = lastError ?: Exception("Unknown error"),
            httpStatusCode = null,
            responseBody = null,
            processingTimeMs = 0
        )
    }
    
    /**
     * إرسال Webhook مرة واحدة
     */
    private suspend fun sendWebhook(
        url: String,
        payload: JSONObject,
        transactionId: String,
        apiKey: String,
        attempt: Int
    ): WebhookResult {
        val startTime = System.currentTimeMillis()
        
        try {
            // إضافة timestamp و signature
            val timestamp = System.currentTimeMillis() / 1000
            val signature = securityManager.generateHmacSignature(
                data = payload.toString(),
                secret = apiKey
            )
            
            // بناء الطلب
            val requestBody = payload.toString()
                .toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Webhook-Signature", signature)
                .addHeader("X-Webhook-Timestamp", timestamp.toString())
                .addHeader("X-Transaction-Id", transactionId)
                .addHeader("X-Attempt", attempt.toString())
                .build()
            
            // إرسال الطلب
            val response = client.newCall(request).execute()
            val processingTime = System.currentTimeMillis() - startTime
            val responseBody = response.body?.string()
            
            return if (response.isSuccessful) {
                // نجح الإرسال
                logWebhookAttempt(
                    transactionId = transactionId,
                    url = url,
                    attempt = attempt,
                    status = WebhookStatus.SUCCESS,
                    httpStatusCode = response.code,
                    errorMessage = null,
                    requestPayload = payload.toString(),
                    responseBody = responseBody,
                    processingTimeMs = processingTime
                )
                
                WebhookResult.Success(
                    httpStatusCode = response.code,
                    responseBody = responseBody,
                    processingTimeMs = processingTime
                )
            } else {
                // فشل الإرسال (status code غير ناجح)
                WebhookResult.Failure(
                    exception = Exception("HTTP ${response.code}: ${response.message}"),
                    httpStatusCode = response.code,
                    responseBody = responseBody,
                    processingTimeMs = processingTime
                )
            }
            
        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "Error sending webhook for transaction $transactionId", e)
            
            return WebhookResult.Failure(
                exception = e,
                httpStatusCode = null,
                responseBody = null,
                processingTimeMs = processingTime
            )
        }
    }
    
    /**
     * حساب وقت الانتظار باستخدام Exponential Backoff
     */
    private fun calculateBackoffDelay(attempt: Int): Long {
        // 1s, 2s, 4s, 8s, 16s, 32s (max)
        val delay = BASE_DELAY_MS * (1 shl (attempt - 1))
        return minOf(delay, MAX_DELAY_MS)
    }
    
    /**
     * تسجيل محاولة Webhook
     */
    private suspend fun logWebhookAttempt(
        transactionId: String,
        url: String,
        attempt: Int,
        status: WebhookStatus,
        httpStatusCode: Int?,
        errorMessage: String?,
        requestPayload: String,
        responseBody: String?,
        processingTimeMs: Long
    ) {
        try {
            val log = WebhookLog(
                transactionId = transactionId,
                webhookUrl = url,
                attempt = attempt,
                status = status,
                httpStatusCode = httpStatusCode,
                errorMessage = errorMessage,
                requestPayload = requestPayload,
                responseBody = responseBody,
                processingTimeMs = processingTimeMs
            )
            
            database.webhookLogDao().insert(log)
        } catch (e: Exception) {
            Log.e(TAG, "Error logging webhook attempt", e)
        }
    }
}

/**
 * نتيجة إرسال Webhook
 */
sealed class WebhookResult {
    data class Success(
        val httpStatusCode: Int,
        val responseBody: String?,
        val processingTimeMs: Long
    ) : WebhookResult()
    
    data class Failure(
        val exception: Exception,
        val httpStatusCode: Int?,
        val responseBody: String?,
        val processingTimeMs: Long
    ) : WebhookResult()
}
```

---

### 5. تحديث WebhookClient

**الملف**: `app/src/main/java/com/sms/paymentgateway/services/WebhookClient.kt`

```kotlin
package com.sms.paymentgateway.services

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebhookClient @Inject constructor(
    private val webhookRetryManager: WebhookRetryManager
) {
    companion object {
        private const val TAG = "WebhookClient"
    }
    
    /**
     * إرسال إشعار Webhook (مع إعادة المحاولة التلقائية)
     */
    fun sendPaymentConfirmation(
        webhookUrl: String,
        transactionId: String,
        smsData: Map<String, Any>,
        apiKey: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payload = JSONObject().apply {
                    put("event", "PAYMENT_CONFIRMED")
                    put("transactionId", transactionId)
                    put("smsData", JSONObject(smsData))
                    put("processedAt", System.currentTimeMillis())
                }
                
                val result = webhookRetryManager.sendWithRetry(
                    url = webhookUrl,
                    payload = payload,
                    transactionId = transactionId,
                    apiKey = apiKey
                )
                
                when (result) {
                    is WebhookResult.Success -> {
                        Log.i(TAG, "Webhook delivered successfully for transaction $transactionId")
                    }
                    is WebhookResult.Failure -> {
                        Log.e(TAG, "Failed to deliver webhook for transaction $transactionId after all retries", result.exception)
                        // يمكن إضافة إشعار للمستخدم هنا
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in sendPaymentConfirmation", e)
            }
        }
    }
}
```

---

### 6. إضافة API Endpoint لعرض السجلات

**الملف**: `app/src/main/java/com/sms/paymentgateway/services/ApiServer.kt`

أضف هذا الـ endpoint:

```kotlin
// في ApiServer.kt، أضف هذا الـ endpoint

private fun handleWebhookLogs(session: IHTTPSession): Response {
    return try {
        // التحقق من API Key
        val apiKey = session.headers["authorization"]?.removePrefix("Bearer ")
        if (!securityManager.validateApiKey(apiKey)) {
            return newFixedLengthResponse(
                Response.Status.UNAUTHORIZED,
                "application/json",
                """{"error": "Invalid API key"}"""
            )
        }
        
        // الحصول على المعاملات
        val transactionId = session.parameters["transactionId"]?.firstOrNull()
        val limit = session.parameters["limit"]?.firstOrNull()?.toIntOrNull() ?: 100
        
        val logs = runBlocking {
            if (transactionId != null) {
                database.webhookLogDao()
                    .getLogsByTransaction(transactionId)
                    .first()
            } else {
                database.webhookLogDao()
                    .getAllLogs(limit)
                    .first()
            }
        }
        
        val jsonArray = JSONArray()
        logs.forEach { log ->
            jsonArray.put(JSONObject().apply {
                put("id", log.id)
                put("transactionId", log.transactionId)
                put("webhookUrl", log.webhookUrl)
                put("attempt", log.attempt)
                put("status", log.status.name)
                put("httpStatusCode", log.httpStatusCode)
                put("errorMessage", log.errorMessage)
                put("timestamp", log.timestamp)
                put("processingTimeMs", log.processingTimeMs)
            })
        }
        
        newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            JSONObject().apply {
                put("logs", jsonArray)
                put("count", logs.size)
            }.toString()
        )
        
    } catch (e: Exception) {
        Log.e(TAG, "Error handling webhook logs request", e)
        newFixedLengthResponse(
            Response.Status.INTERNAL_ERROR,
            "application/json",
            """{"error": "${e.message}"}"""
        )
    }
}

// أضف هذا في serve() method:
"/api/v1/webhooks/logs" -> handleWebhookLogs(session)
```

---

## 🧪 الاختبار

### 1. اختبار يدوي

```bash
# إرسال معاملة جديدة
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "test-tx-001",
    "amount": 100.00,
    "phoneNumber": "01012345678",
    "walletType": "VODAFONE_CASH",
    "expiresInMinutes": 30
  }'

# عرض سجلات Webhook
curl -X GET "http://localhost:8080/api/v1/webhooks/logs?limit=10" \
  -H "Authorization: Bearer YOUR_API_KEY"

# عرض سجلات معاملة محددة
curl -X GET "http://localhost:8080/api/v1/webhooks/logs?transactionId=test-tx-001" \
  -H "Authorization: Bearer YOUR_API_KEY"
```

### 2. اختبار Unit Test

**الملف**: `app/src/test/java/com/sms/paymentgateway/WebhookRetryManagerTest.kt`

```kotlin
package com.sms.paymentgateway

import com.sms.paymentgateway.services.WebhookRetryManager
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class WebhookRetryManagerTest {
    
    @Test
    fun testBackoffDelay() {
        val manager = WebhookRetryManager(mockDatabase, mockSecurityManager)
        
        // Test exponential backoff: 1s, 2s, 4s, 8s, 16s
        assertEquals(1000L, manager.calculateBackoffDelay(1))
        assertEquals(2000L, manager.calculateBackoffDelay(2))
        assertEquals(4000L, manager.calculateBackoffDelay(3))
        assertEquals(8000L, manager.calculateBackoffDelay(4))
        assertEquals(16000L, manager.calculateBackoffDelay(5))
        assertEquals(32000L, manager.calculateBackoffDelay(6)) // max
    }
    
    @Test
    fun testWebhookRetrySuccess() = runBlocking {
        // Test successful webhook delivery
        val result = manager.sendWithRetry(
            url = "https://example.com/webhook",
            payload = JSONObject(mapOf("test" to "data")),
            transactionId = "test-001",
            apiKey = "test-key"
        )
        
        assertTrue(result is WebhookResult.Success)
    }
}
```

---

## 📊 مراقبة الأداء

### Dashboard Metrics

أضف هذه المقاييس لـ Dashboard:

```kotlin
data class WebhookMetrics(
    val totalAttempts: Int,
    val successfulDeliveries: Int,
    val failedDeliveries: Int,
    val averageProcessingTime: Double,
    val successRate: Float,
    val retriesCount: Int
)

suspend fun getWebhookMetrics(
    startTime: Long,
    endTime: Long
): WebhookMetrics {
    val logs = database.webhookLogDao()
        .getLogsByTimeRange(startTime, endTime)
    
    return WebhookMetrics(
        totalAttempts = logs.size,
        successfulDeliveries = logs.count { it.status == WebhookStatus.SUCCESS },
        failedDeliveries = logs.count { it.status == WebhookStatus.FAILED },
        averageProcessingTime = logs
            .mapNotNull { it.processingTimeMs }
            .average(),
        successRate = logs.count { it.status == WebhookStatus.SUCCESS }.toFloat() / logs.size,
        retriesCount = logs.count { it.attempt > 1 }
    )
}
```

---

## ✅ Checklist التنفيذ

- [ ] إنشاء WebhookLog Entity
- [ ] إنشاء WebhookLogDao
- [ ] تحديث AppDatabase مع Migration
- [ ] إنشاء WebhookRetryManager
- [ ] تحديث WebhookClient
- [ ] إضافة API Endpoint للسجلات
- [ ] كتابة Unit Tests
- [ ] اختبار يدوي
- [ ] إضافة Dashboard Metrics
- [ ] توثيق API

---

## 🎓 ملاحظات مهمة

1. **Exponential Backoff**: يبدأ بـ 1 ثانية ويتضاعف مع كل محاولة (1s, 2s, 4s, 8s, 16s)
2. **Max Retries**: 5 محاولات كحد أقصى
3. **Client Errors (4xx)**: لا نعيد المحاولة لأنها أخطاء من جانب العميل
4. **Server Errors (5xx)**: نعيد المحاولة لأنها قد تكون مؤقتة
5. **Logging**: نسجل كل محاولة للمراجعة والتحليل

---

**الخطوة التالية**: بعد تنفيذ هذه الميزة، يمكن الانتقال لتنفيذ "تحسين Battery Optimization"
