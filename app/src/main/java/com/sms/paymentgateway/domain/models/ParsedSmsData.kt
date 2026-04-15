package com.sms.paymentgateway.domain.models

import java.util.Date

data class ParsedSmsData(
    val walletType: WalletType,
    val transactionId: String?,
    val amount: Double?,
    val currency: String = "EGP",
    val senderPhone: String?,
    val receiverPhone: String?,
    val transactionType: TransactionType,
    val timestamp: Date,
    val rawMessage: String,
    val confidence: Double = 0.0
)

enum class TransactionType {
    RECEIVED,
    SENT,
    PAYMENT,
    FAILED,
    UNKNOWN
}
