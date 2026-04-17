package com.sms.paymentgateway.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.sms.paymentgateway.utils.BatteryOptimizationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class PaymentGatewayService : Service() {

    @Inject lateinit var apiServer: ApiServer
    @Inject lateinit var cleanupManager: CleanupManager
    @Inject lateinit var relayClient: RelayClient
    @Inject lateinit var connectionMonitor: ConnectionMonitor
    @Inject lateinit var batteryOptimizationManager: BatteryOptimizationManager
    @Inject lateinit var expirationChecker: ExpirationChecker
    @Inject lateinit var smartTunnelManager: SmartTunnelManager

    private val CHANNEL_ID      = "payment_gateway_channel"
    private val NOTIFICATION_ID = 1
    
    /** Wake Lock للحفاظ على الخدمة نشطة */
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Timber.i("🚀 بدء خدمة بوابة الدفع - Huggingface Relay")
        
        // الحصول على Wake Lock
        acquireWakeLock()
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // بدء خادم API المحلي
        runCatching { 
            apiServer.start()
            Timber.i("✅ خادم API المحلي يعمل على المنفذ 8080") 
        }.onFailure { 
            Timber.e(it, "❌ فشل تشغيل خادم API") 
        }

        // بدء مدير التنظيف
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { 
                cleanupManager.startPeriodicCleanup() 
            }.onFailure { 
                Timber.e(it, "❌ فشل تشغيل مدير التنظيف") 
            }
        }

        // بدء RelayClient للاتصال بـ Huggingface Relay
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { 
                // ربط معالج الطلبات بـ ApiServer
                relayClient.tunnelRequestHandler = { method, path, headers, body ->
                    apiServer.handleTunnelRequest(method, path, headers, body)
                }
                relayClient.start()
                Timber.i("✅ RelayClient متصل بـ Huggingface Relay") 
            }.onFailure { 
                Timber.e(it, "❌ فشل الاتصال بـ Relay Server") 
            }
        }

        // بدء SmartTunnel - يولد رابط https عام
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                smartTunnelManager.requestHandler = { method, path, headers, body ->
                    apiServer.handleTunnelRequest(method, path, headers, body)
                }
                smartTunnelManager.start()
                Timber.i("✅ SmartTunnel نشط - رابط عام جاهز")
            }.onFailure {
                Timber.e(it, "❌ فشل تشغيل SmartTunnel")
            }
        }

        // بدء مراقبة الاتصال
        runCatching { 
            connectionMonitor.startMonitoring()
            Timber.i("✅ مراقبة الاتصال نشطة") 
        }.onFailure { 
            Timber.e(it, "❌ فشل تشغيل مراقبة الاتصال") 
        }
        
        // بدء فحص انتهاء صلاحية المعاملات
        runCatching {
            expirationChecker.startPeriodicCheck()
            Timber.i("✅ فحص انتهاء الصلاحية نشط")
        }.onFailure {
            Timber.e(it, "❌ فشل تشغيل فحص انتهاء الصلاحية")
        }
        
        // تجديد Wake Lock كل 5 دقائق
        scheduleWakeLockRenewal()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("🔄 إعادة تشغيل الخدمة")
        
        // التأكد من Wake Lock نشط
        if (!batteryOptimizationManager.isWakeLockHeld()) {
            acquireWakeLock()
        }
        
        // التأكد من أن RelayClient يعمل
        CoroutineScope(Dispatchers.IO).launch {
            if (!relayClient.isConnected()) {
                relayClient.connect()
            }
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("🛑 إيقاف خدمة بوابة الدفع")
        
        runCatching { apiServer.stop() }
        runCatching { cleanupManager.stopCleanup() }
        runCatching { relayClient.stop() }
        runCatching { connectionMonitor.stopMonitoring() }
        runCatching { expirationChecker.stopPeriodicCheck() }
        runCatching { smartTunnelManager.stop() }
        
        // إطلاق Wake Lock
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    /**
     * الحصول على Wake Lock
     */
    private fun acquireWakeLock() {
        try {
            wakeLock = batteryOptimizationManager.acquireWakeLock(
                duration = 10 * 60 * 1000L, // 10 دقائق
                tag = "PaymentGatewayService::WakeLock"
            )
            Timber.i("🔋 Wake lock acquired for service")
        } catch (e: Exception) {
            Timber.e(e, "❌ Error acquiring wake lock")
        }
    }
    
    /**
     * إطلاق Wake Lock
     */
    private fun releaseWakeLock() {
        try {
            batteryOptimizationManager.releaseWakeLock()
            wakeLock = null
            Timber.i("🔓 Wake lock released")
        } catch (e: Exception) {
            Timber.e(e, "❌ Error releasing wake lock")
        }
    }
    
    /**
     * جدولة تجديد Wake Lock
     */
    private fun scheduleWakeLockRenewal() {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                kotlinx.coroutines.delay(5 * 60 * 1000L) // كل 5 دقائق
                
                try {
                    if (batteryOptimizationManager.isWakeLockHeld()) {
                        batteryOptimizationManager.renewWakeLock()
                        Timber.d("🔄 Wake lock renewed")
                    } else {
                        acquireWakeLock()
                        Timber.w("⚠️ Wake lock was lost, re-acquired")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "❌ Error renewing wake lock")
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "بوابة دفع SMS - Huggingface Relay",
                NotificationManager.IMPORTANCE_LOW
            ).apply { 
                description = "تشغيل خدمة بوابة الدفع مع Huggingface Relay في الخلفية" 
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val status = if (relayClient.isConnected()) "متصل ✓" else "غير متصل"
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("بوابة دفع SMS - Huggingface Relay")
            .setContentText("الخدمة تعمل: $status")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
