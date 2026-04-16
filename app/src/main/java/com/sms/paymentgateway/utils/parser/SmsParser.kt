package com.sms.paymentgateway.utils.parser

import com.sms.paymentgateway.domain.models.ParsedSmsData
import com.sms.paymentgateway.domain.models.TransactionType
import com.sms.paymentgateway.domain.models.WalletType
import java.util.Date

class SmsParser {

    fun parse(sender: String, message: String): ParsedSmsData? {
        val walletType = WalletType.fromSender(sender)

        return when (walletType) {
            WalletType.VODAFONE_CASH  -> parseVodafoneCash(message, walletType)
            WalletType.ORANGE_MONEY   -> parseOrangeMoney(message, walletType)
            WalletType.ETISALAT_CASH  -> parseEtisalatCash(message, walletType)
            WalletType.WE_PAY         -> parseWePay(message, walletType)
            WalletType.FAWRY          -> parseFawry(message, walletType)
            WalletType.INSTAPAY       -> parseInstaPay(message, walletType)
            WalletType.BANK_MISR      -> parseBankMisr(message, walletType)
            WalletType.CIB            -> parseCib(message, walletType)
            else                      -> null
        }
    }

    // ─── Vodafone Cash ────────────────────────────────────────────────────────
    private fun parseVodafoneCash(message: String, walletType: WalletType): ParsedSmsData {
        // مبلغ: يدعم كتابة المبلغ قبل الكلمات الدالة أو بعدها
        val amountPattern = listOf(
            """(?:تم استلام|استلمت|received|You received)\s*(?:مبلغ)?\s*(\d[\d,]*(?:\.\d{1,2})?)\s*(?:جنيه|EGP|LE|ج\.م)?""",
            """(?:تم إرسال|أرسلت|تم تحويل|sent|You sent|transfer(?:red)?)\s*(?:مبلغ)?\s*(\d[\d,]*(?:\.\d{1,2})?)\s*(?:جنيه|EGP|LE|ج\.م)?""",
            """(?:مبلغ|amount|قيمة|value|بقيمة)\s*[:\s]\s*(\d[\d,]*(?:\.\d{1,2})?)\s*(?:جنيه|EGP|LE|ج\.م)?""",
            """(\d[\d,]*(?:\.\d{1,2})?)\s*(?:جنيه|EGP|LE|ج\.م)"""
        ).firstNotNullOfOrNull { pat ->
            pat.toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)).find(message)?.groupValues?.get(1)
        }

        val txIdPattern = """(?:رقم العملية|رقم المرجع|رقم الحوالة|Transaction\s*ID|Ref(?:erence)?|مرجع)\s*[:\s]\s*(VC[\w\-]{5,20}|[A-Z]{2,4}\d{6,20})""".toRegex(RegexOption.IGNORE_CASE)
        val txIdAltPattern = """VC[\d]{7,15}""".toRegex(RegexOption.IGNORE_CASE)

        val txId = txIdPattern.find(message)?.groupValues?.get(1)
            ?: txIdAltPattern.find(message)?.value

        val phonePattern = """(?:من|to|from|إلى|محفظة|رقم)\s*[:\s]?\s*(01[0-9]{9})""".toRegex(RegexOption.IGNORE_CASE)
        val phoneFallback = """(01[0-9]{9})""".toRegex()
        val phone = phonePattern.find(message)?.groupValues?.get(1)
            ?: phoneFallback.find(message)?.value

        val transactionType = detectTransactionType(message)
        val amount = cleanAmount(amountPattern)
        val confidence = calculateConfidence(amount != null, txId != null, phone != null)

        return ParsedSmsData(
            walletType = walletType,
            transactionId = txId,
            amount = amount,
            senderPhone   = if (transactionType == TransactionType.RECEIVED) phone else null,
            receiverPhone = if (transactionType == TransactionType.SENT) phone else null,
            transactionType = transactionType,
            timestamp = Date(),
            rawMessage = message,
            confidence = confidence
        )
    }

    // ─── Orange Money ─────────────────────────────────────────────────────────
    private fun parseOrangeMoney(message: String, walletType: WalletType): ParsedSmsData {
        val amountPatterns = listOf(
            """(?:استلمت|تم استلام|received|Received)\s*(?:تحويل\s*)?(?:بقيمة)?\s*(\d[\d,]*(?:\.\d{1,2})?)\s*(?:جنيه|EGP|LE)?""",
            """(?:تم تحويل|تحويل|transferred?)\s*(\d[\d,]*(?:\.\d{1,2})?)\s*(?:جنيه|EGP|LE)?""",
            """(?:بقيمة|المبلغ|amount)\s*[:\s]\s*(\d[\d,]*(?:\.\d{1,2})?)\s*(?:جنيه|EGP|LE)?""",
            """(\d[\d,]*(?:\.\d{1,2})?)\s*(?:جنيه|EGP|LE)"""
        )
        val amount = amountPatterns.firstNotNullOfOrNull { pat ->
            pat.toRegex(setOf(RegexOption.IGNORE_CASE)).find(message)?.groupValues?.get(1)
        }

        val txIdPattern = """(?:رقم|ID|Ref|رقم العملية|Reference)\s*[:\s]?\s*(OM[\w]{4,15}|[A-Z]{2}\d{6,15})""".toRegex(RegexOption.IGNORE_CASE)
        val txIdAlt     = """OM\d{5,15}""".toRegex(RegexOption.IGNORE_CASE)
        val txId = txIdPattern.find(message)?.groupValues?.get(1) ?: txIdAlt.find(message)?.value

        val phone = """(01[0-9]{9})""".toRegex().find(message)?.value
        val transactionType = detectTransactionType(message)

        return ParsedSmsData(
            walletType = walletType,
            transactionId = txId,
            amount = cleanAmount(amount),
            senderPhone   = if (transactionType == TransactionType.RECEIVED) phone else null,
            receiverPhone = if (transactionType == TransactionType.SENT) phone else null,
            transactionType = transactionType,
            timestamp = Date(),
            rawMessage = message,
            confidence = calculateConfidence(amount != null, txId != null, phone != null)
        )
    }

    // ─── Etisalat / e& Cash ──────────────────────────────────────────────────
    private fun parseEtisalatCash(message: String, walletType: WalletType): ParsedSmsData {
        val amount = listOf(
            """(?:استلمت|تم استلام|received|Received)\s*(\d[\d,]*(?:\.\d{1,2})?)\s*(?:جنيه|EGP)?""",
            """(\d[\d,]*(?:\.\d{1,2})?)\s*(?:جنيه|EGP)"""
        ).firstNotNullOfOrNull { pat ->
            pat.toRegex(RegexOption.IGNORE_CASE).find(message)?.groupValues?.get(1)
        }

        val txId = listOf(
            """(?:Ref|رقم)[:\s]?\s*(EC[\w]{6,15}|[A-Z]{2,4}\d{6,15})""",
            """(EC\d{6,15})"""
        ).firstNotNullOfOrNull { pat ->
            pat.toRegex(RegexOption.IGNORE_CASE).find(message)?.groupValues?.get(1)
        }

        val phone = """(01[0-9]{9})""".toRegex().find(message)?.value
        val transactionType = detectTransactionType(message)

        return ParsedSmsData(
            walletType = walletType,
            transactionId = txId,
            amount = cleanAmount(amount),
            senderPhone   = phone,
            receiverPhone = null,
            transactionType = transactionType,
            timestamp = Date(),
            rawMessage = message,
            confidence = calculateConfidence(amount != null, txId != null, phone != null)
        )
    }

    // ─── WE Pay ───────────────────────────────────────────────────────────────
    private fun parseWePay(message: String, walletType: WalletType): ParsedSmsData {
        val amount = listOf(
            """(?:استلمت|تم استلام|received)\s*(\d[\d,]*(?:\.\d{1,2})?)\s*(?:جنيه|EGP)?""",
            """(\d[\d,]*(?:\.\d{1,2})?)\s*(?:جنيه|EGP)"""
        ).firstNotNullOfOrNull { pat ->
            pat.toRegex(RegexOption.IGNORE_CASE).find(message)?.groupValues?.get(1)
        }

        val txId = """(?:Ref|رقم)[:\s]?\s*(WE[\w]{5,15}|[A-Z0-9]{8,20})""".toRegex(RegexOption.IGNORE_CASE)
            .find(message)?.groupValues?.get(1)

        val phone = """(01[0-9]{9})""".toRegex().find(message)?.value
        val transactionType = detectTransactionType(message)

        return ParsedSmsData(
            walletType = walletType,
            transactionId = txId,
            amount = cleanAmount(amount),
            senderPhone   = phone,
            receiverPhone = null,
            transactionType = transactionType,
            timestamp = Date(),
            rawMessage = message,
            confidence = calculateConfidence(amount != null, txId != null, phone != null)
        )
    }

    // ─── Fawry ────────────────────────────────────────────────────────────────
    private fun parseFawry(message: String, walletType: WalletType): ParsedSmsData {
        val amount = listOf(
            """(?:بقيمة|المبلغ|amount|قيمة الفاتورة|Payment)\s*[:\s]?\s*(\d[\d,]*(?:\.\d{1,2})?)\s*(?:جنيه|EGP|LE)?""",
            """(\d[\d,]*(?:\.\d{1,2})?)\s*(?:جنيه|EGP)"""
        ).firstNotNullOfOrNull { pat ->
            pat.toRegex(RegexOption.IGNORE_CASE).find(message)?.groupValues?.get(1)
        }

        // Fawry transaction IDs are numeric
        val txId = listOf(
            """(?:رقم العملية|رقم المرجع|Ref(?:erence)?|Transaction)\s*[:\s]?\s*(\d{8,20})""",
            """(\d{10,20})"""
        ).firstNotNullOfOrNull { pat ->
            pat.toRegex(RegexOption.IGNORE_CASE).find(message)?.groupValues?.get(1)
        }

        val phone = """(01[0-9]{9})""".toRegex().find(message)?.value

        return ParsedSmsData(
            walletType = walletType,
            transactionId = txId,
            amount = cleanAmount(amount),
            senderPhone = phone,
            receiverPhone = null,
            transactionType = TransactionType.PAYMENT,
            timestamp = Date(),
            rawMessage = message,
            confidence = calculateConfidence(amount != null, txId != null, phone != null)
        )
    }

    // ─── InstaPay ────────────────────────────────────────────────────────────
    private fun parseInstaPay(message: String, walletType: WalletType): ParsedSmsData {
        val amount = listOf(
            """(?:مبلغ|amount|بقيمة|استلمت|received)\s*[:\s]?\s*(\d[\d,]*(?:\.\d{1,2})?)\s*(?:جنيه|EGP|LE)?""",
            """(\d[\d,]*(?:\.\d{1,2})?)\s*(?:جنيه|EGP|LE)"""
        ).firstNotNullOfOrNull { pat ->
            pat.toRegex(RegexOption.IGNORE_CASE).find(message)?.groupValues?.get(1)
        }

        val txId = listOf(
            """(?:رقم|Ref|ID)[:\s]?\s*(IP[\w]{5,15}|[A-Z]{2}\d{6,15})""",
            """IP\d{5,15}"""
        ).firstNotNullOfOrNull { pat ->
            pat.toRegex(RegexOption.IGNORE_CASE).find(message)?.groupValues?.get(1)
        }

        val phonePattern = """(?:من|to|from|إلى)\s*[:\s]?\s*(01[0-9]{9})""".toRegex(RegexOption.IGNORE_CASE)
        val phone = phonePattern.find(message)?.groupValues?.get(1)
            ?: """(01[0-9]{9})""".toRegex().find(message)?.value

        val transactionType = detectTransactionType(message)

        return ParsedSmsData(
            walletType = walletType,
            transactionId = txId,
            amount = cleanAmount(amount),
            senderPhone   = if (transactionType == TransactionType.RECEIVED) phone else null,
            receiverPhone = if (transactionType == TransactionType.SENT) phone else null,
            transactionType = transactionType,
            timestamp = Date(),
            rawMessage = message,
            confidence = calculateConfidence(amount != null, txId != null, phone != null)
        )
    }

    // ─── Bank Misr (Meeza) ────────────────────────────────────────────────────
    private fun parseBankMisr(message: String, walletType: WalletType): ParsedSmsData {
        val amount = listOf(
            """(?:مبلغ|Amount|قيمة)\s*[:\s]?\s*(\d[\d,]*(?:\.\d{1,2})?)\s*(?:جنيه|EGP)?""",
            """(\d[\d,]*(?:\.\d{1,2})?)\s*(?:جنيه|EGP)"""
        ).firstNotNullOfOrNull { pat ->
            pat.toRegex(RegexOption.IGNORE_CASE).find(message)?.groupValues?.get(1)
        }

        val txId = """(?:Ref|رقم|Transaction)[:\s]?\s*([A-Z0-9]{8,20})""".toRegex(RegexOption.IGNORE_CASE)
            .find(message)?.groupValues?.get(1)

        val phone = """(01[0-9]{9})""".toRegex().find(message)?.value
        val transactionType = detectTransactionType(message)

        return ParsedSmsData(
            walletType = walletType,
            transactionId = txId,
            amount = cleanAmount(amount),
            senderPhone = phone,
            receiverPhone = null,
            transactionType = transactionType,
            timestamp = Date(),
            rawMessage = message,
            confidence = calculateConfidence(amount != null, txId != null, phone != null)
        )
    }

    // ─── CIB ─────────────────────────────────────────────────────────────────
    private fun parseCib(message: String, walletType: WalletType): ParsedSmsData {
        val amount = listOf(
            """(?:Amount|مبلغ)\s*[:\s]?\s*(\d[\d,]*(?:\.\d{1,2})?)\s*(?:EGP|جنيه)?""",
            """(\d[\d,]*(?:\.\d{1,2})?)\s*EGP"""
        ).firstNotNullOfOrNull { pat ->
            pat.toRegex(RegexOption.IGNORE_CASE).find(message)?.groupValues?.get(1)
        }

        val txId = """(?:Ref|Transaction\s*No\.?|Auth\.?\s*Code|رقم)[:\s]?\s*([A-Z0-9]{6,20})""".toRegex(RegexOption.IGNORE_CASE)
            .find(message)?.groupValues?.get(1)

        val phone = """(01[0-9]{9})""".toRegex().find(message)?.value
        val transactionType = detectTransactionType(message)

        return ParsedSmsData(
            walletType = walletType,
            transactionId = txId,
            amount = cleanAmount(amount),
            senderPhone = phone,
            receiverPhone = null,
            transactionType = transactionType,
            timestamp = Date(),
            rawMessage = message,
            confidence = calculateConfidence(amount != null, txId != null, phone != null)
        )
    }

    // ─── Shared Helpers ───────────────────────────────────────────────────────

    private fun detectTransactionType(message: String): TransactionType {
        val lower = message.lowercase()
        return when {
            lower.containsAny("استلمت", "تم استلام", "received", "استقبال", "وردك", "وصلك") -> TransactionType.RECEIVED
            lower.containsAny("أرسلت", "تم إرسال", "تم تحويل", "حولت", "sent", "transfer") -> TransactionType.SENT
            lower.containsAny("فشل", "failed", "غير مكتمل", "خطأ", "error", "unsuccessful") -> TransactionType.FAILED
            lower.containsAny("سداد", "دفع", "payment", "paid", "فاتورة") -> TransactionType.PAYMENT
            else -> TransactionType.UNKNOWN
        }
    }

    private fun String.containsAny(vararg terms: String): Boolean = terms.any { this.contains(it) }

    private fun cleanAmount(raw: String?): Double? {
        return raw?.replace(",", "")?.toDoubleOrNull()
    }

    private fun calculateConfidence(hasAmount: Boolean, hasTxId: Boolean, hasPhone: Boolean): Double {
        var c = 0.0
        if (hasAmount) c += 0.45
        if (hasTxId)   c += 0.35
        if (hasPhone)  c += 0.20
        return c
    }
}
