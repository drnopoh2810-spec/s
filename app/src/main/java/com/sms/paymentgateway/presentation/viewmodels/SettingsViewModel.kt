package com.sms.paymentgateway.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sms.paymentgateway.services.ApiDocumentationGenerator
import com.sms.paymentgateway.services.DocLanguage
import com.sms.paymentgateway.services.RelayClient
import com.sms.paymentgateway.services.SmartTunnelManager
import com.sms.paymentgateway.utils.security.ConnectionCard
import com.sms.paymentgateway.utils.security.SecurityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securityManager: SecurityManager,
    private val relayClient: RelayClient,
    private val smartTunnelManager: SmartTunnelManager,
    private val apiDocGenerator: ApiDocumentationGenerator
) : ViewModel() {

    private val _apiKey         = MutableStateFlow(securityManager.getApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _webhookUrl     = MutableStateFlow(securityManager.getWebhookUrl() ?: "")
    val webhookUrl: StateFlow<String> = _webhookUrl.asStateFlow()

    private val _relayUrl       = MutableStateFlow(securityManager.getRelayUrl())
    val relayUrl: StateFlow<String> = _relayUrl.asStateFlow()

    private val _isDefaultRelay = MutableStateFlow(securityManager.isUsingDefaultRelayUrl())
    val isDefaultRelay: StateFlow<Boolean> = _isDefaultRelay.asStateFlow()

    private val _ipWhitelist    = MutableStateFlow(securityManager.getIpWhitelist().toList())
    val ipWhitelist: StateFlow<List<String>> = _ipWhitelist.asStateFlow()

    /**
     * حالة الاتصال - تجمع بين SmartTunnel و RelayClient
     * SmartTunnel هو المصدر الرئيسي للاتصال الآن
     */
    val relayConnected: StateFlow<Boolean> = smartTunnelManager.state
        .map { it.status == SmartTunnelManager.TunnelStatus.ACTIVE }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * بطاقة الاتصال - تُبنى من رابط SmartTunnel الفعلي
     */
    val connectionCard: StateFlow<ConnectionCard?> = smartTunnelManager.state
        .map { tunnelState ->
            val publicUrl = tunnelState.publicUrl  // رابط كامل من الـ Relay
            if (publicUrl != null) {
                // publicUrl = "https://relay.hf.space/gateway/{deviceId}"
                // الرابط الكامل للـ API = publicUrl (بدون /api/v1 - يضيفه المستخدم في طلباته)
                ConnectionCard(apiUrl = publicUrl, apiKey = securityManager.getApiKey())
            } else {
                securityManager.buildConnectionCard()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), securityManager.buildConnectionCard())

    // ─── Actions ────────────────────────────────────────────────────────────

    fun regenerateApiKey() = viewModelScope.launch {
        securityManager.regenerateApiKey()
        _apiKey.value = securityManager.getApiKey()
    }

    fun updateWebhookUrl(url: String) = viewModelScope.launch {
        securityManager.setWebhookUrl(url)
        _webhookUrl.value = url
    }

    fun updateRelayUrl(url: String) = viewModelScope.launch {
        val trimmed = url.trim()
        securityManager.setRelayUrl(trimmed)
        // إعادة تشغيل كلا الـ clients
        relayClient.stop()
        smartTunnelManager.stop()
        kotlinx.coroutines.delay(500)
        relayClient.start()
        smartTunnelManager.start()
        _relayUrl.value = securityManager.getRelayUrl()
        _isDefaultRelay.value = securityManager.isUsingDefaultRelayUrl()
    }

    fun resetRelayUrlToDefault() = viewModelScope.launch {
        securityManager.clearRelayUrl()
        relayClient.stop()
        smartTunnelManager.stop()
        kotlinx.coroutines.delay(500)
        relayClient.start()
        smartTunnelManager.start()
        _relayUrl.value = securityManager.getRelayUrl()
        _isDefaultRelay.value = true
    }

    fun refreshRelayStatus() {
        // لا شيء - الحالة تأتي تلقائياً من SmartTunnelManager.state
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

    suspend fun downloadDocumentation(lang: DocLanguage): Result<File> = withContext(Dispatchers.IO) {
        try {
            Result.success(apiDocGenerator.saveToDownloads(lang))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
