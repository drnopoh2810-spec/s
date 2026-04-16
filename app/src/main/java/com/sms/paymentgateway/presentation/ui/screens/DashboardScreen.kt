package com.sms.paymentgateway.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sms.paymentgateway.data.entities.PendingTransaction
import com.sms.paymentgateway.data.entities.SmsLog
import com.sms.paymentgateway.data.entities.TransactionStatus
import com.sms.paymentgateway.presentation.viewmodels.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val stats               by viewModel.stats.collectAsState()
    val pendingTransactions by viewModel.pendingTransactions.collectAsState()
    val recentSms           by viewModel.recentSmsLogs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("لوحة التحكم") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // إحصائيات
            item { Text("📊 الإحصائيات", style = MaterialTheme.typography.headlineSmall) }

            item {
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    StatCard("قيد الانتظار", stats.totalPending.toString(), Modifier.weight(1f))
                    StatCard("تم المطابقة", stats.totalMatched.toString(), Modifier.weight(1f))
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    StatCard("رسائل مستلمة", stats.totalSmsReceived.toString(), Modifier.weight(1f))
                    StatCard("نسبة النجاح", "${stats.successRate}%", Modifier.weight(1f))
                }
            }

            // معاملات قيد الانتظار
            item {
                Text(
                    "⏳ المعاملات المعلّقة",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (pendingTransactions.isEmpty()) {
                item {
                    EmptyCard("لا توجد معاملات معلّقة حالياً")
                }
            } else {
                items(pendingTransactions) { tx -> TransactionCard(tx) }
            }

            // آخر الرسائل
            item {
                Text(
                    "📩 آخر الرسائل المستلمة",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (recentSms.isEmpty()) {
                item { EmptyCard("لم يتم استلام رسائل SMS بعد") }
            } else {
                items(recentSms) { sms -> SmsLogCard(sms) }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(title, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun EmptyCard(message: String) {
    Card(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TransactionCard(tx: PendingTransaction) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(tx.id, style = MaterialTheme.typography.titleMedium)
                Badge(
                    containerColor = when (tx.status) {
                        TransactionStatus.PENDING -> MaterialTheme.colorScheme.primaryContainer
                        TransactionStatus.MATCHED -> MaterialTheme.colorScheme.tertiaryContainer
                        else                       -> MaterialTheme.colorScheme.errorContainer
                    }
                ) {
                    Text(when (tx.status) {
                        TransactionStatus.PENDING   -> "معلّق"
                        TransactionStatus.MATCHED   -> "تم ✓"
                        TransactionStatus.EXPIRED   -> "منتهي"
                        TransactionStatus.CANCELLED -> "ملغي"
                    })
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("المبلغ: ${tx.amount} جنيه", style = MaterialTheme.typography.bodyLarge)
            Text("الهاتف: ${tx.phoneNumber}", style = MaterialTheme.typography.bodyMedium)
            tx.confidence?.let {
                Text("الثقة: ${(it * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun SmsLogCard(sms: SmsLog) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(sms.sender, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (sms.parsed) "✓ تم التحليل" else "✗ فشل",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sms.parsed) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(4.dp))
            sms.amount?.let { Text("المبلغ: $it جنيه") }
            sms.transactionId?.let { Text("رقم العملية: $it") }
            if (sms.matched) Text("✓ تمت المطابقة", color = MaterialTheme.colorScheme.tertiary)
        }
    }
}
