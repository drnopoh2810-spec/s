package com.sms.paymentgateway.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sms.paymentgateway.presentation.viewmodels.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val stats by viewModel.stats.collectAsState()
    val pendingTransactions by viewModel.pendingTransactions.collectAsState()
    val recentSms by viewModel.recentSmsLogs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stats Cards
            item {
                Text(
                    text = "Statistics",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        title = "Pending",
                        value = stats.totalPending.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Matched",
                        value = stats.totalMatched.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        title = "SMS Received",
                        value = stats.totalSmsReceived.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Success Rate",
                        value = "${stats.successRate}%",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Pending Transactions
            item {
                Text(
                    text = "Pending Transactions",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            if (pendingTransactions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No pending transactions")
                        }
                    }
                }
            } else {
                items(pendingTransactions) { transaction ->
                    TransactionCard(transaction)
                }
            }

            // Recent SMS
            item {
                Text(
                    text = "Recent SMS",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            if (recentSms.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No SMS received yet")
                        }
                    }
                }
            } else {
                items(recentSms) { sms ->
                    SmsLogCard(sms)
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun TransactionCard(transaction: com.sms.paymentgateway.data.entities.PendingTransaction) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = transaction.id,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = transaction.status.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (transaction.status) {
                        com.sms.paymentgateway.data.entities.TransactionStatus.PENDING -> MaterialTheme.colorScheme.primary
                        com.sms.paymentgateway.data.entities.TransactionStatus.MATCHED -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Amount: ${transaction.amount} EGP",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Phone: ${transaction.phoneNumber}",
                style = MaterialTheme.typography.bodyMedium
            )
            transaction.confidence?.let {
                Text(
                    text = "Confidence: ${(it * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun SmsLogCard(sms: com.sms.paymentgateway.data.entities.SmsLog) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = sms.sender,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (sms.parsed) "✓ Parsed" else "✗ Failed",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sms.parsed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            sms.amount?.let {
                Text(
                    text = "Amount: $it EGP",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            sms.transactionId?.let {
                Text(
                    text = "TX ID: $it",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (sms.matched) {
                Text(
                    text = "✓ Matched",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}
