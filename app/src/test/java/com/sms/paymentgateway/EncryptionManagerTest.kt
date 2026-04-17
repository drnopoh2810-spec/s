package com.sms.paymentgateway

import android.content.Context
import android.content.SharedPreferences
import com.sms.paymentgateway.utils.security.EncryptionException
import com.sms.paymentgateway.utils.security.EncryptionManager
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EncryptionManagerTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var sharedPreferences: SharedPreferences

    @Mock
    private lateinit var editor: SharedPreferences.Editor

    private lateinit var encryptionManager: EncryptionManager

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        `when`(context.getSharedPreferences("encryption_prefs", Context.MODE_PRIVATE))
            .thenReturn(sharedPreferences)
        `when`(sharedPreferences.edit()).thenReturn(editor)
        `when`(editor.putString(anyString(), anyString())).thenReturn(editor)
        `when`(editor.putBoolean(anyString(), anyBoolean())).thenReturn(editor)
        `when`(editor.remove(anyString())).thenReturn(editor)
        
        encryptionManager = EncryptionManager(context)
    }

    @Test
    fun `encrypt and decrypt should return original text`() {
        // Arrange
        val plainText = "Hello, World!"
        
        // Act
        val encrypted = encryptionManager.encrypt(plainText)
        val decrypted = encryptionManager.decrypt(encrypted)
        
        // Assert
        assertEquals(plainText, decrypted)
        assertNotEquals(plainText, encrypted)
    }

    @Test
    fun `encrypt should produce different output for same input`() {
        // Arrange
        val plainText = "Test message"
        
        // Act
        val encrypted1 = encryptionManager.encrypt(plainText)
        val encrypted2 = encryptionManager.encrypt(plainText)
        
        // Assert
        assertNotEquals(encrypted1, encrypted2) // Different IV each time
    }

    @Test
    fun `encrypt should handle empty string`() {
        // Arrange
        val plainText = ""
        
        // Act
        val encrypted = encryptionManager.encrypt(plainText)
        val decrypted = encryptionManager.decrypt(encrypted)
        
        // Assert
        assertEquals(plainText, decrypted)
    }

    @Test
    fun `encrypt should handle special characters`() {
        // Arrange
        val plainText = "مرحباً! 你好! 🎉 @#$%^&*()"
        
        // Act
        val encrypted = encryptionManager.encrypt(plainText)
        val decrypted = encryptionManager.decrypt(encrypted)
        
        // Assert
        assertEquals(plainText, decrypted)
    }

    @Test
    fun `encrypt should handle long text`() {
        // Arrange
        val plainText = "A".repeat(10000)
        
        // Act
        val encrypted = encryptionManager.encrypt(plainText)
        val decrypted = encryptionManager.decrypt(encrypted)
        
        // Assert
        assertEquals(plainText, decrypted)
    }

    @Test(expected = EncryptionException::class)
    fun `decrypt should throw exception for invalid data`() {
        // Arrange
        val invalidData = "invalid:data"
        
        // Act
        encryptionManager.decrypt(invalidData)
    }

    @Test(expected = EncryptionException::class)
    fun `decrypt should throw exception for malformed data`() {
        // Arrange
        val malformedData = "onlyonepart"
        
        // Act
        encryptionManager.decrypt(malformedData)
    }

    @Test
    fun `encryptJson should encrypt and decrypt JSON correctly`() {
        // Arrange
        val jsonData = """{"name":"John","age":30,"city":"Cairo"}"""
        
        // Act
        val encrypted = encryptionManager.encryptJson(jsonData)
        val decrypted = encryptionManager.decryptJson(encrypted)
        
        // Assert
        assertEquals(jsonData, decrypted)
    }

    @Test
    fun `encryptSensitiveData should encrypt and decrypt map correctly`() {
        // Arrange
        val sensitiveData = mapOf(
            "phoneNumber" to "01012345678",
            "amount" to 500.0,
            "transactionId" to "TX123"
        )
        
        // Act
        val encrypted = encryptionManager.encryptSensitiveData(sensitiveData)
        val decrypted = encryptionManager.decryptSensitiveData(encrypted)
        
        // Assert
        assertEquals(sensitiveData["phoneNumber"], decrypted["phoneNumber"])
        assertEquals(sensitiveData["amount"], decrypted["amount"])
        assertEquals(sensitiveData["transactionId"], decrypted["transactionId"])
    }

    @Test
    fun `setEncryptionEnabled should update preference`() {
        // Act
        encryptionManager.setEncryptionEnabled(true)
        
        // Assert
        verify(editor).putBoolean("encryption_enabled", true)
        verify(editor).apply()
    }

    @Test
    fun `isEncryptionEnabled should return false by default`() {
        // Arrange
        `when`(sharedPreferences.getBoolean("encryption_enabled", false)).thenReturn(false)
        
        // Act
        val isEnabled = encryptionManager.isEncryptionEnabled()
        
        // Assert
        assertFalse(isEnabled)
    }

    @Test
    fun `isEncryptionEnabled should return true when enabled`() {
        // Arrange
        `when`(sharedPreferences.getBoolean("encryption_enabled", false)).thenReturn(true)
        
        // Act
        val isEnabled = encryptionManager.isEncryptionEnabled()
        
        // Assert
        assertTrue(isEnabled)
    }

    @Test
    fun `hasEncryptionKey should return false when no key exists`() {
        // Arrange
        `when`(sharedPreferences.getString("master_key", null)).thenReturn(null)
        
        // Act
        val hasKey = encryptionManager.hasEncryptionKey()
        
        // Assert
        assertFalse(hasKey)
    }

    @Test
    fun `hasEncryptionKey should return true when key exists`() {
        // Arrange
        `when`(sharedPreferences.getString("master_key", null)).thenReturn("some_key")
        
        // Act
        val hasKey = encryptionManager.hasEncryptionKey()
        
        // Assert
        assertTrue(hasKey)
    }

    @Test
    fun `getKeyInfo should return correct information`() {
        // Arrange
        `when`(sharedPreferences.getString("master_key", null)).thenReturn("key")
        `when`(sharedPreferences.getString("master_key_backup", null)).thenReturn("backup")
        
        // Act
        val keyInfo = encryptionManager.getKeyInfo()
        
        // Assert
        assertTrue(keyInfo.hasKey)
        assertTrue(keyInfo.hasBackup)
        assertEquals("AES/GCM/NoPadding", keyInfo.algorithm)
        assertEquals(256, keyInfo.keySize)
    }

    @Test
    fun `deleteEncryptionKey should remove keys`() {
        // Act
        encryptionManager.deleteEncryptionKey()
        
        // Assert
        verify(editor).remove("master_key")
        verify(editor).remove("master_key_backup")
        verify(editor).apply()
    }
}
