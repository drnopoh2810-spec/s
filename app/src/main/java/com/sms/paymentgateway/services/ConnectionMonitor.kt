package com.sms.paymentgateway.services

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مراقب حالة الاتصال - يتأكد من أن RelayClient متصل دائماً
 */
@Singleton
class ConnectionMonitor @Inject constructor(
    private val context: Context,
    private val relayClient: RelayClient
) {
    private val TAG = "ConnectionMonitor"
    private var monitoringJob: Job? = null
    private var isMonitoring = false
    private val handler = Handler(Looper.getMainLooper())
    
    companion object {
        private const val CHECK_INTERVAL = 30_000L // 30 ثانية
        private const val FORCE_RECONNECT_INTERVAL = 300_000L // 5 دقائق
    }
    
    /**
     * بدء مراقبة الاتصال
     */
    fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "المراقبة تعمل بالفعل")
            return
        }
        
        isMonitoring = true
        Log.d(TAG, "🔍 بدء مراقبة حالة الاتصال...")
        
        monitoringJob = CoroutineScope(Dispatchers.IO).launch {
            var lastForceReconnect = 0L
            
            while (isMonitoring) {
                try {
                    val currentTime = System.currentTimeMillis()
                    
                    // التحقق من حالة الاتصال
                    if (!relayClient.isConnected() && relayClient.isStarted()) {
                        Log.w(TAG, "⚠️ RelayClient غير متصل، محاولة إعادة الاتصال...")
                        
                        // إعادة اتصال قسرية كل 5 دقائق
                        if (currentTime - lastForceReconnect > FORCE_RECONNECT_INTERVAL) {
                            Log.i(TAG, "🔄 إعادة اتصال قسرية...")
                            relayClient.disconnect()
                            delay(2000) // انتظار قصير
                            relayClient.connect()
                            lastForceReconnect = currentTime
                        }
                    } else if (relayClient.isConnected()) {
                        Log.v(TAG, "✅ الاتصال سليم")
                    }
                    
                    // التحقق من أن الخدمة مبدءة
                    if (!relayClient.isStarted()) {
                        Log.w(TAG, "⚠️ RelayClient غير مبدء، إعادة تشغيل...")
                        relayClient.start()
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ خطأ في مراقبة الاتصال: ${e.message}", e)
                }
                
                delay(CHECK_INTERVAL)
            }
        }
    }
    
    /**
     * إيقاف مراقبة الاتصال
     */
    fun stopMonitoring() {
        isMonitoring = false
        monitoringJob?.cancel()
        monitoringJob = null
        Log.d(TAG, "🛑 تم إيقاف مراقبة الاتصال")
    }
    
    /**
     * فرض إعادة الاتصال
     */
    fun forceReconnect() {
        Log.i(TAG, "🔄 فرض إعادة الاتصال...")
        CoroutineScope(Dispatchers.IO).launch {
            relayClient.disconnect()
            delay(1000)
            relayClient.connect()
        }
    }
    
    /**
     * الحصول على حالة المراقبة
     */
    fun isMonitoring(): Boolean = isMonitoring
}