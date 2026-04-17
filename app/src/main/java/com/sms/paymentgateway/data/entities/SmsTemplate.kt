package com.sms.paymentgateway.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * قالب رسالة SMS
 * يدعم المتغيرات بصيغة {variable_name}
 *
 * مثال: "تم استلام {amount} جنيه من {phone} عبر {wallet}"
 */
@Entity(tableName = "sms_templates")
data class SmsTemplate(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,                    // اسم القالب
    val template: String,                // نص القالب مع المتغيرات
    val description: String? = null,     // وصف اختياري
    val isActive: Boolean = true,
    val isDefault: Boolean = false,      // القالب الافتراضي
    val usageCount: Int = 0,             // عدد مرات الاستخدام
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
