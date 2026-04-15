package com.sms.paymentgateway.services

import com.sms.paymentgateway.data.dao.PendingTransactionDao
import com.sms.paymentgateway.data.dao.SmsLogDao
import com.sms.paymentgateway.data.entities.SmsLog
import com.sms.paymentgateway.data.entities.TransactionStatus
import com.sms.paymentgateway.domain.models.WalletType
import com.sms.paymentgateway.utils.matcher.TransactionMatcher
import com.sms.paymentgateway.utils.parser.SmsParser
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsProcessor @Inject constructor(
    private val smsParser: SmsParser,
    private val transactionMatcher: TransactionMatcher,
    private val smsLogDao: SmsLogDao,
    private val pendingTransactionDao: PendingTransactionDao,
    private val webhookClient: WebhookClient
) {

    suspend fun processSms(sender: String, message: String) {
        Timber.d("Processing SMS from: $sender")
        
        // Parse SMS
        val parsedData = smsParser.parse(sender, message)
        
        if (parsedData == null) {
            Timber.w("Could not parse SMS from $sender")
            saveSmsLog(sender, message, null, false, false)
            return
        }

        Timber.d("Parsed SMS: $parsedData")
        
        // Save to database
        val smsLogId = saveSmsLog(
            sender = sender,
            message = message,
            parsedData = parsedData,
            parsed = true,
            matched = false
        )

        // Try to match with pending transactions
        val pendingTransactions = pendingTransactionDao.getPendingTransactions().first()
        val matchResult = transactionMatcher.findMatch(parsedData, pendingTransactions)

        if (matchResult.matched && matchResult.transaction != null) {
            Timber.i("Match found! Confidence: ${matchResult.confidence}")
            
            // Update transaction status
            val updatedTransaction = matchResult.transaction.copy(
                status = TransactionStatus.MATCHED,
                matchedAt = Date(),
                matchedSmsId = smsLogId,
                confidence = matchResult.confidence
            )
            pendingTransactionDao.updateTransaction(updatedTransaction)

            // Update SMS log
            val smsLog = smsLogDao.getLogById(smsLogId)
            smsLog?.let {
                smsLogDao.updateLog(it.copy(
                    matched = true,
                    matchedTransactionId = matchResult.transaction.id
                ))
            }

            // Send webhook notification
            webhookClient.sendPaymentConfirmation(
                transactionId = matchResult.transaction.id,
                parsedData = parsedData,
                confidence = matchResult.confidence
            )
        } else {
            Timber.d("No match found for SMS")
        }
    }

    private suspend fun saveSmsLog(
        sender: String,
        message: String,
        parsedData: com.sms.paymentgateway.domain.models.ParsedSmsData?,
        parsed: Boolean,
        matched: Boolean
    ): Long {
        val log = SmsLog(
            sender = sender,
            message = message,
            receivedAt = Date(),
            walletType = parsedData?.walletType?.name ?: WalletType.UNKNOWN.name,
            transactionId = parsedData?.transactionId,
            amount = parsedData?.amount,
            phoneNumber = parsedData?.senderPhone ?: parsedData?.receiverPhone,
            transactionType = parsedData?.transactionType?.name ?: "UNKNOWN",
            parsed = parsed,
            matched = matched,
            confidence = parsedData?.confidence ?: 0.0
        )
        return smsLogDao.insertLog(log)
    }
}
