package com.sms.paymentgateway.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدير تحسين البطارية
 * يضمن عمل التطبيق 24/7 بدون انقطاع
 */
@Singleton
class BatteryOptimizationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BatteryOptimization"
        
        /** مدة Wake Lock الافتراضية (10 دقائق) */
        private const val DEFAULT_WAKE_LOCK_DURATION = 10 * 60 * 1000L
    }
    
    private var wakeLock: PowerManager.WakeLock? = null
    
    /**
     * التحقق من حالة Battery Optimization
     * @return true إذا كان التطبيق مستثنى من Battery Optimization
     */
    fun isBatteryOptimizationDisabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            Log.d(TAG, "Battery optimization disabled: $isIgnoring")
            isIgnoring
        } else {
            // الإصدارات القديمة لا تحتاج Battery Optimization
            Log.d(TAG, "Battery optimization not applicable (Android < M)")
            true
        }
    }
    
    /**
     * طلب استثناء من Battery Optimization
     * يفتح شاشة الإعدادات للمستخدم
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun requestBatteryOptimizationExemption() {
        try {
            if (isBatteryOptimizationDisabled()) {
                Log.i(TAG, "✅ Already exempted from battery optimization")
                return
            }
            
            val intent = Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            context.startActivity(intent)
            Log.i(TAG, "📱 Opened battery optimization settings")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error requesting battery optimization exemption", e)
            
            // Fallback: فتح إعدادات البطارية العامة
            try {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
                Log.i(TAG, "📱 Opened general battery optimization settings")
            } catch (e2: Exception) {
                Log.e(TAG, "❌ Error opening battery settings", e2)
            }
        }
    }
    
    /**
     * الحصول على Wake Lock
     * يمنع النظام من إيقاف التطبيق
     * 
     * @param duration مدة Wake Lock بالميلي ثانية (default: 10 دقائق)
     * @param tag وسم للتعريف
     * @return Wake Lock instance
     */
    fun acquireWakeLock(
        duration: Long = DEFAULT_WAKE_LOCK_DURATION,
        tag: String = "SMSGateway::WakeLock"
    ): PowerManager.WakeLock {
        try {
            // إطلاق Wake Lock القديم إن وجد
            releaseWakeLock()
            
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                tag
            ).apply {
                setReferenceCounted(false)
                acquire(duration)
            }
            
            Log.i(TAG, "🔋 Wake lock acquired for ${duration / 1000}s with tag: $tag")
            return wakeLock!!
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error acquiring wake lock", e)
            throw e
        }
    }
    
    /**
     * إطلاق Wake Lock
     */
    fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.i(TAG, "🔓 Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error releasing wake lock", e)
        }
    }
    
    /**
     * التحقق من حالة Wake Lock
     * @return true إذا كان Wake Lock نشطاً
     */
    fun isWakeLockHeld(): Boolean {
        return wakeLock?.isHeld ?: false
    }
    
    /**
     * تجديد Wake Lock
     * يُستخدم للحفاظ على التطبيق نشطاً لفترة أطول
     */
    fun renewWakeLock(duration: Long = DEFAULT_WAKE_LOCK_DURATION) {
        try {
            if (isWakeLockHeld()) {
                releaseWakeLock()
            }
            acquireWakeLock(duration)
            Log.i(TAG, "🔄 Wake lock renewed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error renewing wake lock", e)
        }
    }
    
    /**
     * الحصول على معلومات البطارية
     * @return معلومات شاملة عن حالة البطارية
     */
    fun getBatteryInfo(): BatteryInfo {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        
        return BatteryInfo(
            isOptimizationDisabled = isBatteryOptimizationDisabled(),
            isWakeLockHeld = isWakeLockHeld(),
            isPowerSaveMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                powerManager.isPowerSaveMode
            } else {
                false
            },
            isInteractive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                powerManager.isInteractive
            } else {
                @Suppress("DEPRECATION")
                powerManager.isScreenOn
            }
        )
    }
    
    /**
     * التحقق من إعدادات Auto-Start
     * ملاحظة: هذا يختلف حسب الشركة المصنعة
     */
    fun checkAutoStartSettings(): AutoStartStatus {
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        return when {
            manufacturer.contains("xiaomi") -> AutoStartStatus.XIAOMI
            manufacturer.contains("huawei") -> AutoStartStatus.HUAWEI
            manufacturer.contains("oppo") -> AutoStartStatus.OPPO
            manufacturer.contains("vivo") -> AutoStartStatus.VIVO
            manufacturer.contains("samsung") -> AutoStartStatus.SAMSUNG
            manufacturer.contains("oneplus") -> AutoStartStatus.ONEPLUS
            else -> AutoStartStatus.UNKNOWN
        }
    }
    
    /**
     * فتح إعدادات Auto-Start حسب الشركة المصنعة
     */
    fun openAutoStartSettings() {
        val status = checkAutoStartSettings()
        
        try {
            val intent = when (status) {
                AutoStartStatus.XIAOMI -> Intent().apply {
                    component = android.content.ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                }
                AutoStartStatus.HUAWEI -> Intent().apply {
                    component = android.content.ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                }
                AutoStartStatus.OPPO -> Intent().apply {
                    component = android.content.ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                }
                AutoStartStatus.VIVO -> Intent().apply {
                    component = android.content.ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                }
                AutoStartStatus.SAMSUNG -> Intent().apply {
                    component = android.content.ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    )
                }
                AutoStartStatus.ONEPLUS -> Intent().apply {
                    component = android.content.ComponentName(
                        "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                    )
                }
                else -> {
                    // Fallback: إعدادات التطبيق العامة
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                }
            }
            
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Log.i(TAG, "📱 Opened auto-start settings for ${status.name}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error opening auto-start settings", e)
            
            // Fallback: إعدادات التطبيق
            try {
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
                Log.i(TAG, "📱 Opened app settings as fallback")
            } catch (e2: Exception) {
                Log.e(TAG, "❌ Error opening app settings", e2)
            }
        }
    }
}

/**
 * معلومات البطارية
 */
data class BatteryInfo(
    /** هل التطبيق مستثنى من Battery Optimization */
    val isOptimizationDisabled: Boolean,
    
    /** هل Wake Lock نشط */
    val isWakeLockHeld: Boolean,
    
    /** هل وضع توفير الطاقة مفعّل */
    val isPowerSaveMode: Boolean,
    
    /** هل الشاشة نشطة */
    val isInteractive: Boolean
)

/**
 * حالة Auto-Start حسب الشركة المصنعة
 */
enum class AutoStartStatus {
    XIAOMI,
    HUAWEI,
    OPPO,
    VIVO,
    SAMSUNG,
    ONEPLUS,
    UNKNOWN
}
