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
    val ipWhitelist by viewModel.ipWhitelist.collectAsState()

    var showWebhookDialog by remember { mutableStateOf(false) }
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

    // Dialogs
    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Regenerate API Key") },
            text = { Text("Are you sure? The old API key will stop working immediately.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.regenerateApiKey()
                    showApiKeyDialog = false
                }) {
                    Text("Regenerate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancel")
                }
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
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWebhookDialog = false }) {
                    Text("Cancel")
                }
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
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
