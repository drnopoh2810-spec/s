package com.sms.paymentgateway.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
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

    private val CHANNEL_ID      = "payment_gateway_channel"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        Timber.i("🚀 بدء خدمة بوابة الدفع")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        runCatching { apiServer.start()
            Timber.i("✅ خادم API يعمل على المنفذ 8080") }
            .onFailure { Timber.e(it, "❌ فشل تشغيل خادم API") }

        CoroutineScope(Dispatchers.IO).launch {
            runCatching { cleanupManager.startPeriodicCleanup() }
                .onFailure { Timber.e(it, "❌ فشل تشغيل مدير التنظيف") }
        }

        CoroutineScope(Dispatchers.IO).launch {
            runCatching { relayClient.start()
                Timber.i("✅ عميل Relay يعمل") }
                .onFailure { Timber.e(it, "❌ فشل تشغيل عميل Relay") }
        }

        runCatching { connectionMonitor.startMonitoring()
            Timber.i("✅ مراقبة الاتصال نشطة") }
            .onFailure { Timber.e(it, "❌ فشل تشغيل مراقبة الاتصال") }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("🔄 إعادة تشغيل الخدمة")
        if (!relayClient.isStarted()) {
            CoroutineScope(Dispatchers.IO).launch { relayClient.start() }
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
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "بوابة دفع SMS",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "تشغيل خدمة بوابة الدفع في الخلفية" }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("بوابة دفع SMS")
            .setContentText("الخدمة تعمل وتراقب الرسائل")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
}
