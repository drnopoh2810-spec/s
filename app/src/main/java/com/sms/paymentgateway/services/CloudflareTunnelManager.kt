package com.sms.paymentgateway.services

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدير Cloudflare Tunnel - يوفر رابط عام ثابت يعمل من أي شبكة
 * 
 * المميزات:
 * - يعمل من أي شبكة (WiFi، 4G، 5G)
 * - لا يحتاج Port Forwarding
 * - HTTPS مجاني
 * - رابط ثابت
 * - مجاني 100%
 */
@Singleton
class CloudflareTunnelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CloudflareTunnel"
    }

    data class TunnelState(
        val isRunning: Boolean = false,
        val publicUrl: String? = null,
        val error: String? = null,
        val method: TunnelMethod = TunnelMethod.NONE
    )

    enum class TunnelMethod {
        NONE,
        SERVEO,      // SSH Tunnel عبر serveo.net
        LOCALTUNNEL, // LocalTunnel
        CLOUDFLARED  // Cloudflare Tunnel الرسمي
    }

    private val _state = MutableStateFlow(TunnelState())
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    private var tunnelJob: Job? = null
    private var process: Process? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * يبدأ Tunnel باستخدام Serveo (الأسهل والأسرع)
     */
    fun startServeoTunnel(localPort: Int = 8080, subdomain: String = "mysmspay") {
        if (_state.value.isRunning) {
            Log.w(TAG, "Tunnel already running")
            return
        }

        tunnelJob = scope.launch {
            try {
                _state.value = TunnelState(
                    isRunning = true,
                    method = TunnelMethod.SERVEO
                )

                Log.i(TAG, "🚀 Starting Serveo tunnel...")
                
                // تحقق من وجود SSH
                if (!isSshAvailable()) {
                    _state.value = TunnelState(
                        isRunning = false,
                        error = "SSH غير متوفر. يرجى تثبيت Termux من F-Droid",
                        method = TunnelMethod.SERVEO
                    )
                    return@launch
                }

                // تشغيل SSH Tunnel
                val command = arrayOf(
                    "ssh",
                    "-o", "StrictHostKeyChecking=no",
                    "-o", "ServerAliveInterval=60",
                    "-R", "$subdomain:80:localhost:$localPort",
                    "serveo.net"
                )

                process = Runtime.getRuntime().exec(command)
                
                // قراءة المخرجات
                val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process!!.errorStream))

                // مراقبة المخرجات للحصول على الرابط
                launch {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Log.d(TAG, "Serveo: $line")
                        
                        // استخراج الرابط
                        if (line?.contains("Forwarding HTTP") == true) {
                            val url = extractUrlFromLine(line!!)
                            if (url != null) {
                                _state.value = TunnelState(
                                    isRunning = true,
                                    publicUrl = url,
                                    method = TunnelMethod.SERVEO
                                )
                                Log.i(TAG, "✅ Tunnel active: $url")
                            }
                        }
                    }
                }

                // مراقبة الأخطاء
                launch {
                    var line: String?
                    while (errorReader.readLine().also { line = it } != null) {
                        Log.e(TAG, "Serveo Error: $line")
                    }
                }

                // انتظار انتهاء العملية
                val exitCode = process!!.waitFor()
                Log.w(TAG, "Serveo process exited with code: $exitCode")

                _state.value = TunnelState(
                    isRunning = false,
                    error = "Tunnel stopped (exit code: $exitCode)",
                    method = TunnelMethod.SERVEO
                )

            } catch (e: Exception) {
                Log.e(TAG, "❌ Serveo tunnel failed", e)
                _state.value = TunnelState(
                    isRunning = false,
                    error = e.message ?: "Unknown error",
                    method = TunnelMethod.SERVEO
                )
            }
        }
    }

    /**
     * يبدأ Tunnel باستخدام Cloudflared
     */
    fun startCloudflaredTunnel(localPort: Int = 8080) {
        if (_state.value.isRunning) {
            Log.w(TAG, "Tunnel already running")
            return
        }

        tunnelJob = scope.launch {
            try {
                _state.value = TunnelState(
                    isRunning = true,
                    method = TunnelMethod.CLOUDFLARED
                )

                Log.i(TAG, "🚀 Starting Cloudflared tunnel...")

                // تحقق من وجود cloudflared
                if (!isCloudflaredAvailable()) {
                    _state.value = TunnelState(
                        isRunning = false,
                        error = "cloudflared غير متوفر. يرجى تثبيته من Termux",
                        method = TunnelMethod.CLOUDFLARED
                    )
                    return@launch
                }

                // تشغيل cloudflared
                val command = arrayOf(
                    "cloudflared",
                    "tunnel",
                    "--url", "http://localhost:$localPort"
                )

                process = Runtime.getRuntime().exec(command)

                // قراءة المخرجات
                val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process!!.errorStream))

                // مراقبة المخرجات
                launch {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Log.d(TAG, "Cloudflared: $line")
                        
                        // استخراج الرابط
                        if (line?.contains("trycloudflare.com") == true) {
                            val url = extractUrlFromLine(line!!)
                            if (url != null) {
                                _state.value = TunnelState(
                                    isRunning = true,
                                    publicUrl = url,
                                    method = TunnelMethod.CLOUDFLARED
                                )
                                Log.i(TAG, "✅ Tunnel active: $url")
                            }
                        }
                    }
                }

                // مراقبة الأخطاء
                launch {
                    var line: String?
                    while (errorReader.readLine().also { line = it } != null) {
                        Log.e(TAG, "Cloudflared Error: $line")
                    }
                }

                // انتظار انتهاء العملية
                val exitCode = process!!.waitFor()
                Log.w(TAG, "Cloudflared process exited with code: $exitCode")

                _state.value = TunnelState(
                    isRunning = false,
                    error = "Tunnel stopped (exit code: $exitCode)",
                    method = TunnelMethod.CLOUDFLARED
                )

            } catch (e: Exception) {
                Log.e(TAG, "❌ Cloudflared tunnel failed", e)
                _state.value = TunnelState(
                    isRunning = false,
                    error = e.message ?: "Unknown error",
                    method = TunnelMethod.CLOUDFLARED
                )
            }
        }
    }

    /**
     * يوقف Tunnel
     */
    fun stop() {
        Log.i(TAG, "🛑 Stopping tunnel...")
        
        tunnelJob?.cancel()
        tunnelJob = null

        process?.destroy()
        process = null

        _state.value = TunnelState(
            isRunning = false,
            method = TunnelMethod.NONE
        )
    }

    /**
     * يعيد تشغيل Tunnel
     */
    fun restart(method: TunnelMethod = TunnelMethod.SERVEO, localPort: Int = 8080) {
        stop()
        delay(1000)
        when (method) {
            TunnelMethod.SERVEO -> startServeoTunnel(localPort)
            TunnelMethod.CLOUDFLARED -> startCloudflaredTunnel(localPort)
            else -> Log.w(TAG, "Unknown tunnel method: $method")
        }
    }

    // ─── Helper Functions ────────────────────────────────────────────────────

    private fun isSshAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "ssh"))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun isCloudflaredAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "cloudflared"))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun extractUrlFromLine(line: String): String? {
        // استخراج URL من السطر
        val urlPattern = Regex("https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
        return urlPattern.find(line)?.value
    }

    private suspend fun delay(millis: Long) {
        kotlinx.coroutines.delay(millis)
    }

    fun cleanup() {
        stop()
        scope.cancel()
    }
}
