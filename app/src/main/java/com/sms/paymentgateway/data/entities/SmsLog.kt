package com.sms.paymentgateway.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "sms_logs")
data class SmsLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String,
    val message: String,
    val receivedAt: Date = Date(),
    val walletType: String,
    val transactionId: String?,
    val amount: Double?,
    val phoneNumber: String?,
    val transactionType: String,
    val parsed: Boolean = false,
    val matched: Boolean = false,
    val matchedTransactionId: String? = null,
    val confidence: Double = 0.0
)
