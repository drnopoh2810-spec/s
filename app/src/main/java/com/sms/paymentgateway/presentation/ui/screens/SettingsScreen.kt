package com.sms.paymentgateway.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sms.paymentgateway.presentation.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val apiKey by viewModel.apiKey.collectAsState()
    val webhookUrl by viewModel.webhookUrl.collectAsState()
    val relayUrl by viewModel.relayUrl.collectAsState()
    val relayConnected by viewModel.relayConnected.collectAsState()
    val ipWhitelist by viewModel.ipWhitelist.collectAsState()

    var showWebhookDialog by remember { mutableStateOf(false) }
    var showRelayDialog by remember { mutableStateOf(false) }
    var showIpDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // API Key Section
            item {
                Text(
                    text = "API Configuration",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showApiKeyDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "API Key",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = apiKey.take(20) + "...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(Icons.Default.Refresh, "Regenerate")
                    }
                }
            }

            // ── Relay Server Section ──────────────────────────────────────
            item {
                Text(
                    text = "Relay Server (Cloud Connection)",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        viewModel.refreshRelayStatus()
                        showRelayDialog = true
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (relayConnected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Relay Server URL",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Badge(
                                    containerColor = if (relayConnected) Color(0xFF2E7D32) else Color(0xFFB71C1C)
                                ) {
                                    Text(
                                        text = if (relayConnected) "Connected" else "Offline",
                                        color = Color.White
                                    )
                                }
                            }
                            Text(
                                text = relayUrl.ifEmpty { "Not configured — tap to set up" },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(Icons.Default.Edit, "Edit")
                    }
                }
            }

            if (relayUrl.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "How websites connect to your device:",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = relayUrl.replace("wss://", "https://").replace("/device", "/api/v1/"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Webhook Section
            item {
                Text(
                    text = "Webhook Configuration",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showWebhookDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Webhook URL",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = webhookUrl.ifEmpty { "Not configured" },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(Icons.Default.Edit, "Edit")
                    }
                }
            }

            // IP Whitelist Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "IP Whitelist",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    IconButton(onClick = { showIpDialog = true }) {
                        Icon(Icons.Default.Add, "Add IP")
                    }
                }
            }

            if (ipWhitelist.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("All IPs allowed (whitelist disabled)")
                        }
                    }
                }
            } else {
                items(ipWhitelist) { ip ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = ip)
                            IconButton(onClick = { viewModel.removeIpFromWhitelist(ip) }) {
                                Icon(Icons.Default.Delete, "Remove")
                            }
                        }
                    }
                }

                item {
                    TextButton(
                        onClick = { viewModel.clearIpWhitelist() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear All")
                    }
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────

    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Regenerate API Key") },
            text = { Text("Are you sure? The old API key will stop working immediately.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.regenerateApiKey()
                    showApiKeyDialog = false
                }) { Text("Regenerate") }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRelayDialog) {
        var url by remember { mutableStateOf(relayUrl) }
        AlertDialog(
            onDismissRequest = { showRelayDialog = false },
            title = { Text("Relay Server URL") },
            text = {
                Column {
                    Text(
                        text = "أدخل عنوان WebSocket لخادم الوسيط. مثال:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "wss://your-relay.replit.app/device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Relay WebSocket URL") },
                        placeholder = { Text("wss://your-relay.replit.app/device") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateRelayUrl(url.trim())
                    showRelayDialog = false
                }) { Text("Save & Connect") }
            },
            dismissButton = {
                TextButton(onClick = { showRelayDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showWebhookDialog) {
        var url by remember { mutableStateOf(webhookUrl) }
        AlertDialog(
            onDismissRequest = { showWebhookDialog = false },
            title = { Text("Webhook URL") },
            text = {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://example.com/webhook") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateWebhookUrl(url)
                    showWebhookDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showWebhookDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showIpDialog) {
        var ip by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showIpDialog = false },
            title = { Text("Add IP to Whitelist") },
            text = {
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("IP Address") },
                    placeholder = { Text("192.168.1.100") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (ip.isNotBlank()) {
                            viewModel.addIpToWhitelist(ip)
                            showIpDialog = false
                        }
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showIpDialog = false }) { Text("Cancel") }
            }
        )
    }
}
