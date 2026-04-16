package com.sms.paymentgateway.services

import com.google.gson.Gson
import fi.iki.elonen.NanoWSD
import okhttp3.*
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketHandler @Inject constructor(
    private val gson: Gson
) {
    companion object {
        private const val TAG = "WebSocketHandler"
    }

    // قائمة العملاء المتصلين (دعم كلاً من OkHttp WebSocket و NanoWSD WebSocket)
    private val okHttpClients = CopyOnWriteArrayList<WebSocket>()
    private val nanoWsdClients = CopyOnWriteArrayList<NanoWSD.WebSocket>()

    /**
     * إضافة عميل OkHttp WebSocket
     */
    fun addClient(webSocket: WebSocket) {
        okHttpClients.add(webSocket)
        Timber.i("$TAG: OkHttp WebSocket client connected. Total: ${getTotalClients()}")
    }

    /**
     * إضافة عميل NanoWSD WebSocket
     */
    fun addClient(webSocket: NanoWSD.WebSocket) {
        nanoWsdClients.add(webSocket)
        Timber.i("$TAG: NanoWSD WebSocket client connected. Total: ${getTotalClients()}")
    }

    /**
     * إزالة عميل OkHttp WebSocket
     */
    fun removeClient(webSocket: WebSocket) {
        okHttpClients.remove(webSocket)
        Timber.i("$TAG: OkHttp WebSocket client disconnected. Total: ${getTotalClients()}")
    }

    /**
     * إزالة عميل NanoWSD WebSocket
     */
    fun removeClient(webSocket: NanoWSD.WebSocket) {
        nanoWsdClients.remove(webSocket)
        Timber.i("$TAG: NanoWSD WebSocket client disconnected. Total: ${getTotalClients()}")
    }

    /**
     * الحصول على إجمالي عدد العملاء المتصلين
     */
    fun getTotalClients(): Int {
        return okHttpClients.size + nanoWsdClients.size
    }

    /**
     * بث رسالة لجميع العملاء المتصلين
     */
    fun broadcast(event: String, data: Any) {
        val message = gson.toJson(mapOf(
            "event" to event,
            "data" to data,
            "timestamp" to System.currentTimeMillis()
        ))

        Timber.d("$TAG: Broadcasting message to ${getTotalClients()} clients: $event")

        // إرسال لعملاء OkHttp
        okHttpClients.forEach { client ->
            try {
                client.send(message)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to send message to OkHttp WebSocket client")
                okHttpClients.remove(client)
            }
        }

        // إرسال لعملاء NanoWSD
        nanoWsdClients.forEach { client ->
            try {
                client.send(message)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to send message to NanoWSD WebSocket client")
                nanoWsdClients.remove(client)
            }
        }
    }

    /**
     * إرسال رسالة لعميل محدد
     */
    fun sendToClient(webSocket: NanoWSD.WebSocket, event: String, data: Any) {
        val message = gson.toJson(mapOf(
            "event" to event,
            "data" to data,
            "timestamp" to System.currentTimeMillis()
        ))

        try {
            webSocket.send(message)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to send message to specific client")
            nanoWsdClients.remove(webSocket)
        }
    }

    /**
     * إشعار تأكيد الدفعة
     */
    fun onPaymentConfirmed(transactionId: String, amount: Double, confidence: Double) {
        broadcast("PAYMENT_CONFIRMED", mapOf(
            "transactionId" to transactionId,
            "amount" to amount,
            "confidence" to confidence,
            "status" to "confirmed"
        ))
    }

    /**
     * إشعار تأكيد الدفعة مع بيانات SMS كاملة
     */
    fun broadcastPaymentConfirmation(transactionId: String, smsData: Map<String, Any>) {
        broadcast("PAYMENT_CONFIRMED", mapOf(
            "transactionId" to transactionId,
            "smsData" to smsData,
            "status" to "confirmed"
        ))
    }

    /**
     * إشعار استقبال SMS
     */
    fun onSmsReceived(sender: String, parsed: Boolean, walletType: String? = null) {
        broadcast("SMS_RECEIVED", mapOf(
            "sender" to sender,
            "parsed" to parsed,
            "walletType" to walletType,
            "status" to "received"
        ))
    }

    /**
     * إشعار إنشاء معاملة جديدة
     */
    fun onTransactionCreated(transactionId: String, amount: Double? = null, phoneNumber: String? = null) {
        broadcast("TRANSACTION_CREATED", mapOf(
            "transactionId" to transactionId,
            "amount" to amount,
            "phoneNumber" to phoneNumber,
            "status" to "created"
        ))
    }

    /**
     * إشعار تحديث حالة المعاملة
     */
    fun onTransactionStatusUpdated(transactionId: String, status: String, confidence: Double? = null) {
        broadcast("TRANSACTION_STATUS_UPDATED", mapOf(
            "transactionId" to transactionId,
            "status" to status,
            "confidence" to confidence
        ))
    }

    /**
     * إرسال heartbeat لجميع العملاء
     */
    fun sendHeartbeat() {
        broadcast("HEARTBEAT", mapOf(
            "status" to "alive",
            "connectedClients" to getTotalClients()
        ))
    }

    /**
     * إرسال معلومات الاتصال
     */
    fun sendConnectionInfo(localIp: String, port: Int) {
        broadcast("CONNECTION_INFO", mapOf(
            "localIp" to localIp,
            "port" to port,
            "connectionType" to "direct",
            "version" to "2.0.0"
        ))
    }

    /**
     * قطع جميع الاتصالات
     */
    fun disconnectAll() {
        Timber.i("$TAG: Disconnecting all clients...")

        okHttpClients.forEach { client ->
            try {
                client.close(1000, "Server shutting down")
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error closing OkHttp WebSocket")
            }
        }
        okHttpClients.clear()

        nanoWsdClients.forEach { client ->
            try {
                client.close(NanoWSD.WebSocketFrame.CloseCode.NormalClosure, "Server shutting down", false)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error closing NanoWSD WebSocket")
            }
        }
        nanoWsdClients.clear()

        Timber.i("$TAG: All clients disconnected")
    }

    /**
     * الحصول على إحصائيات الاتصال
     */
    fun getConnectionStats(): Map<String, Any> {
        return mapOf(
            "totalClients" to getTotalClients(),
            "okHttpClients" to okHttpClients.size,
            "nanoWsdClients" to nanoWsdClients.size,
            "timestamp" to System.currentTimeMillis()
        )
    }
}
