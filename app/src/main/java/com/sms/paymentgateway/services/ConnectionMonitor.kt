package com.sms.paymentgateway.services

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val directConnectionManager: DirectConnectionManager
) {
    private val TAG = "ConnectionMonitor"
    private var monitorJob: Job? = null
    private var _monitoring = false

    companion object {
        private const val CHECK_INTERVAL = 30_000L   // 30 ثانية
        private const val RESTART_INTERVAL = 300_000L  // 5 دقائق
    }

    fun startMonitoring() {
        if (_monitoring) return
        _monitoring = true
        Log.d(TAG, "🔍 بدء مراقبة الاتصال المباشر…")

        monitorJob = CoroutineScope(Dispatchers.IO).launch {
            var lastRestart = 0L
            while (_monitoring) {
                try {
                    val now = System.currentTimeMillis()
                    
                    // فحص حالة DirectConnectionManager
                    if (!directConnectionManager.isActive.value) {
                        Log.w(TAG, "⚠️ مدير الاتصال المباشر متوقف، محاولة إعادة التشغيل…")
                        
                        if (now - lastRestart > RESTART_INTERVAL) {
                            Log.i(TAG, "🔄 إعادة تشغيل مدير الاتصال المباشر…")
                            directConnectionManager.stop()
                            delay(2_000)
                            directConnectionManager.start()
                            lastRestart = now
                        }
                    } else {
                        // فحص عدد العملاء المتصلين
                        val clientCount = directConnectionManager.connectedClients.value.size
                        Log.d(TAG, "📊 العملاء المتصلين: $clientCount")
                        
                        // إرسال heartbeat إذا كان هناك عملاء متصلين
                        if (clientCount > 0) {
                            directConnectionManager.broadcastMessage(
                                """{"type":"heartbeat","timestamp":${System.currentTimeMillis()},"status":"alive"}"""
                            )
                        }
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

    fun forceRestart() {
        CoroutineScope(Dispatchers.IO).launch {
            Log.i(TAG, "🔄 إعادة تشغيل قسرية لمدير الاتصال المباشر")
            directConnectionManager.stop()
            delay(1_000)
            directConnectionManager.start()
        }
    }

    fun isMonitoring(): Boolean = _monitoring

    /**
     * الحصول على معلومات الاتصال الحالية
     */
    fun getConnectionStatus(): Map<String, Any> {
        return mapOf(
            "isActive" to directConnectionManager.isActive.value,
            "connectionUrl" to (directConnectionManager.connectionUrl.value ?: "غير متاح"),
            "connectedClients" to directConnectionManager.connectedClients.value.size,
            "isMonitoring" to _monitoring,
            "timestamp" to System.currentTimeMillis()
        )
    }
}
