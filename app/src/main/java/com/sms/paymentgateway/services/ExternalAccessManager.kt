package com.sms.paymentgateway.services

import android.content.Context
import android.util.Log
import com.sms.paymentgateway.utils.security.SecurityManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدير الوصول الخارجي - يدير إعدادات الوصول من خارج الشبكة المحلية
 */
@Singleton
class ExternalAccessManager @Inject constructor(
    private val context: Context,
    private val securityManager: SecurityManager,
    private val networkDetector: NetworkDetector
) {
    companion object {
        private const val TAG = "ExternalAccessManager"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // حالة الوصول الخارجي
    private val _externalAccessInfo = MutableStateFlow<ExternalAccessInfo?>(null)
    val externalAccessInfo: StateFlow<ExternalAccessInfo?> = _externalAccessInfo

    // إعدادات الوصول الخارجي
    private val _accessSettings = MutableStateFlow(ExternalAccessSettings())
    val accessSettings: StateFlow<ExternalAccessSettings> = _accessSettings

    data class ExternalAccessInfo(
        val networkInfo: NetworkDetector.NetworkInfo,
        val recommendedSetup: SetupRecommendation,
        val availableOptions: List<AccessOption>
    )

    data class ExternalAccessSettings(
        val enablePublicAccess: Boolean = false,
        val preferredMethod: AccessMethod = AccessMethod.PORT_FORWARDING,
        val customDomain: String? = null,
        val ngrokEnabled: Boolean = false,
        val ddnsProvider: String? = null
    )

    data class SetupRecommendation(
        val method: AccessMethod,
        val difficulty: SetupDifficulty,
        val description: String,
        val steps: List<String>
    )

    data class AccessOption(
        val method: AccessMethod,
        val title: String,
        val description: String,
        val difficulty: SetupDifficulty,
        val estimatedTime: String,
        val pros: List<String>,
        val cons: List<String>
    )

    enum class AccessMethod {
        PORT_FORWARDING,
        DDNS,
        NGROK,
        VPN,
        CLOUD_RELAY
    }

    enum class SetupDifficulty {
        EASY,
        MEDIUM,
        HARD
    }

    /**
     * تحليل إمكانيات الوصول الخارجي
     */
    suspend fun analyzeExternalAccess(port: Int = 8080): ExternalAccessInfo {
        Log.i(TAG, "🔍 تحليل إمكانيات الوصول الخارجي...")

        val networkInfo = networkDetector.detectNetworkInfo(port)
        val recommendation = generateRecommendation(networkInfo)
        val options = generateAccessOptions(networkInfo)

        val accessInfo = ExternalAccessInfo(
            networkInfo = networkInfo,
            recommendedSetup = recommendation,
            availableOptions = options
        )

        _externalAccessInfo.value = accessInfo
        return accessInfo
    }

    /**
     * توليد توصية الإعداد
     */
    private fun generateRecommendation(networkInfo: NetworkDetector.NetworkInfo): SetupRecommendation {
        return when (networkInfo.networkType) {
            NetworkDetector.NetworkType.PUBLIC_IP -> {
                if (networkInfo.isPublicAccessible) {
                    SetupRecommendation(
                        method = AccessMethod.PORT_FORWARDING,
                        difficulty = SetupDifficulty.EASY,
                        description = "جهازك لديه IP عام ويمكن الوصول إليه مباشرة!",
                        steps = listOf(
                            "✅ الإعداد مكتمل - يمكن الوصول من أي مكان",
                            "استخدم الرابط العام المعروض",
                            "تأكد من أمان API Key"
                        )
                    )
                } else {
                    SetupRecommendation(
                        method = AccessMethod.PORT_FORWARDING,
                        difficulty = SetupDifficulty.MEDIUM,
                        description = "تحتاج لفتح المنفذ في جدار الحماية",
                        steps = listOf(
                            "افتح إعدادات جدار الحماية",
                            "أضف استثناء للمنفذ 8080",
                            "اختبر الوصول من خارج الشبكة"
                        )
                    )
                }
            }
            
            NetworkDetector.NetworkType.BEHIND_NAT -> {
                SetupRecommendation(
                    method = AccessMethod.PORT_FORWARDING,
                    difficulty = SetupDifficulty.MEDIUM,
                    description = "الحل الأمثل: إعداد Port Forwarding في الراوتر",
                    steps = listOf(
                        "ادخل إعدادات الراوتر (عادة 192.168.1.1)",
                        "ابحث عن Port Forwarding أو Virtual Server",
                        "أضف قاعدة: المنفذ 8080 → ${networkInfo.localIp}:8080",
                        "احفظ الإعدادات وأعد تشغيل الراوتر",
                        "اختبر الوصول باستخدام IP العام"
                    )
                )
            }
            
            NetworkDetector.NetworkType.MOBILE_DATA -> {
                SetupRecommendation(
                    method = AccessMethod.NGROK,
                    difficulty = SetupDifficulty.EASY,
                    description = "الأفضل: استخدام Ngrok للوصول السريع",
                    steps = listOf(
                        "حمل تطبيق Ngrok",
                        "سجل حساب مجاني",
                        "شغل: ngrok http 8080",
                        "استخدم الرابط المولد"
                    )
                )
            }
            
            else -> {
                SetupRecommendation(
                    method = AccessMethod.NGROK,
                    difficulty = SetupDifficulty.EASY,
                    description = "ابدأ بـ Ngrok للاختبار السريع",
                    steps = listOf(
                        "حمل Ngrok من ngrok.com",
                        "شغل: ngrok http 8080",
                        "استخدم الرابط https المولد"
                    )
                )
            }
        }
    }

    /**
     * توليد خيارات الوصول المتاحة
     */
    private fun generateAccessOptions(networkInfo: NetworkDetector.NetworkInfo): List<AccessOption> {
        val options = mutableListOf<AccessOption>()

        // Port Forwarding
        options.add(AccessOption(
            method = AccessMethod.PORT_FORWARDING,
            title = "Port Forwarding (الأفضل)",
            description = "فتح منفذ في الراوتر للوصول المباشر",
            difficulty = SetupDifficulty.MEDIUM,
            estimatedTime = "10-15 دقيقة",
            pros = listOf(
                "اتصال مباشر وسريع",
                "لا توجد قيود على البيانات",
                "مجاني بالكامل",
                "أداء ممتاز"
            ),
            cons = listOf(
                "يحتاج وصول لإعدادات الراوتر",
                "قد يتطلب IP ثابت",
                "إعداد تقني نسبياً"
            )
        ))

        // DDNS
        options.add(AccessOption(
            method = AccessMethod.DDNS,
            title = "Dynamic DNS",
            description = "دومين ثابت يتتبع IP المتغير",
            difficulty = SetupDifficulty.MEDIUM,
            estimatedTime = "15-20 دقيقة",
            pros = listOf(
                "دومين ثابت سهل التذكر",
                "يعمل مع IP متغير",
                "خدمات مجانية متاحة",
                "احترافي للعملاء"
            ),
            cons = listOf(
                "يحتاج Port Forwarding أيضاً",
                "قد يتطلب تحديث دوري",
                "بعض الخدمات مدفوعة"
            )
        ))

        // Ngrok
        options.add(AccessOption(
            method = AccessMethod.NGROK,
            title = "Ngrok (سريع)",
            description = "نفق مؤقت للوصول الفوري",
            difficulty = SetupDifficulty.EASY,
            estimatedTime = "2-5 دقائق",
            pros = listOf(
                "إعداد سريع جداً",
                "يعمل مع أي شبكة",
                "HTTPS مجاني",
                "مثالي للاختبار"
            ),
            cons = listOf(
                "رابط عشوائي يتغير",
                "قيود على الاستخدام المجاني",
                "يعتمد على خدمة خارجية",
                "قد يكون بطيء نسبياً"
            )
        ))

        // VPN
        options.add(AccessOption(
            method = AccessMethod.VPN,
            title = "VPN الشخصي",
            description = "شبكة خاصة افتراضية",
            difficulty = SetupDifficulty.HARD,
            estimatedTime = "30-60 دقيقة",
            pros = listOf(
                "أمان عالي جداً",
                "تحكم كامل",
                "يعمل مع جميع التطبيقات",
                "خصوصية تامة"
            ),
            cons = listOf(
                "إعداد معقد",
                "يحتاج خادم VPN",
                "تكلفة إضافية",
                "صيانة مستمرة"
            )
        ))

        return options
    }

    /**
     * توليد دليل الإعداد المفصل
     */
    fun generateSetupGuide(method: AccessMethod, networkInfo: NetworkDetector.NetworkInfo): String {
        return when (method) {
            AccessMethod.PORT_FORWARDING -> generatePortForwardingGuide(networkInfo)
            AccessMethod.DDNS -> generateDDNSGuide(networkInfo)
            AccessMethod.NGROK -> generateNgrokGuide()
            AccessMethod.VPN -> generateVPNGuide()
            AccessMethod.CLOUD_RELAY -> generateCloudRelayGuide()
        }
    }

    private fun generatePortForwardingGuide(networkInfo: NetworkDetector.NetworkInfo): String {
        return """
# دليل إعداد Port Forwarding

## الخطوات التفصيلية:

### 1. الوصول لإعدادات الراوتر
- افتح متصفح الويب
- اذهب إلى: http://192.168.1.1 (أو http://192.168.0.1)
- ادخل اسم المستخدم وكلمة المرور (عادة admin/admin)

### 2. البحث عن Port Forwarding
ابحث عن أحد هذه الأسماء:
- Port Forwarding
- Virtual Server
- NAT Forwarding
- Port Mapping

### 3. إضافة القاعدة الجديدة
- **اسم الخدمة**: SMS Payment Gateway
- **المنفذ الخارجي**: 8080
- **IP المحلي**: ${networkInfo.localIp}
- **المنفذ المحلي**: 8080
- **البروتوكول**: TCP أو Both

### 4. حفظ واختبار
- احفظ الإعدادات
- أعد تشغيل الراوتر
- اختبر الوصول: http://${networkInfo.publicIp ?: "YOUR_PUBLIC_IP"}:8080

## نصائح مهمة:
✅ تأكد من أن الهاتف متصل بنفس الشبكة
✅ قد تحتاج IP ثابت من مزود الخدمة
✅ بعض مزودي الخدمة يحجبون المنافذ
        """.trimIndent()
    }

    private fun generateDDNSGuide(networkInfo: NetworkDetector.NetworkInfo): String {
        return """
# دليل إعداد Dynamic DNS

## الخطوات:

### 1. اختيار مزود DDNS
خدمات مجانية موصى بها:
- **No-IP**: noip.com (30 يوم مجاني)
- **DuckDNS**: duckdns.org (مجاني بالكامل)
- **Dynu**: dynu.com (مجاني مع قيود)

### 2. إنشاء حساب ودومين
- سجل في الخدمة المختارة
- أنشئ دومين مثل: mygateway.ddns.net
- احفظ معلومات الحساب

### 3. إعداد الراوتر
- ادخل إعدادات الراوتر
- ابحث عن DDNS Settings
- أدخل معلومات الخدمة والدومين
- فعّل التحديث التلقائي

### 4. إعداد Port Forwarding
- اتبع خطوات Port Forwarding السابقة
- استخدم الدومين بدلاً من IP

### 5. الاختبار
- اختبر الوصول: http://mygateway.ddns.net:8080
- تأكد من التحديث التلقائي

## الرابط النهائي:
http://your-domain.ddns.net:8080/api/v1/connect?key=${securityManager.getApiKey()}
        """.trimIndent()
    }

    private fun generateNgrokGuide(): String {
        return """
# دليل إعداد Ngrok (الأسرع)

## الخطوات:

### 1. تحميل Ngrok
- اذهب إلى: https://ngrok.com
- حمل النسخة المناسبة لنظامك
- استخرج الملف

### 2. إنشاء حساب (اختياري)
- سجل حساب مجاني للحصول على ميزات إضافية
- احصل على Auth Token

### 3. تشغيل Ngrok
افتح Terminal/Command Prompt وشغل:
```bash
ngrok http 8080
```

### 4. نسخ الرابط
- انسخ الرابط https المعروض
- مثال: https://abc123.ngrok.io

### 5. الاستخدام
الرابط النهائي:
https://abc123.ngrok.io/api/v1/connect?key=${securityManager.getApiKey()}

## ملاحظات:
⚠️ الرابط يتغير كل مرة تشغل Ngrok
⚠️ الحساب المجاني له قيود
✅ مثالي للاختبار والاستخدام المؤقت
        """.trimIndent()
    }

    private fun generateVPNGuide(): String {
        return """
# دليل إعداد VPN الشخصي

## الخيارات المتاحة:

### 1. OpenVPN
- إعداد خادم OpenVPN على VPS
- تكلفة: 5-10$ شهرياً
- أمان عالي جداً

### 2. WireGuard
- بروتوكول VPN حديث وسريع
- أسهل في الإعداد من OpenVPN
- أداء ممتاز

### 3. Tailscale (الأسهل)
- خدمة VPN mesh مجانية
- إعداد بنقرة واحدة
- مثالي للاستخدام الشخصي

## خطوات Tailscale:
1. حمل Tailscale على الهاتف والكمبيوتر
2. سجل دخول بنفس الحساب
3. الأجهزة ستظهر في شبكة واحدة
4. استخدم IP الـ Tailscale للاتصال

## الرابط النهائي:
http://100.x.x.x:8080/api/v1/connect?key=${securityManager.getApiKey()}
        """.trimIndent()
    }

    private fun generateCloudRelayGuide(): String {
        return """
# دليل إعداد Cloud Relay

## الفكرة:
خادم سحابي بسيط يعيد توجيه الطلبات للهاتف

## الخطوات:

### 1. إنشاء خادم سحابي
- استخدم Heroku أو Railway أو Vercel
- أنشئ تطبيق Node.js بسيط
- أضف كود إعادة التوجيه

### 2. كود الخادم البسيط:
```javascript
const express = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');

const app = express();
const PHONE_URL = 'http://YOUR_PHONE_IP:8080';

app.use('/api', createProxyMiddleware({
  target: PHONE_URL,
  changeOrigin: true
}));

app.listen(process.env.PORT || 3000);
```

### 3. النشر
- ارفع الكود للخدمة السحابية
- احصل على رابط التطبيق

### 4. الاستخدام
الرابط النهائي:
https://your-app.herokuapp.com/api/v1/connect?key=${securityManager.getApiKey()}

## المزايا:
✅ رابط ثابت وجميل
✅ HTTPS مجاني
✅ لا يحتاج إعداد الراوتر

## العيوب:
⚠️ يحتاج برمجة بسيطة
⚠️ قد يكون بطيء نسبياً
        """.trimIndent()
    }

    /**
     * اختبار الوصول الخارجي
     */
    suspend fun testExternalAccess(url: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            // سيتم تطبيق اختبار الوصول هنا
            Log.i(TAG, "🧪 اختبار الوصول الخارجي: $url")
            // TODO: تطبيق اختبار HTTP
            false
        } catch (e: Exception) {
            Log.e(TAG, "فشل اختبار الوصول الخارجي", e)
            false
        }
    }
}