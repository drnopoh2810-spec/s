package com.sms.paymentgateway.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * سجل محاولات إرسال Webhook
 * يحفظ جميع المحاولات الناجحة والفاشلة للمراجعة والتحليل
 */
@Entity(tableName = "webhook_logs")
data class WebhookLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** معرف المعاملة المرتبطة */
    val transactionId: String,
    
    /** عنوان Webhook المستهدف */
    val webhookUrl: String,
    
    /** رقم المحاولة (1-5) */
    val attempt: Int,
    
    /** حالة المحاولة */
    val status: WebhookStatus,
    
    /** HTTP Status Code إن وجد */
    val httpStatusCode: Int?,
    
    /** رسالة الخطأ إن وجدت */
    val errorMessage: String?,
    
    /** البيانات المرسلة (JSON) */
    val requestPayload: String,
    
    /** الرد المستلم من الخادم */
    val responseBody: String?,
    
    /** وقت المحاولة */
    val timestamp: Long = System.currentTimeMillis(),
    
    /** وقت المعالجة بالميلي ثانية */
    val processingTimeMs: Long?
)

/**
 * حالات Webhook
 */
enum class WebhookStatus {
    /** في انتظار الإرسال */
    PENDING,
    
    /** تم الإرسال بنجاح */
    SUCCESS,
    
    /** فشل الإرسال نهائياً */
    FAILED,
    
    /** جاري إعادة المحاولة */
    RETRYING
}
