package com.sms.paymentgateway.services

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * كاشف الشبكة - يحدد نوع الاتصال والـ IP العام
 */
@Singleton
class NetworkDetector @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "NetworkDetector"
        private const val PUBLIC_IP_SERVICE = "https://api.ipify.org?format=json"
        private const val BACKUP_IP_SERVICE = "https://httpbin.org/ip"
        private const val CONNECTION_TIMEOUT = 10_000L
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        .readTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        .build()

    data class NetworkInfo(
        val localIp: String,
        val publicIp: String?,
        val isPublicAccessible: Boolean,
        val networkType: NetworkType,
        val suggestedUrls: List<ConnectionUrl>
    )

    data class ConnectionUrl(
        val type: String,
        val url: String,
        val description: String,
        val accessible: AccessibilityLevel
    )

    enum class NetworkType {
        LOCAL_ONLY,      // شبكة محلية فقط
        PUBLIC_IP,       // IP عام مباشر
        BEHIND_NAT,      // خلف NAT (يحتاج Port Forwarding)
        MOBILE_DATA,     // بيانات الجوال
        UNKNOWN
    }

    enum class AccessibilityLevel {
        LOCAL_ONLY,      // محلي فقط
        REQUIRES_SETUP,  // يحتاج إعداد
        PUBLIC_READY,    // جاهز للوصول العام
        UNKNOWN
    }

    /**
     * كشف معلومات الشبكة الشاملة
     */
    suspend fun detectNetworkInfo(port: Int = 8080): NetworkInfo = withContext(Dispatchers.IO) {
        Log.i(TAG, "🔍 بدء كشف معلومات الشبكة...")

        val localIp = getLocalIpAddress()
        val publicIp = getPublicIpAddress()
        val networkType = determineNetworkType(localIp, publicIp)
        val isPublicAccessible = checkPublicAccessibility(publicIp, port)

        val suggestedUrls = generateConnectionUrls(localIp, publicIp, port, isPublicAccessible)

        Log.i(TAG, "📊 نتائج كشف الشبكة:")
        Log.i(TAG, "   IP محلي: $localIp")
        Log.i(TAG, "   IP عام: $publicIp")
        Log.i(TAG, "   نوع الشبكة: $networkType")
        Log.i(TAG, "   قابل للوصول العام: $isPublicAccessible")

        NetworkInfo(
            localIp = localIp,
            publicIp = publicIp,
            isPublicAccessible = isPublicAccessible,
            networkType = networkType,
            suggestedUrls = suggestedUrls
        )
    }

    /**
     * الحصول على عنوان IP المحلي
     */
    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    
                    if (!address.isLoopbackAddress && 
                        !address.isLinkLocalAddress && 
                        address is java.net.Inet4Address) {
                        return address.hostAddress ?: "localhost"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في الحصول على IP المحلي", e)
        }
        return "localhost"
    }

    /**
     * الحصول على عنوان IP العام
     */
    private suspend fun getPublicIpAddress(): String? = withContext(Dispatchers.IO) {
        try {
            // محاولة الخدمة الأساسية
            val request = Request.Builder()
                .url(PUBLIC_IP_SERVICE)
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                val publicIp = json.optString("ip")
                if (publicIp.isNotEmpty()) {
                    Log.i(TAG, "✅ تم الحصول على IP العام: $publicIp")
                    return@withContext publicIp
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "فشل في الحصول على IP العام من الخدمة الأساسية", e)
        }

        try {
            // محاولة الخدمة البديلة
            val request = Request.Builder()
                .url(BACKUP_IP_SERVICE)
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                val publicIp = json.optString("origin")?.split(",")?.first()?.trim()
                if (!publicIp.isNullOrEmpty()) {
                    Log.i(TAG, "✅ تم الحصول على IP العام من الخدمة البديلة: $publicIp")
                    return@withContext publicIp
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "فشل في الحصول على IP العام من جميع الخدمات", e)
        }

        return@withContext null
    }

    /**
     * تحديد نوع الشبكة
     */
    private fun determineNetworkType(localIp: String, publicIp: String?): NetworkType {
        return when {
            publicIp == null -> NetworkType.LOCAL_ONLY
            localIp == publicIp -> NetworkType.PUBLIC_IP
            isPrivateIp(localIp) -> NetworkType.BEHIND_NAT
            isMobileDataIp(localIp) -> NetworkType.MOBILE_DATA
            else -> NetworkType.UNKNOWN
        }
    }

    /**
     * فحص إمكانية الوصول العام
     */
    private suspend fun checkPublicAccessibility(publicIp: String?, port: Int): Boolean = withContext(Dispatchers.IO) {
        if (publicIp == null) return@withContext false

        try {
            // محاولة الاتصال بالـ IP العام
            val testUrl = "http://$publicIp:$port/api/v1/health"
            val request = Request.Builder()
                .url(testUrl)
                .build()

            val response = httpClient.newCall(request).execute()
            val accessible = response.isSuccessful
            
            Log.i(TAG, "🔍 اختبار الوصول العام: $testUrl -> ${if (accessible) "نجح" else "فشل"}")
            return@withContext accessible
            
        } catch (e: Exception) {
            Log.d(TAG, "لا يمكن الوصول للخادم من الخارج (طبيعي إذا لم يتم إعداد Port Forwarding)")
            return@withContext false
        }
    }

    /**
     * توليد روابط الاتصال المقترحة
     */
    private fun generateConnectionUrls(
        localIp: String, 
        publicIp: String?, 
        port: Int, 
        isPublicAccessible: Boolean
    ): List<ConnectionUrl> {
        val urls = mutableListOf<ConnectionUrl>()

        // رابط محلي
        urls.add(ConnectionUrl(
            type = "local",
            url = "http://$localIp:$port/api/v1/connect",
            description = "للاستخدام داخل نفس الشبكة المحلية",
            accessible = AccessibilityLevel.LOCAL_ONLY
        ))

        // رابط عام
        if (publicIp != null) {
            urls.add(ConnectionUrl(
                type = "public",
                url = "http://$publicIp:$port/api/v1/connect",
                description = if (isPublicAccessible) 
                    "جاهز للاستخدام من أي مكان في العالم" 
                else 
                    "يحتاج إعداد Port Forwarding في الراوتر",
                accessible = if (isPublicAccessible) 
                    AccessibilityLevel.PUBLIC_READY 
                else 
                    AccessibilityLevel.REQUIRES_SETUP
            ))
        }

        // اقتراح DDNS
        urls.add(ConnectionUrl(
            type = "ddns",
            url = "http://your-domain.ddns.net:$port/api/v1/connect",
            description = "استخدم خدمة DDNS للحصول على دومين ثابت",
            accessible = AccessibilityLevel.REQUIRES_SETUP
        ))

        // اقتراح Ngrok
        urls.add(ConnectionUrl(
            type = "tunnel",
            url = "https://abc123.ngrok.io/api/v1/connect",
            description = "استخدم Ngrok للوصول المؤقت السريع",
            accessible = AccessibilityLevel.REQUIRES_SETUP
        ))

        return urls
    }

    /**
     * فحص ما إذا كان IP خاص (محلي)
     */
    private fun isPrivateIp(ip: String): Boolean {
        return ip.startsWith("192.168.") || 
               ip.startsWith("10.") || 
               ip.startsWith("172.16.") ||
               ip.startsWith("172.17.") ||
               ip.startsWith("172.18.") ||
               ip.startsWith("172.19.") ||
               ip.startsWith("172.20.") ||
               ip.startsWith("172.21.") ||
               ip.startsWith("172.22.") ||
               ip.startsWith("172.23.") ||
               ip.startsWith("172.24.") ||
               ip.startsWith("172.25.") ||
               ip.startsWith("172.26.") ||
               ip.startsWith("172.27.") ||
               ip.startsWith("172.28.") ||
               ip.startsWith("172.29.") ||
               ip.startsWith("172.30.") ||
               ip.startsWith("172.31.")
    }

    /**
     * فحص ما إذا كان IP من بيانات الجوال
     */
    private fun isMobileDataIp(ip: String): Boolean {
        // هذا تخمين بناءً على نطاقات شائعة لمزودي الخدمة
        // يمكن تحسينه بناءً على المزودين المحليين
        return !isPrivateIp(ip) && !ip.startsWith("127.")
    }

    /**
     * الحصول على معلومات الشبكة المبسطة
     */
    suspend fun getQuickNetworkInfo(): Pair<String, String?> = withContext(Dispatchers.IO) {
        val localIp = getLocalIpAddress()
        val publicIp = getPublicIpAddress()
        return@withContext Pair(localIp, publicIp)
    }
}