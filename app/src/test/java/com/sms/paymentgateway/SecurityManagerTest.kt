package com.sms.paymentgateway

import android.content.Context
import android.content.SharedPreferences
import com.sms.paymentgateway.utils.security.SecurityManager
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SecurityManagerTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var securityManager: SecurityManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putStringSet(any(), any()) } returns editor
        every { editor.apply() } just Runs

        securityManager = SecurityManager(context)
    }

    @Test
    fun `API key is generated on first access`() {
        every { sharedPreferences.getString("api_key", null) } returns null

        val apiKey = securityManager.getApiKey()

        assertNotNull(apiKey)
        assertEquals(32, apiKey.length)
        verify { editor.putString("api_key", any()) }
    }

    @Test
    fun `validate API key returns true for correct key`() {
        val testKey = "test_api_key_12345"
        every { sharedPreferences.getString("api_key", null) } returns testKey

        val result = securityManager.validateApiKey(testKey)

        assertTrue(result)
    }

    @Test
    fun `validate API key returns false for incorrect key`() {
        every { sharedPreferences.getString("api_key", null) } returns "correct_key"

        val result = securityManager.validateApiKey("wrong_key")

        assertFalse(result)
    }

    @Test
    fun `validate API key returns false for null key`() {
        every { sharedPreferences.getString("api_key", null) } returns "correct_key"

        val result = securityManager.validateApiKey(null)

        assertFalse(result)
    }

    @Test
    fun `HMAC signature is generated correctly`() {
        every { sharedPreferences.getString("hmac_secret", null) } returns "test_secret"

        val data = "test data"
        val signature = securityManager.generateHmacSignature(data)

        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
        assertEquals(64, signature.length) // SHA-256 produces 64 hex characters
    }

    @Test
    fun `HMAC signature verification works`() {
        every { sharedPreferences.getString("hmac_secret", null) } returns "test_secret"

        val data = "test data"
        val signature = securityManager.generateHmacSignature(data)
        val isValid = securityManager.verifyHmacSignature(data, signature)

        assertTrue(isValid)
    }

    @Test
    fun `HMAC signature verification fails for wrong signature`() {
        every { sharedPreferences.getString("hmac_secret", null) } returns "test_secret"

        val data = "test data"
        val wrongSignature = "wrong_signature"
        val isValid = securityManager.verifyHmacSignature(data, wrongSignature)

        assertFalse(isValid)
    }

    @Test
    fun `IP whitelist allows all when empty`() {
        every { sharedPreferences.getStringSet("ip_whitelist", emptySet()) } returns emptySet()

        val result = securityManager.isIpAllowed("192.168.1.1")

        assertTrue(result)
    }

    @Test
    fun `IP whitelist allows whitelisted IP`() {
        every { sharedPreferences.getStringSet("ip_whitelist", emptySet()) } returns setOf("192.168.1.1")

        val result = securityManager.isIpAllowed("192.168.1.1")

        assertTrue(result)
    }

    @Test
    fun `IP whitelist blocks non-whitelisted IP`() {
        every { sharedPreferences.getStringSet("ip_whitelist", emptySet()) } returns setOf("192.168.1.1")

        val result = securityManager.isIpAllowed("192.168.1.2")

        assertFalse(result)
    }

    @Test
    fun `IP whitelist allows all with wildcard`() {
        every { sharedPreferences.getStringSet("ip_whitelist", emptySet()) } returns setOf("*")

        val result = securityManager.isIpAllowed("192.168.1.100")

        assertTrue(result)
    }
}
