package com.sms.paymentgateway.utils.matcher

import com.sms.paymentgateway.data.entities.PendingTransaction
import com.sms.paymentgateway.domain.models.ParsedSmsData
import kotlin.math.abs

class TransactionMatcher {

    data class MatchResult(
        val matched: Boolean,
        val transaction: PendingTransaction?,
        val confidence: Double
    )

    suspend fun findMatch(
        parsedSms: ParsedSmsData,
        pendingTransactions: List<PendingTransaction>
    ): MatchResult {
        
        if (parsedSms.amount == null) {
            return MatchResult(false, null, 0.0)
        }

        // Try exact transaction ID match first
        parsedSms.transactionId?.let { smsTxId ->
            val exactMatch = pendingTransactions.firstOrNull { 
                it.expectedTxId == smsTxId 
            }
            if (exactMatch != null) {
                return MatchResult(true, exactMatch, 1.0)
            }
        }

        // Try amount + phone + time window match
        val phone = parsedSms.senderPhone ?: parsedSms.receiverPhone
        if (phone != null) {
            val candidates = pendingTransactions.filter { pending ->
                matchesAmount(parsedSms.amount, pending.amount) &&
                matchesPhone(phone, pending.phoneNumber) &&
                matchesTimeWindow(parsedSms.timestamp.time, pending.createdAt.time)
            }

            if (candidates.isNotEmpty()) {
                val bestMatch = candidates.maxByOrNull { 
                    calculateMatchConfidence(parsedSms, it) 
                }
                val confidence = calculateMatchConfidence(parsedSms, bestMatch!!)
                
                if (confidence >= 0.7) {
                    return MatchResult(true, bestMatch, confidence)
                }
            }
        }

        return MatchResult(false, null, 0.0)
    }

    private fun matchesAmount(smsAmount: Double, pendingAmount: Double): Boolean {
        return abs(smsAmount - pendingAmount) < 0.01
    }

    private fun matchesPhone(smsPhone: String, pendingPhone: String): Boolean {
        val cleanSms = smsPhone.replace(Regex("[^0-9]"), "")
        val cleanPending = pendingPhone.replace(Regex("[^0-9]"), "")
        return cleanSms == cleanPending || cleanSms.takeLast(10) == cleanPending.takeLast(10)
    }

    private fun matchesTimeWindow(smsTime: Long, pendingTime: Long, windowMinutes: Long = 5): Boolean {
        val diffMinutes = abs(smsTime - pendingTime) / (1000 * 60)
        return diffMinutes <= windowMinutes
    }

    private fun calculateMatchConfidence(sms: ParsedSmsData, pending: PendingTransaction): Double {
        var confidence = 0.0

        // Amount match (40%)
        if (matchesAmount(sms.amount ?: 0.0, pending.amount)) {
            confidence += 0.4
        }

        // Phone match (30%)
        val phone = sms.senderPhone ?: sms.receiverPhone
        if (phone != null && matchesPhone(phone, pending.phoneNumber)) {
            confidence += 0.3
        }

        // Time window (20%)
        if (matchesTimeWindow(sms.timestamp.time, pending.createdAt.time)) {
            confidence += 0.2
        }

        // SMS parsing confidence (10%)
        confidence += sms.confidence * 0.1

        return confidence
    }
}
