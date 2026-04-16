package com.sms.paymentgateway.services

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionMonitor @Inject constructor(
    private val context: Context,
    private val relayClient: RelayClient
) {
    private val TAG = "ConnectionMonitor"
    private var monitorJob: Job? = null
    private var _monitoring = false

    companion object {
        private const val CHECK_INTERVAL        = 30_000L   // 30 ثانية
        private const val FORCE_RECONNECT_INTERVAL = 300_000L  // 5 دقائق
    }

    fun startMonitoring() {
        if (_monitoring) return
        _monitoring = true
        Log.d(TAG, "🔍 بدء مراقبة الاتصال…")

        monitorJob = CoroutineScope(Dispatchers.IO).launch {
            var lastForce = 0L
            while (_monitoring) {
                try {
                    val now = System.currentTimeMillis()
                    if (!relayClient.isConnected() && relayClient.isStarted()) {
                        Log.w(TAG, "⚠️ انقطع الاتصال، محاولة الاسترداد…")
                        if (now - lastForce > FORCE_RECONNECT_INTERVAL) {
                            Log.i(TAG, "🔄 إعادة اتصال قسرية…")
                            relayClient.disconnect()
                            delay(2_000)
                            relayClient.connect()
                            lastForce = now
                        }
                    }
                    if (!relayClient.isStarted()) {
                        Log.w(TAG, "⚠️ RelayClient متوقف، إعادة تشغيل…")
                        relayClient.start()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "خطأ في مراقبة الاتصال: ${e.message}")
                }
                delay(CHECK_INTERVAL)
            }
        }
    }

    fun stopMonitoring() {
        _monitoring = false
        monitorJob?.cancel()
        monitorJob = null
        Log.d(TAG, "🛑 توقفت مراقبة الاتصال")
    }

    fun forceReconnect() {
        CoroutineScope(Dispatchers.IO).launch {
            relayClient.disconnect()
            delay(1_000)
            relayClient.connect()
        }
    }

    fun isMonitoring(): Boolean = _monitoring
}
