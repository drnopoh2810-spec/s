package com.sms.paymentgateway.utils.parser

import com.sms.paymentgateway.domain.models.ParsedSmsData
import com.sms.paymentgateway.domain.models.TransactionType
import com.sms.paymentgateway.domain.models.WalletType
import java.util.Date

class SmsParser {

    fun parse(sender: String, message: String): ParsedSmsData? {
        val walletType = WalletType.fromSender(sender)
        
        return when (walletType) {
            WalletType.VODAFONE_CASH -> parseVodafoneCash(message, walletType)
            WalletType.ORANGE_MONEY -> parseOrangeMoney(message, walletType)
            WalletType.ETISALAT_CASH -> parseEtisalatCash(message, walletType)
            WalletType.FAWRY -> parseFawry(message, walletType)
            WalletType.INSTAPAY -> parseInstaPay(message, walletType)
            else -> null
        }
    }

    private fun parseVodafoneCash(message: String, walletType: WalletType): ParsedSmsData? {
        // Patterns for Vodafone Cash
        val amountPattern = """(?:مبلغ|amount|قيمة)\s*:?\s*(\d+(?:\.\d{1,2})?)\s*(?:جنيه|EGP|LE)?""".toRegex(RegexOption.IGNORE_CASE)
        val txIdPattern = """(?:رقم العملية|transaction|ref|reference)\s*:?\s*([A-Z0-9]+)""".toRegex(RegexOption.IGNORE_CASE)
        val phonePattern = """(?:من|to|from)\s*:?\s*(01\d{9})""".toRegex(RegexOption.IGNORE_CASE)
        
        val amount = amountPattern.find(message)?.groupValues?.get(1)?.toDoubleOrNull()
        val txId = txIdPattern.find(message)?.groupValues?.get(1)
        val phone = phonePattern.find(message)?.groupValues?.get(1)
        
        val transactionType = when {
            message.contains("استلمت", ignoreCase = true) || message.contains("received", ignoreCase = true) -> TransactionType.RECEIVED
            message.contains("أرسلت", ignoreCase = true) || message.contains("sent", ignoreCase = true) -> TransactionType.SENT
            message.contains("فشل", ignoreCase = true) || message.contains("failed", ignoreCase = true) -> TransactionType.FAILED
            else -> TransactionType.UNKNOWN
        }
        
        val confidence = calculateConfidence(amount != null, txId != null, phone != null)
        
        return ParsedSmsData(
            walletType = walletType,
            transactionId = txId,
            amount = amount,
            senderPhone = if (transactionType == TransactionType.RECEIVED) phone else null,
            receiverPhone = if (transactionType == TransactionType.SENT) phone else null,
            transactionType = transactionType,
            timestamp = Date(),
            rawMessage = message,
            confidence = confidence
        )
    }

    private fun parseOrangeMoney(message: String, walletType: WalletType): ParsedSmsData? {
        val amountPattern = """(\d+(?:\.\d{1,2})?)\s*(?:جنيه|EGP|LE)""".toRegex(RegexOption.IGNORE_CASE)
        val txIdPattern = """(?:رقم|ref|ID)\s*:?\s*([A-Z0-9]+)""".toRegex(RegexOption.IGNORE_CASE)
        val phonePattern = """(01\d{9})""".toRegex()
        
        val amount = amountPattern.find(message)?.groupValues?.get(1)?.toDoubleOrNull()
        val txId = txIdPattern.find(message)?.groupValues?.get(1)
        val phone = phonePattern.find(message)?.groupValues?.get(1)
        
        val transactionType = when {
            message.contains("استلام", ignoreCase = true) -> TransactionType.RECEIVED
            message.contains("تحويل", ignoreCase = true) -> TransactionType.SENT
            else -> TransactionType.UNKNOWN
        }
        
        return ParsedSmsData(
            walletType = walletType,
            transactionId = txId,
            amount = amount,
            senderPhone = phone,
            receiverPhone = null,
            transactionType = transactionType,
            timestamp = Date(),
            rawMessage = message,
            confidence = calculateConfidence(amount != null, txId != null, phone != null)
        )
    }

    private fun parseEtisalatCash(message: String, walletType: WalletType): ParsedSmsData? {
        val amountPattern = """(\d+(?:\.\d{1,2})?)\s*(?:جنيه|EGP)""".toRegex(RegexOption.IGNORE_CASE)
        val txIdPattern = """([A-Z0-9]{8,})""".toRegex()
        val phonePattern = """(01\d{9})""".toRegex()
        
        return ParsedSmsData(
            walletType = walletType,
            transactionId = txIdPattern.find(message)?.value,
            amount = amountPattern.find(message)?.groupValues?.get(1)?.toDoubleOrNull(),
            senderPhone = phonePattern.find(message)?.value,
            receiverPhone = null,
            transactionType = TransactionType.RECEIVED,
            timestamp = Date(),
            rawMessage = message,
            confidence = 0.8
        )
    }

    private fun parseFawry(message: String, walletType: WalletType): ParsedSmsData? {
        val amountPattern = """(\d+(?:\.\d{1,2})?)\s*(?:جنيه|EGP)""".toRegex(RegexOption.IGNORE_CASE)
        val txIdPattern = """([0-9]{10,})""".toRegex()
        
        return ParsedSmsData(
            walletType = walletType,
            transactionId = txIdPattern.find(message)?.value,
            amount = amountPattern.find(message)?.groupValues?.get(1)?.toDoubleOrNull(),
            senderPhone = null,
            receiverPhone = null,
            transactionType = TransactionType.PAYMENT,
            timestamp = Date(),
            rawMessage = message,
            confidence = 0.75
        )
    }

    private fun parseInstaPay(message: String, walletType: WalletType): ParsedSmsData? {
        val amountPattern = """(?:مبلغ|amount)\s*:?\s*(\d+(?:\.\d{1,2})?)\s*(?:جنيه|EGP)?""".toRegex(RegexOption.IGNORE_CASE)
        val txIdPattern = """(?:رقم|ref|ID)\s*:?\s*([A-Z0-9]+)""".toRegex(RegexOption.IGNORE_CASE)
        val phonePattern = """(01\d{9})""".toRegex()
        
        val amount = amountPattern.find(message)?.groupValues?.get(1)?.toDoubleOrNull()
        val txId = txIdPattern.find(message)?.groupValues?.get(1)
        val phone = phonePattern.find(message)?.value
        
        val transactionType = when {
            message.contains("استلمت", ignoreCase = true) -> TransactionType.RECEIVED
            message.contains("حولت", ignoreCase = true) -> TransactionType.SENT
            else -> TransactionType.UNKNOWN
        }
        
        return ParsedSmsData(
            walletType = walletType,
            transactionId = txId,
            amount = amount,
            senderPhone = if (transactionType == TransactionType.RECEIVED) phone else null,
            receiverPhone = if (transactionType == TransactionType.SENT) phone else null,
            transactionType = transactionType,
            timestamp = Date(),
            rawMessage = message,
            confidence = calculateConfidence(amount != null, txId != null, phone != null)
        )
    }

    private fun calculateConfidence(hasAmount: Boolean, hasTxId: Boolean, hasPhone: Boolean): Double {
        var confidence = 0.0
        if (hasAmount) confidence += 0.4
        if (hasTxId) confidence += 0.4
        if (hasPhone) confidence += 0.2
        return confidence
    }
}
