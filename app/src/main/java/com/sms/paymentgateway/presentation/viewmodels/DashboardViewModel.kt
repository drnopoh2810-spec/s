package com.sms.paymentgateway.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sms.paymentgateway.data.dao.PendingTransactionDao
import com.sms.paymentgateway.data.dao.SmsLogDao
import com.sms.paymentgateway.data.entities.PendingTransaction
import com.sms.paymentgateway.data.entities.SmsLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val pendingTransactionDao: PendingTransactionDao,
    private val smsLogDao: SmsLogDao
) : ViewModel() {

    val pendingTransactions: StateFlow<List<PendingTransaction>> = 
        pendingTransactionDao.getPendingTransactions()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val recentSmsLogs: StateFlow<List<SmsLog>> = 
        smsLogDao.getAllLogs()
            .map { it.take(10) } // Last 10 SMS
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val stats: StateFlow<DashboardStats> = combine(
        pendingTransactionDao.getPendingTransactions(),
        smsLogDao.getAllLogs()
    ) { transactions, logs ->
        DashboardStats(
            totalPending = transactions.size,
            totalMatched = transactions.count { it.status == com.sms.paymentgateway.data.entities.TransactionStatus.MATCHED },
            totalSmsReceived = logs.size,
            totalSmsParsed = logs.count { it.parsed },
            successRate = if (logs.isNotEmpty()) {
                (logs.count { it.parsed }.toFloat() / logs.size * 100).toInt()
            } else 0
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardStats()
    )
}

data class DashboardStats(
    val totalPending: Int = 0,
    val totalMatched: Int = 0,
    val totalSmsReceived: Int = 0,
    val totalSmsParsed: Int = 0,
    val successRate: Int = 0
)
