package com.sms.paymentgateway.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sms.paymentgateway.services.RelayClient
import com.sms.paymentgateway.utils.security.ConnectionCard
import com.sms.paymentgateway.utils.security.SecurityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securityManager: SecurityManager,
    private val relayClient: RelayClient
) : ViewModel() {

    private val _apiKey         = MutableStateFlow(securityManager.getApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _webhookUrl     = MutableStateFlow(securityManager.getWebhookUrl() ?: "")
    val webhookUrl: StateFlow<String> = _webhookUrl.asStateFlow()

    private val _relayUrl       = MutableStateFlow(securityManager.getRelayUrl() ?: "")
    val relayUrl: StateFlow<String> = _relayUrl.asStateFlow()

    private val _relayConnected = MutableStateFlow(relayClient.isConnected())
    val relayConnected: StateFlow<Boolean> = _relayConnected.asStateFlow()

    private val _ipWhitelist    = MutableStateFlow(securityManager.getIpWhitelist().toList())
    val ipWhitelist: StateFlow<List<String>> = _ipWhitelist.asStateFlow()

    /** بطاقة الاتصال المباشر التي يولدها الجهاز */
    private val _connectionCard  = MutableStateFlow(securityManager.buildConnectionCard())
    val connectionCard: StateFlow<ConnectionCard?> = _connectionCard.asStateFlow()

    // ─── Actions ────────────────────────────────────────────────────────────

    fun regenerateApiKey() = viewModelScope.launch {
        val newKey = securityManager.regenerateApiKey()
        _apiKey.value = newKey
        _connectionCard.value = securityManager.buildConnectionCard()
    }

    fun updateWebhookUrl(url: String) = viewModelScope.launch {
        securityManager.setWebhookUrl(url)
        _webhookUrl.value = url
    }

    fun updateRelayUrl(url: String) = viewModelScope.launch {
        if (url.isBlank()) {
            securityManager.clearRelayUrl()
            relayClient.stop()
        } else {
            securityManager.setRelayUrl(url)
            relayClient.stop()
            relayClient.start()
        }
        _relayUrl.value = url
        _relayConnected.value = relayClient.isConnected()
        _connectionCard.value = securityManager.buildConnectionCard()
    }

    fun refreshRelayStatus() {
        _relayConnected.value = relayClient.isConnected()
    }

    fun addIpToWhitelist(ip: String) = viewModelScope.launch {
        securityManager.addIpToWhitelist(ip)
        _ipWhitelist.value = securityManager.getIpWhitelist().toList()
    }

    fun removeIpFromWhitelist(ip: String) = viewModelScope.launch {
        securityManager.removeIpFromWhitelist(ip)
        _ipWhitelist.value = securityManager.getIpWhitelist().toList()
    }

    fun clearIpWhitelist() = viewModelScope.launch {
        securityManager.clearIpWhitelist()
        _ipWhitelist.value = emptyList()
    }
}
