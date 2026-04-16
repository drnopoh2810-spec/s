package com.sms.paymentgateway.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sms.paymentgateway.data.dao.PendingTransactionDao
import com.sms.paymentgateway.data.dao.SmsLogDao
import com.sms.paymentgateway.data.entities.PendingTransaction
import com.sms.paymentgateway.data.entities.SmsLog
import com.sms.paymentgateway.services.RelayClient
import com.sms.paymentgateway.services.ConnectionMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val pendingTransactionDao: PendingTransactionDao,
    private val smsLogDao: SmsLogDao,
    private val relayClient: RelayClient,
    private val connectionMonitor: ConnectionMonitor
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

    // معلومات الاتصال عبر Relay
    private val _connectionInfo = MutableStateFlow(ConnectionInfo())
    val connectionInfo: StateFlow<ConnectionInfo> = _connectionInfo.asStateFlow()

    init {
        // تحديث معلومات الاتصال دورياً
        viewModelScope.launch {
            while (true) {
                updateConnectionInfo()
                delay(5000) // كل 5 ثواني
            }
        }
    }

    private fun updateConnectionInfo() {
        _connectionInfo.value = ConnectionInfo(
            isActive = relayClient.isConnected(),
            connectionUrl = "Huggingface Relay",
            connectedClients = if (relayClient.isConnected()) 1 else 0,
            clientsList = if (relayClient.isConnected()) listOf("Relay Server") else emptyList()
        )
    }

    val stats: StateFlow<DashboardStats> = combine(
        pendingTransactionDao.getPendingTransactions(),
        smsLogDao.getAllLogs(),
        connectionInfo
    ) { transactions, logs, connection ->
        DashboardStats(
            totalPending = transactions.size,
            totalMatched = transactions.count { it.status == com.sms.paymentgateway.data.entities.TransactionStatus.MATCHED },
            totalSmsReceived = logs.size,
            totalSmsParsed = logs.count { it.parsed },
            successRate = if (logs.isNotEmpty()) {
                (logs.count { it.parsed }.toFloat() / logs.size * 100).toInt()
            } else 0,
            connectionActive = connection.isActive,
            connectedClients = connection.connectedClients
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardStats()
    )

    /**
     * إعادة تشغيل الاتصال بـ Relay
     */
    fun restartConnection() {
        viewModelScope.launch {
            relayClient.disconnect()
            delay(1000)
            relayClient.connect()
            updateConnectionInfo()
        }
    }
}

data class DashboardStats(
    val totalPending: Int = 0,
    val totalMatched: Int = 0,
    val totalSmsReceived: Int = 0,
    val totalSmsParsed: Int = 0,
    val successRate: Int = 0,
    val connectionActive: Boolean = false,
    val connectedClients: Int = 0
)

data class ConnectionInfo(
    val isActive: Boolean = false,
    val connectionUrl: String = "غير متاح",
    val connectedClients: Int = 0,
    val clientsList: List<String> = emptyList()
)
