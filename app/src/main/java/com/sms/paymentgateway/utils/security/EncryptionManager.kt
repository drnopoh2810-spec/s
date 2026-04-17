package com.sms.paymentgateway.utils.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدير التشفير End-to-End
 * 
 * يستخدم AES-256-GCM لتشفير بيانات الدفع الحساسة
 * 
 * الميزات:
 * - AES-256-GCM encryption
 * - Secure key generation
 * - Key rotation support
 * - IV (Initialization Vector) لكل عملية تشفير
 */
@Singleton
class EncryptionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("encryption_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "EncryptionManager"
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_SIZE = 256 // bits
        private const val GCM_TAG_LENGTH = 128 // bits
        private const val IV_LENGTH = 12 // bytes (96 bits recommended for GCM)
    }
    
    /**
     * الحصول على مفتاح التشفير الرئيسي
     * يتم إنشاؤه تلقائياً إذا لم يكن موجوداً
     */
    private fun getMasterKey(): SecretKey {
        val keyString = prefs.getString("master_key", null)
        
        return if (keyString != null) {
            // استرجاع المفتاح الموجود
            val keyBytes = Base64.decode(keyString, Base64.NO_WRAP)
            SecretKeySpec(keyBytes, "AES")
        } else {
            // إنشاء مفتاح جديد
            generateAndSaveMasterKey()
        }
    }
    
    /**
     * إنشاء وحفظ مفتاح تشفير جديد
     */
    private fun generateAndSaveMasterKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(KEY_SIZE, SecureRandom())
        val secretKey = keyGenerator.generateKey()
        
        // حفظ المفتاح
        val keyString = Base64.encodeToString(secretKey.encoded, Base64.NO_WRAP)
        prefs.edit().putString("master_key", keyString).apply()
        
        Timber.i("$TAG: Generated new master encryption key")
        return secretKey
    }
    
    /**
     * تشفير نص
     * 
     * @param plainText النص المراد تشفيره
     * @return النص المشفر مع IV (مفصولة بـ :)
     */
    fun encrypt(plainText: String): String {
        return try {
            val masterKey = getMasterKey()
            
            // إنشاء IV عشوائي
            val iv = ByteArray(IV_LENGTH)
            SecureRandom().nextBytes(iv)
            
            // إعداد Cipher
            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, gcmSpec)
            
            // التشفير
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            
            // دمج IV مع البيانات المشفرة
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            
            "$ivBase64:$encryptedBase64"
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error encrypting data")
            throw EncryptionException("Failed to encrypt data", e)
        }
    }
    
    /**
     * فك تشفير نص
     * 
     * @param encryptedText النص المشفر (مع IV)
     * @return النص الأصلي
     */
    fun decrypt(encryptedText: String): String {
        return try {
            val masterKey = getMasterKey()
            
            // فصل IV عن البيانات المشفرة
            val parts = encryptedText.split(":")
            if (parts.size != 2) {
                throw EncryptionException("Invalid encrypted data format")
            }
            
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            
            // إعداد Cipher
            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, masterKey, gcmSpec)
            
            // فك التشفير
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error decrypting data")
            throw EncryptionException("Failed to decrypt data", e)
        }
    }
    
    /**
     * تشفير بيانات JSON
     * 
     * @param jsonData البيانات بصيغة JSON
     * @return JSON مشفر
     */
    fun encryptJson(jsonData: String): String {
        return encrypt(jsonData)
    }
    
    /**
     * فك تشفير بيانات JSON
     * 
     * @param encryptedJson JSON مشفر
     * @return JSON الأصلي
     */
    fun decryptJson(encryptedJson: String): String {
        return decrypt(encryptedJson)
    }
    
    /**
     * تشفير بيانات حساسة (مثل أرقام الهواتف، المبالغ)
     * 
     * @param sensitiveData البيانات الحساسة
     * @return البيانات المشفرة
     */
    fun encryptSensitiveData(sensitiveData: Map<String, Any>): String {
        val jsonString = com.google.gson.Gson().toJson(sensitiveData)
        return encrypt(jsonString)
    }
    
    /**
     * فك تشفير بيانات حساسة
     * 
     * @param encryptedData البيانات المشفرة
     * @return البيانات الأصلية
     */
    fun decryptSensitiveData(encryptedData: String): Map<String, Any> {
        val jsonString = decrypt(encryptedData)
        return com.google.gson.Gson().fromJson(jsonString, Map::class.java) as Map<String, Any>
    }
    
    /**
     * تدوير المفتاح (Key Rotation)
     * إنشاء مفتاح جديد واستبدال القديم
     * 
     * @return المفتاح الجديد (Base64)
     */
    fun rotateKey(): String {
        // حفظ المفتاح القديم للنسخ الاحتياطي
        val oldKey = prefs.getString("master_key", null)
        if (oldKey != null) {
            prefs.edit().putString("master_key_backup", oldKey).apply()
            Timber.i("$TAG: Old key backed up")
        }
        
        // إنشاء مفتاح جديد
        val newKey = generateAndSaveMasterKey()
        val keyString = Base64.encodeToString(newKey.encoded, Base64.NO_WRAP)
        
        Timber.i("$TAG: Key rotated successfully")
        return keyString
    }
    
    /**
     * استرجاع المفتاح القديم من النسخة الاحتياطية
     */
    fun restoreBackupKey(): Boolean {
        val backupKey = prefs.getString("master_key_backup", null)
        return if (backupKey != null) {
            prefs.edit().putString("master_key", backupKey).apply()
            Timber.i("$TAG: Backup key restored")
            true
        } else {
            Timber.w("$TAG: No backup key found")
            false
        }
    }
    
    /**
     * التحقق من وجود مفتاح تشفير
     */
    fun hasEncryptionKey(): Boolean {
        return prefs.getString("master_key", null) != null
    }
    
    /**
     * حذف مفتاح التشفير (استخدم بحذر!)
     */
    fun deleteEncryptionKey() {
        prefs.edit()
            .remove("master_key")
            .remove("master_key_backup")
            .apply()
        Timber.w("$TAG: Encryption keys deleted")
    }
    
    /**
     * الحصول على معلومات المفتاح
     */
    fun getKeyInfo(): EncryptionKeyInfo {
        val hasKey = hasEncryptionKey()
        val hasBackup = prefs.getString("master_key_backup", null) != null
        
        return EncryptionKeyInfo(
            hasKey = hasKey,
            hasBackup = hasBackup,
            algorithm = ALGORITHM,
            keySize = KEY_SIZE
        )
    }
    
    /**
     * تمكين/تعطيل التشفير
     */
    fun setEncryptionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("encryption_enabled", enabled).apply()
        Timber.i("$TAG: Encryption ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * التحقق من تمكين التشفير
     */
    fun isEncryptionEnabled(): Boolean {
        return prefs.getBoolean("encryption_enabled", false)
    }
}

/**
 * معلومات مفتاح التشفير
 */
data class EncryptionKeyInfo(
    val hasKey: Boolean,
    val hasBackup: Boolean,
    val algorithm: String,
    val keySize: Int
)

/**
 * استثناء التشفير
 */
class EncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)
