package com.sms.paymentgateway.utils.security

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs: SharedPreferences = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
    
    private val _apiKey: String
        get() = prefs.getString("api_key", null) ?: generateAndSaveApiKey()

    private val _hmacSecret: String
        get() = prefs.getString("hmac_secret", null) ?: generateAndSaveHmacSecret()

    fun validateApiKey(providedKey: String?): Boolean {
        if (providedKey == null) return false
        return providedKey == _apiKey
    }

    fun getApiKey(): String = _apiKey

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

    fun verifyHmacSignature(data: String, signature: String): Boolean {
        val computed = generateHmacSignature(data)
        return computed.equals(signature, ignoreCase = true)
    }

    private fun generateAndSaveApiKey(): String {
        val key = generateRandomString(32)
        prefs.edit().putString("api_key", key).apply()
        Timber.i("Generated new API Key")
        return key
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

    // Relay URL (kept for compatibility)
    fun getRelayUrl(): String? = prefs.getString("relay_url", null)
    fun setRelayUrl(url: String) {
        prefs.edit().putString("relay_url", url).apply()
    }
    fun clearRelayUrl() {
        prefs.edit().remove("relay_url").apply()
    }

    /** يبني رابط API المباشر من الـ relay أو direct URL */
    fun buildDirectApiUrl(): String? {
        val direct = getDirectConnectionUrl()
        if (!direct.isNullOrBlank()) return direct.removeSuffix("/connect").let {
            if (it.contains("/api/v1")) it else "$it/api/v1"
        }
        val relay = getRelayUrl() ?: return null
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