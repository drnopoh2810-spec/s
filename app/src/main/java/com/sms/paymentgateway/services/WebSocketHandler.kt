package com.sms.paymentgateway.services

import com.google.gson.Gson
import okhttp3.*
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketHandler @Inject constructor(
    private val gson: Gson
) {
    private val clients = CopyOnWriteArrayList<WebSocket>()

    fun addClient(webSocket: WebSocket) {
        clients.add(webSocket)
        Timber.i("WebSocket client connected. Total clients: ${clients.size}")
    }

    fun removeClient(webSocket: WebSocket) {
        clients.remove(webSocket)
        Timber.i("WebSocket client disconnected. Total clients: ${clients.size}")
    }

    fun broadcast(event: String, data: Any) {
        val message = gson.toJson(mapOf(
            "event" to event,
            "data" to data,
            "timestamp" to System.currentTimeMillis()
        ))

        clients.forEach { client ->
            try {
                client.send(message)
            } catch (e: Exception) {
                Timber.e(e, "Failed to send message to WebSocket client")
                clients.remove(client)
            }
        }
    }

    fun onPaymentConfirmed(transactionId: String, amount: Double, confidence: Double) {
        broadcast("PAYMENT_CONFIRMED", mapOf(
            "transactionId" to transactionId,
            "amount" to amount,
            "confidence" to confidence
        ))
    }

    fun onSmsReceived(sender: String, parsed: Boolean) {
        broadcast("SMS_RECEIVED", mapOf(
            "sender" to sender,
            "parsed" to parsed
        ))
    }

    fun onTransactionCreated(transactionId: String) {
        broadcast("TRANSACTION_CREATED", mapOf(
            "transactionId" to transactionId
        ))
    }
}
