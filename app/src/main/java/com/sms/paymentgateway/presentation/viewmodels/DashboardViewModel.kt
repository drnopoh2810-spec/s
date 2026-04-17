package com.sms.paymentgateway.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sms.paymentgateway.data.dao.PendingTransactionDao
import com.sms.paymentgateway.data.dao.SmsLogDao
import com.sms.paymentgateway.data.entities.PendingTransaction
import com.sms.paymentgateway.data.entities.SmsLog
import com.sms.paymentgateway.data.repository.AnalyticsRepository
import com.sms.paymentgateway.data.repository.DashboardAnalytics
import com.sms.paymentgateway.services.RelayClient
import com.sms.paymentgateway.services.ConnectionMonitor
import com.sms.paymentgateway.services.SmartTunnelManager
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
    private val connectionMonitor: ConnectionMonitor,
    private val analyticsRepository: AnalyticsRepository,
    private val smartTunnelManager: SmartTunnelManager
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
            .map { it.take(10) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    private val _connectionInfo = MutableStateFlow(ConnectionInfo())
    val connectionInfo: StateFlow<ConnectionInfo> = _connectionInfo.asStateFlow()

    private val _analytics = MutableStateFlow<DashboardAnalytics?>(null)
    val analytics: StateFlow<DashboardAnalytics?> = _analytics.asStateFlow()

    /** حالة SmartTunnel - الرابط العام */
    val tunnelState = smartTunnelManager.state

    init {
        viewModelScope.launch {
            while (true) {
                updateConnectionInfo()
                refreshAnalytics()
                delay(30_000L) // كل 30 ثانية
            }
        }
    }

    fun refreshAnalytics() {
        viewModelScope.launch {
            _analytics.value = analyticsRepository.getDashboardAnalytics()
        }
    }

    private fun updateConnectionInfo() {
        // نستخدم SmartTunnel كمصدر رئيسي + RelayClient كـ fallback
        val tunnelActive = smartTunnelManager.state.value.status == SmartTunnelManager.TunnelStatus.ACTIVE
        val relayActive  = relayClient.isConnected()
        val isActive     = tunnelActive || relayActive

        _connectionInfo.value = ConnectionInfo(
            isActive = isActive,
            connectionUrl = if (tunnelActive)
                smartTunnelManager.state.value.publicUrl ?: "Huggingface Relay"
            else
                "Huggingface Relay",
            connectedClients = if (isActive) 1 else 0,
            clientsList = if (isActive) listOf("Relay Server") else emptyList()
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
