package com.sms.paymentgateway.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sms.paymentgateway.data.dao.PendingTransactionDao
import com.sms.paymentgateway.data.dao.SmsLogDao
import com.sms.paymentgateway.data.entities.PendingTransaction
import com.sms.paymentgateway.data.entities.SmsLog
import com.sms.paymentgateway.services.DirectConnectionManager
import com.sms.paymentgateway.services.NetworkDetector
import com.sms.paymentgateway.services.ConnectionMonitor
import com.sms.paymentgateway.services.ExternalAccessManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val pendingTransactionDao: PendingTransactionDao,
    private val smsLogDao: SmsLogDao,
    private val directConnectionManager: DirectConnectionManager,
    private val connectionMonitor: ConnectionMonitor,
    private val externalAccessManager: ExternalAccessManager
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

    // معلومات الاتصال المباشر المحدثة
    val connectionInfo: StateFlow<ConnectionInfo> = combine(
        directConnectionManager.isActive,
        directConnectionManager.connectionUrl,
        directConnectionManager.connectedClients,
        directConnectionManager.connectionUrls,
        directConnectionManager.networkInfo
    ) { isActive, url, clients, urls, networkInfo ->
        ConnectionInfo(
            isActive = isActive,
            connectionUrl = url ?: "غير متاح",
            connectedClients = clients.size,
            clientsList = clients.toList(),
            availableUrls = urls,
            networkInfo = networkInfo,
            bestUrl = directConnectionManager.getBestConnectionUrl(),
            isPublicAccessible = networkInfo?.isPublicAccessible ?: false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ConnectionInfo()
    )

    // معلومات الوصول الخارجي
    val externalAccessInfo = externalAccessManager.externalAccessInfo.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

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
     * إعادة تشغيل الاتصال المباشر
     */
    fun restartConnection() {
        viewModelScope.launch {
            connectionMonitor.forceRestart()
        }
    }

    /**
     * تحليل إمكانيات الوصول الخارجي
     */
    fun analyzeExternalAccess() {
        viewModelScope.launch {
            externalAccessManager.analyzeExternalAccess()
        }
    }

    /**
     * الحصول على دليل الإعداد
     */
    fun getSetupGuide(method: ExternalAccessManager.AccessMethod): String? {
        val networkInfo = directConnectionManager.networkInfo.value
        return if (networkInfo != null) {
            externalAccessManager.generateSetupGuide(method, networkInfo)
        } else null
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
    val clientsList: List<String> = emptyList(),
    val availableUrls: List<NetworkDetector.ConnectionUrl> = emptyList(),
    val networkInfo: NetworkDetector.NetworkInfo? = null,
    val bestUrl: String? = null,
    val isPublicAccessible: Boolean = false
)
