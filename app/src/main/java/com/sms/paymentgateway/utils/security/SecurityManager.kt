package com.sms.paymentgateway.utils.security

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
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
 * API Key ثابت بعد إعادة التثبيت - مشتق من Android ID + App Signature
 */
@Singleton
class SecurityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)

    // ─── API Key ──────────────────────────────────────────────────────────────

    private val _apiKey: String
        get() {
            val manual = prefs.getString("api_key_manual", null)
            if (!manual.isNullOrBlank()) return manual
            return getDerivedApiKey()
        }

    private val _hmacSecret: String
        get() = prefs.getString("hmac_secret", null) ?: generateAndSaveHmacSecret()

    private fun getDerivedApiKey(): String {
        val cached = prefs.getString("api_key_derived", null)
        if (!cached.isNullOrBlank()) return cached

        val androidId = getAndroidId()
        val appSignature = getAppSignature()
        val staticSalt = "sms_gw_v3_unique_per_build"
        val derived = sha256("$androidId:$appSignature:$staticSalt").take(32)

        prefs.edit().putString("api_key_derived", derived).apply()
        Timber.i("API Key derived (unique per device + build)")
        return derived
    }

    private fun getAppSignature(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pi = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val sig = pi.signingInfo?.apkContentsSigners?.firstOrNull()
                if (sig != null) sha256(sig.toByteArray().toString()).take(16)
                else sha256(context.packageName).take(16)
            } else {
                @Suppress("DEPRECATION")
                val pi = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
                @Suppress("DEPRECATION")
                val sig = pi.signatures?.firstOrNull()
                if (sig != null) sha256(sig.toByteArray().toString()).take(16)
                else sha256(context.packageName).take(16)
            }
        } catch (e: Exception) {
            sha256(context.packageName).take(16)
        }
    }

    private fun getAndroidId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "fallback_id"

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    // ─── Public API Key Methods ───────────────────────────────────────────────

    fun getApiKey(): String = _apiKey

    fun validateApiKey(providedKey: String?): Boolean =
        providedKey != null && providedKey == _apiKey

    fun validateApiKeyWithGrace(providedKey: String?): Boolean {
        if (providedKey == null) return false
        if (providedKey == _apiKey) return true
        val oldKey = prefs.getString("api_key_old", null)
        val graceEnd = prefs.getLong("api_key_grace_end", 0L)
        if (oldKey != null && providedKey == oldKey && System.currentTimeMillis() < graceEnd) {
            Timber.w("Request using old API key (grace period active)")
            return true
        }
        return false
    }

    fun setManualApiKey(key: String) {
        if (key.length < 16) { Timber.w("API Key too short"); return }
        prefs.edit().putString("api_key_manual", key).apply()
        Timber.i("Manual API Key set")
    }

    fun clearManualApiKey() {
        prefs.edit().remove("api_key_manual").apply()
        Timber.i("Reverted to derived key")
    }

    fun isUsingManualKey(): Boolean =
        !prefs.getString("api_key_manual", null).isNullOrBlank()

    fun regenerateApiKey(): String {
        val newKey = generateRandomString(32)
        setManualApiKey(newKey)
        Timber.i("API Key regenerated")
        return newKey
    }

    fun rotateApiKey(): RotatedKey {
        val oldKey = _apiKey
        val newKey = generateRandomString(32)
        val gracePeriodEnd = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
        prefs.edit()
            .putString("api_key_manual", newKey)
            .putString("api_key_old", oldKey)
            .putLong("api_key_grace_end", gracePeriodEnd)
            .apply()
        Timber.i("API Key rotated with 24h grace period")
        return RotatedKey(newKey = newKey, oldKey = oldKey, gracePeriodEnd = gracePeriodEnd)
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

    fun getApiKeyInfo(): ApiKeyInfo = ApiKeyInfo(
        key = _apiKey,
        source = if (isUsingManualKey()) ApiKeySource.MANUAL else ApiKeySource.DERIVED,
        isPersistent = true,
        androidId = getAndroidId().take(8) + "..."
    )

    // ─── HMAC ─────────────────────────────────────────────────────────────────

    fun generateHmacSignature(data: String): String = hmac(data, _hmacSecret)

    fun generateHmacSignature(data: String, secret: String): String = hmac(data, secret)

    fun verifyHmacSignature(data: String, signature: String): Boolean =
        generateHmacSignature(data).equals(signature, ignoreCase = true)

    private fun hmac(data: String, secret: String): String {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
            mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "Error generating HMAC")
            ""
        }
    }

    private fun generateAndSaveHmacSecret(): String {
        val secret = generateRandomString(64)
        prefs.edit().putString("hmac_secret", secret).apply()
        return secret
    }

    private fun generateRandomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }

    // ─── IP Whitelist ─────────────────────────────────────────────────────────

    fun isIpAllowed(ip: String): Boolean {
        val whitelist = getIpWhitelist()
        return whitelist.isEmpty() || whitelist.contains(ip) || whitelist.contains("*")
    }

    fun getIpWhitelist(): Set<String> =
        prefs.getStringSet("ip_whitelist", emptySet()) ?: emptySet()

    fun addIpToWhitelist(ip: String) {
        val w = getIpWhitelist().toMutableSet().also { it.add(ip) }
        prefs.edit().putStringSet("ip_whitelist", w).apply()
    }

    fun removeIpFromWhitelist(ip: String) {
        val w = getIpWhitelist().toMutableSet().also { it.remove(ip) }
        prefs.edit().putStringSet("ip_whitelist", w).apply()
    }

    fun clearIpWhitelist() = prefs.edit().remove("ip_whitelist").apply()

    fun addIpToWhitelistEnhanced(ip: String, description: String = "", expiresAt: Long? = null) {
        val list = getIpWhitelistEnhanced().toMutableList()
        list.removeAll { it.ip == ip }
        list.add(IpWhitelistEntry(ip, description, expiresAt))
        saveIpWhitelistEnhanced(list)
    }

    fun removeIpFromWhitelistEnhanced(ip: String) =
        saveIpWhitelistEnhanced(getIpWhitelistEnhanced().filter { it.ip != ip })

    fun getIpWhitelistEnhanced(): List<IpWhitelistEntry> {
        val json = prefs.getString("ip_whitelist_v2", null) ?: return emptyList()
        return try {
            com.google.gson.Gson().fromJson(json, Array<IpWhitelistEntry>::class.java).toList()
        } catch (e: Exception) { emptyList() }
    }

    private fun saveIpWhitelistEnhanced(list: List<IpWhitelistEntry>) =
        prefs.edit().putString("ip_whitelist_v2", com.google.gson.Gson().toJson(list)).apply()

    fun isIpAllowedEnhanced(clientIp: String): Boolean {
        val enhanced = getIpWhitelistEnhanced()
        if (enhanced.isEmpty()) return isIpAllowed(clientIp)
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
            val ipInt = ip.split(".").map { it.toInt() }.fold(0) { a, v -> (a shl 8) or v }
            val netInt = network.split(".").map { it.toInt() }.fold(0) { a, v -> (a shl 8) or v }
            val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
            (ipInt and mask) == (netInt and mask)
        } catch (e: Exception) { false }
    }

    // ─── Webhook ──────────────────────────────────────────────────────────────

    fun getWebhookUrl(): String? = prefs.getString("webhook_url", null)
    fun setWebhookUrl(url: String) = prefs.edit().putString("webhook_url", url).apply()
    fun getWebhookSecret(): String? = prefs.getString("webhook_secret", null)
    fun setWebhookSecret(secret: String) = prefs.edit().putString("webhook_secret", secret).apply()

    // ─── Direct Connection ────────────────────────────────────────────────────

    fun getDirectConnectionUrl(): String? = prefs.getString("direct_connection_url", null)
    fun setDirectConnectionUrl(url: String) = prefs.edit().putString("direct_connection_url", url).apply()
    fun clearDirectConnectionUrl() = prefs.edit().remove("direct_connection_url").apply()

    // ─── Relay URL ────────────────────────────────────────────────────────────

    companion object {
        const val DEFAULT_RELAY_URL = "wss://nopoh22-sms-relay-server.hf.space/device"
    }

    fun getRelayUrl(): String = prefs.getString("relay_url", null) ?: DEFAULT_RELAY_URL

    fun setRelayUrl(url: String) {
        if (url.isBlank() || url == DEFAULT_RELAY_URL)
            prefs.edit().remove("relay_url").apply()
        else
            prefs.edit().putString("relay_url", url).apply()
    }

    fun clearRelayUrl() = prefs.edit().remove("relay_url").apply()
    fun isUsingDefaultRelayUrl(): Boolean = prefs.getString("relay_url", null) == null

    fun setCustomRelayServer(wsUrl: String, httpBase: String) {
        prefs.edit().putString("relay_url", wsUrl).putString("relay_http_base", httpBase).apply()
    }

    fun getRelayHttpBase(): String =
        prefs.getString("relay_http_base", null)
            ?: DEFAULT_RELAY_URL.replace("wss://", "https://").replace("ws://", "http://").removeSuffix("/device")

    fun buildDirectApiUrl(): String? {
        val direct = getDirectConnectionUrl()
        if (!direct.isNullOrBlank()) return direct.removeSuffix("/connect").let {
            if (it.contains("/api/v1")) it else "$it/api/v1"
        }
        return getRelayUrl()
            .replace("wss://", "https://").replace("ws://", "http://")
            .removeSuffix("/device").let { "$it/api/v1" }
    }

    fun buildConnectionCard(): ConnectionCard? {
        val apiUrl = buildDirectApiUrl() ?: return null
        return ConnectionCard(apiUrl = apiUrl, apiKey = getApiKey())
    }
}

// ─── Data Classes ─────────────────────────────────────────────────────────────

data class ConnectionCard(val apiUrl: String, val apiKey: String)

data class RotatedKey(val newKey: String, val oldKey: String, val gracePeriodEnd: Long)

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

enum class ApiKeySource { DERIVED, MANUAL }
