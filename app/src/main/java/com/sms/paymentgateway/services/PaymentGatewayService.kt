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
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class PaymentGatewayService : Service() {

    @Inject
    lateinit var apiServer: ApiServer

    @Inject
    lateinit var cleanupManager: CleanupManager

    private val CHANNEL_ID = "payment_gateway_channel"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        Timber.i("PaymentGatewayService created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Start API Server
        try {
            apiServer.start()
            Timber.i("API Server started on port 8080")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start API Server")
        }

        // Start periodic cleanup
        try {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                cleanupManager.startPeriodicCleanup(intervalHours = 1)
            }
            Timber.i("Cleanup manager started")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start cleanup manager")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("PaymentGatewayService started")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        apiServer.stop()
        cleanupManager.stopCleanup()
        Timber.i("PaymentGatewayService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMS Payment Gateway",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the payment gateway service running"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Payment Gateway")
            .setContentText("Service is running and monitoring SMS")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
