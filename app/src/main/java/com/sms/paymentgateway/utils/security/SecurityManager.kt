package com.sms.paymentgateway.utils.security

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدير الأمان
 *
 * سلوك API Key:
 * ─────────────────────────────────────────────────────────────
 * الـ API Key يُولَّد مرة واحدة ويبقى ثابتاً حتى بعد إعادة التثبيت.
 *
 * كيف؟
 * نشتق الـ API Key من Android ID (ثابت لكل جهاز) + salt سري.
 * Android ID لا يتغير إلا عند Factory Reset.
 *
 * السيناريوهات:
 * ┌─────────────────────────────────┬──────────────────────────┐
 * │ الحدث                           │ API Key                  │
 * ├─────────────────────────────────┼──────────────────────────┤
 * │ أول تثبيت                       │ يُولَّد ويُحفظ           │
 * │ إعادة تثبيت (Uninstall+Install) │ نفس المفتاح ✅           │
 * │ Clear Data                      │ نفس المفتاح ✅           │
 * │ Factory Reset                   │ مفتاح جديد (طبيعي)      │
 * │ تغيير الهاتف                    │ مفتاح جديد (طبيعي)      │
 * └─────────────────────────────────┴──────────────────────────┘
 *
 * المستخدم يمكنه أيضاً تغيير المفتاح يدوياً من الإعدادات.
 */
@Singleton
class SecurityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)

    // ─── API Key ──────────────────────────────────────────────────────────────

    /**
     * الـ API Key:
     * 1. إذا المستخدم عيّن مفتاحاً يدوياً → استخدمه
     * 2. إذا لا → اشتقه من Android ID (ثابت بعد إعادة التثبيت)
     */
    private val _apiKey: String
        get() {
            // مفتاح يدوي من المستخدم (الأولوية الأعلى)
            val manual = prefs.getString("api_key_manual", null)
            if (!manual.isNullOrBlank()) return manual

            // مفتاح مشتق من Android ID (ثابت بعد إعادة التثبيت)
            return getDerivedApiKey()
        }

    /**
     * اشتقاق API Key من Android ID + App Signing Key
     *
     * المعادلة:
     * API Key = SHA256( AndroidID + ":" + AppSignature + ":" + StaticSalt )
     *
     * لماذا هذا آمن؟
     * - AndroidID: فريد لكل هاتف (مختلف بين الأجهزة)
     * - AppSignature: توقيع APK الخاص بك (مختلف بين المطورين)
     * - StaticSalt: ثابت في الكود (يصعّب الـ brute force)
     *
     * النتيجة:
     * - هاتف A عند مطور X  → API Key مختلف عن هاتف B عند مطور X ✅
     * - هاتف A عند مطور X  → API Key مختلف عن هاتف A عند مطور Y ✅
     * - حتى لو نشرت الكود على GitHub، لا أحد يقدر يحسب مفتاحك ✅
     */
    private fun getDerivedApiKey(): String {
        val cached = prefs.getString("api_key_derived", null)
        if (!cached.isNullOrBlank()) return cached

        val androidId = getAndroidId()
        val appSignature = getAppSignature()
        val staticSalt = "sms_gw_v3_\$2b\$12\$unique_per_build"

        // دمج الثلاثة معاً
        val derived = sha256("$androidId:$appSignature:$staticSalt").take(32)

        prefs.edit().putString("api_key_derived", derived).apply()
        Timber.i("API Key derived (unique per device + build)")
        return derived
    }

    /**
     * الحصول على توقيع APK
     * هذا فريد لكل مطور (يعتمد على keystore الخاص به)
     */
    private fun getAppSignature(): String {
        return try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = packageInfo.signingInfo
                val signatures = signingInfo?.apkContentsSigners ?: return "no_sig"
                sha256(signatures[0].toByteArray().toString()).take(16)
            } else {
                @Suppress("DEPRECATION")
                val pi = context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_SIGNATURES
                )
                @Suppress("DEPRECATION")
                sha256(pi.signatures[0].toByteArray().toString()).take(16)
            }
            packageInfo
        } catch (e: Exception) {
            // fallback: استخدم package name (أقل أماناً لكن يعمل)
            sha256(context.packageName).take(16)
        }
    }

    private fun getAndroidId(): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "fallback_${System.currentTimeMillis()}"
    }

    private fun sha256(input: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun getApiKey(): String = _apiKey

    fun validateApiKey(providedKey: String?): Boolean {
        if (providedKey == null) return false
        return providedKey == _apiKey
    }

    /**
     * تعيين مفتاح API يدوي من المستخدم
     * يتجاوز المفتاح المشتق من Android ID
     */
    fun setManualApiKey(key: String) {
        if (key.length < 16) {
            Timber.w("API Key too short (min 16 chars)")
            return
        }
        prefs.edit().putString("api_key_manual", key).apply()
        Timber.i("Manual API Key set")
    }

    /**
     * إعادة تعيين المفتاح اليدوي (العودة للمشتق من Android ID)
     */
    fun clearManualApiKey() {
        prefs.edit().remove("api_key_manual").apply()
        Timber.i("Manual API Key cleared, reverting to derived key")
    }

    fun isUsingManualKey(): Boolean =
        !prefs.getString("api_key_manual", null).isNullOrBlank()

    /**
     * معلومات المفتاح للعرض في الـ UI
     */
    fun getApiKeyInfo(): ApiKeyInfo {
        val isManual = isUsingManualKey()
        return ApiKeyInfo(
            key = _apiKey,
            source = if (isManual) ApiKeySource.MANUAL else ApiKeySource.DERIVED,
            isPersistent = true, // دائماً ثابت بعد إعادة التثبيت
            androidId = getAndroidId().take(8) + "..." // للعرض فقط
        )
    }

    fun generateHmacSignature(data: String): String {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(_hmacSecret.toByteArray(), "HmacSHA256")
            mac.init(secretKey)
            val hmacBytes = mac.doFinal(data.toByteArray())
            hmacBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "Error generating HMAC signature")
            ""
        }
    }

    /** overload يقبل secret خارجي (للـ Webhook signing) */
    fun generateHmacSignature(data: String, secret: String): String {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
            mac.init(secretKey)
            val hmacBytes = mac.doFinal(data.toByteArray())
            hmacBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "Error generating HMAC signature with custom secret")
            ""
        }
    }

    fun verifyHmacSignature(data: String, signature: String): Boolean {
        val computed = generateHmacSignature(data)
        return computed.equals(signature, ignoreCase = true)
    }

    private fun generateAndSaveApiKey(): String {
        // لم يعد يُستخدم - الآن نشتق من Android ID
        return getDerivedApiKey()
    }

    // Regenerate API Key (يدوي - يُنشئ مفتاحاً عشوائياً جديداً)
    fun regenerateApiKey(): String {
        val newKey = generateRandomString(32)
        setManualApiKey(newKey)
        Timber.i("API Key manually regenerated")
        return newKey
    }

    private fun generateAndSaveHmacSecret(): String {
        val secret = generateRandomString(64)
        prefs.edit().putString("hmac_secret", secret).apply()
        Timber.i("Generated new HMAC Secret")
        return secret
    }

    private fun generateRandomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }

    // IP Whitelist Management
    fun isIpAllowed(ip: String): Boolean {
        val whitelist = getIpWhitelist()
        // If whitelist is empty, allow all IPs
        if (whitelist.isEmpty()) return true
        
        // Check if IP is in whitelist
        return whitelist.contains(ip) || whitelist.contains("*")
    }

    fun getIpWhitelist(): Set<String> {
        return prefs.getStringSet("ip_whitelist", emptySet()) ?: emptySet()
    }

    fun addIpToWhitelist(ip: String) {
        val whitelist = getIpWhitelist().toMutableSet()
        whitelist.add(ip)
        prefs.edit().putStringSet("ip_whitelist", whitelist).apply()
        Timber.i("Added IP to whitelist: $ip")
    }

    fun removeIpFromWhitelist(ip: String) {
        val whitelist = getIpWhitelist().toMutableSet()
        whitelist.remove(ip)
        prefs.edit().putStringSet("ip_whitelist", whitelist).apply()
        Timber.i("Removed IP from whitelist: $ip")
    }

    fun clearIpWhitelist() {
        prefs.edit().remove("ip_whitelist").apply()
        Timber.i("IP whitelist cleared")
    }

    // Webhook Configuration
    fun getWebhookUrl(): String? {
        return prefs.getString("webhook_url", null)
    }

    fun setWebhookUrl(url: String) {
        prefs.edit().putString("webhook_url", url).apply()
        Timber.i("Webhook URL updated")
    }

    fun getWebhookSecret(): String? {
        return prefs.getString("webhook_secret", null)
    }

    fun setWebhookSecret(secret: String) {
        prefs.edit().putString("webhook_secret", secret).apply()
        Timber.i("Webhook secret updated")
    }

    // Regenerate API Key
    fun regenerateApiKey(): String {
        val newKey = generateRandomString(32)
        prefs.edit().putString("api_key", newKey).apply()
        Timber.i("API Key regenerated")
        return newKey
    }

    // ─── API Key Rotation (ميزة 12) ─────────────────────────────────────────

    /**
     * تدوير مفتاح API مع فترة سماح للمفتاح القديم
     * @return المفتاح الجديد
     */
    fun rotateApiKey(): RotatedKey {
        val oldKey = _apiKey
        val newKey = generateRandomString(32)
        val gracePeriodEnd = System.currentTimeMillis() + 24 * 60 * 60 * 1000L

        // الـ key الجديد يصبح manual key
        prefs.edit()
            .putString("api_key_manual", newKey)
            .putString("api_key_old", oldKey)
            .putLong("api_key_grace_end", gracePeriodEnd)
            .apply()

        Timber.i("API Key rotated with 24h grace period")
        return RotatedKey(newKey = newKey, oldKey = oldKey, gracePeriodEnd = gracePeriodEnd)
    }

    /** التحقق من المفتاح مع دعم فترة السماح للمفتاح القديم */
    fun validateApiKeyWithGrace(providedKey: String?): Boolean {
        if (providedKey == null) return false
        if (providedKey == _apiKey) return true

        // تحقق من المفتاح القديم خلال فترة السماح
        val oldKey = prefs.getString("api_key_old", null)
        val graceEnd = prefs.getLong("api_key_grace_end", 0L)
        if (oldKey != null && providedKey == oldKey && System.currentTimeMillis() < graceEnd) {
            Timber.w("Request using old API key (grace period active)")
            return true
        }
        return false
    }

    fun getKeyRotationInfo(): Map<String, Any?> {
        val graceEnd = prefs.getLong("api_key_grace_end", 0L)
        val hasOldKey = prefs.getString("api_key_old", null) != null
        val graceActive = hasOldKey && System.currentTimeMillis() < graceEnd
        return mapOf(
            "hasOldKey" to hasOldKey,
            "gracePeriodActive" to graceActive,
            "gracePeriodEnd" to if (graceActive) graceEnd else null
        )
    }

    // ─── IP Whitelist Enhancement (ميزة 13) ─────────────────────────────────

    /** إضافة IP مع وصف وتاريخ انتهاء اختياري */
    fun addIpToWhitelistEnhanced(ip: String, description: String = "", expiresAt: Long? = null) {
        val whitelist = getIpWhitelistEnhanced().toMutableList()
        whitelist.removeAll { it.ip == ip } // إزالة القديم إن وُجد
        whitelist.add(IpWhitelistEntry(ip, description, expiresAt))
        saveIpWhitelistEnhanced(whitelist)
        Timber.i("IP added to whitelist: $ip")
    }

    fun removeIpFromWhitelistEnhanced(ip: String) {
        val whitelist = getIpWhitelistEnhanced().filter { it.ip != ip }
        saveIpWhitelistEnhanced(whitelist)
        Timber.i("IP removed from whitelist: $ip")
    }

    fun getIpWhitelistEnhanced(): List<IpWhitelistEntry> {
        val json = prefs.getString("ip_whitelist_v2", null) ?: return emptyList()
        return try {
            com.google.gson.Gson().fromJson(json, Array<IpWhitelistEntry>::class.java).toList()
        } catch (e: Exception) { emptyList() }
    }

    private fun saveIpWhitelistEnhanced(list: List<IpWhitelistEntry>) {
        prefs.edit().putString("ip_whitelist_v2", com.google.gson.Gson().toJson(list)).apply()
    }

    /** تحقق من IP مع دعم CIDR ومراعاة تاريخ الانتهاء */
    fun isIpAllowedEnhanced(clientIp: String): Boolean {
        val enhanced = getIpWhitelistEnhanced()
        if (enhanced.isEmpty()) return isIpAllowed(clientIp) // fallback للقديم

        val now = System.currentTimeMillis()
        return enhanced.any { entry ->
            val notExpired = entry.expiresAt == null || entry.expiresAt > now
            notExpired && (entry.ip == clientIp || entry.ip == "*" || matchesCidr(clientIp, entry.ip))
        }
    }

    private fun matchesCidr(ip: String, cidr: String): Boolean {
        if (!cidr.contains("/")) return false
        return try {
            val (network, prefixStr) = cidr.split("/")
            val prefix = prefixStr.toInt()
            val ipParts = ip.split(".").map { it.toInt() }
            val netParts = network.split(".").map { it.toInt() }
            if (ipParts.size != 4 || netParts.size != 4) return false
            val ipInt = ipParts.fold(0) { acc, v -> (acc shl 8) or v }
            val netInt = netParts.fold(0) { acc, v -> (acc shl 8) or v }
            val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
            (ipInt and mask) == (netInt and mask)
        } catch (e: Exception) { false }
    }

    // Direct Connection Configuration
    fun getDirectConnectionUrl(): String? {
        return prefs.getString("direct_connection_url", null)
    }

    fun setDirectConnectionUrl(url: String) {
        prefs.edit().putString("direct_connection_url", url).apply()
        Timber.i("Direct connection URL updated: $url")
    }

    fun clearDirectConnectionUrl() {
        prefs.edit().remove("direct_connection_url").apply()
        Timber.i("Direct connection URL cleared")
    }

    // Relay URL — الرابط الافتراضي يُستخدم إذا لم يُضبط المستخدم رابطاً مخصصاً
    companion object {
        const val DEFAULT_RELAY_URL = "wss://nopoh22-sms-relay-server.hf.space/device"
    }

    fun getRelayUrl(): String = prefs.getString("relay_url", null) ?: DEFAULT_RELAY_URL

    fun setRelayUrl(url: String) {
        if (url.isBlank() || url == DEFAULT_RELAY_URL) {
            prefs.edit().remove("relay_url").apply()
        } else {
            prefs.edit().putString("relay_url", url).apply()
        }
    }

    fun clearRelayUrl() {
        prefs.edit().remove("relay_url").apply()
    }

    fun isUsingDefaultRelayUrl(): Boolean = prefs.getString("relay_url", null) == null

    /** حفظ رابط الـ Relay Server المخصص (بعد نشره على Huggingface) */
    fun setCustomRelayServer(wsUrl: String, httpBase: String) {
        prefs.edit()
            .putString("relay_url", wsUrl)
            .putString("relay_http_base", httpBase)
            .apply()
        Timber.i("Custom relay server set: $wsUrl")
    }

    fun getRelayHttpBase(): String {
        return prefs.getString("relay_http_base", null)
            ?: DEFAULT_RELAY_URL
                .replace("wss://", "https://")
                .replace("ws://", "http://")
                .removeSuffix("/device")
    }

    /** يبني رابط API المباشر من الـ relay أو direct URL */
    fun buildDirectApiUrl(): String? {
        val direct = getDirectConnectionUrl()
        if (!direct.isNullOrBlank()) return direct.removeSuffix("/connect").let {
            if (it.contains("/api/v1")) it else "$it/api/v1"
        }
        val relay = getRelayUrl()
        return relay
            .replace("wss://", "https://")
            .replace("ws://", "http://")
            .removeSuffix("/device")
            .let { "$it/api/v1" }
    }

    /** يبني بطاقة الاتصال الكاملة */
    fun buildConnectionCard(): ConnectionCard? {
        val apiUrl = buildDirectApiUrl() ?: return null
        val key    = getApiKey()
        return ConnectionCard(apiUrl = apiUrl, apiKey = key)
    }
}

data class ConnectionCard(
    val apiUrl: String,
    val apiKey: String
)

data class RotatedKey(
    val newKey: String,
    val oldKey: String,
    val gracePeriodEnd: Long
)

data class IpWhitelistEntry(
    val ip: String,
    val description: String = "",
    val expiresAt: Long? = null
)

data class ApiKeyInfo(
    val key: String,
    val source: ApiKeySource,
    val isPersistent: Boolean,
    val androidId: String
)

enum class ApiKeySource {
    DERIVED,  // مشتق من Android ID (ثابت بعد إعادة التثبيت)
    MANUAL    // عيّنه المستخدم يدوياً
}