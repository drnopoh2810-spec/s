package com.sms.paymentgateway.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "pending_transactions")
data class PendingTransaction(
    @PrimaryKey
    val id: String,
    val amount: Double,
    val phoneNumber: String,
    val expectedTxId: String? = null,
    val walletType: String? = null,
    val status: TransactionStatus = TransactionStatus.PENDING,
    val createdAt: Date = Date(),
    val expiresAt: Date,
    val matchedAt: Date? = null,
    val matchedSmsId: Long? = null,
    val confidence: Double? = null
)

enum class TransactionStatus {
    PENDING,
    MATCHED,
    EXPIRED,
    CANCELLED
}
