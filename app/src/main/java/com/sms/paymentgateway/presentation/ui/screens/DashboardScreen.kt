package com.sms.paymentgateway.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sms.paymentgateway.data.entities.PendingTransaction
import com.sms.paymentgateway.data.entities.SmsLog
import com.sms.paymentgateway.data.entities.TransactionStatus
import com.sms.paymentgateway.presentation.viewmodels.DashboardViewModel
import com.sms.paymentgateway.presentation.viewmodels.ConnectionInfo
import com.sms.paymentgateway.services.NetworkDetector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val stats               by viewModel.stats.collectAsState()
    val pendingTransactions by viewModel.pendingTransactions.collectAsState()
    val recentSms           by viewModel.recentSmsLogs.collectAsState()
    val connectionInfo      by viewModel.connectionInfo.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("لوحة التحكم - الاتصال المباشر") },
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
            // معلومات الاتصال عبر Relay
            item { 
                Text("🔗 حالة الاتصال - Huggingface Relay", style = MaterialTheme.typography.headlineSmall) 
            }
            
            item {
                SimpleConnectionStatusCard(
                    connectionInfo = connectionInfo,
                    onRestartConnection = { viewModel.restartConnection() }
                )
            }

            // إحصائيات
            item { 
                Text("📊 الإحصائيات", style = MaterialTheme.typography.headlineSmall) 
            }

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

            item {
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        "حالة الاتصال", 
                        if (stats.connectionActive) "نشط ✓" else "متوقف ✗", 
                        Modifier.weight(1f)
                    )
                    StatCard("العملاء المتصلين", stats.connectedClients.toString(), Modifier.weight(1f))
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
    ElevatedCard(
        modifier = modifier,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically
            ) {
                Text(
                    tx.id,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Badge(
                    containerColor = when (tx.status) {
                        TransactionStatus.PENDING -> MaterialTheme.colorScheme.primaryContainer
                        TransactionStatus.MATCHED -> MaterialTheme.colorScheme.tertiaryContainer
                        else                       -> MaterialTheme.colorScheme.errorContainer
                    }
                ) {
                    Text(
                        when (tx.status) {
                            TransactionStatus.PENDING   -> "معلّق ⏳"
                            TransactionStatus.MATCHED   -> "تم ✓"
                            TransactionStatus.EXPIRED   -> "منتهي ⏰"
                            TransactionStatus.CANCELLED -> "ملغي ✗"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "المبلغ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${tx.amount} جنيه",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "رقم الهاتف",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        tx.phoneNumber,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            tx.confidence?.let {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = it.toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "الثقة: ${(it * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SmsLogCard(sms: SmsLog) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = if (sms.parsed)
                            MaterialTheme.colorScheme.tertiaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                if (sms.parsed) "✓" else "✗",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (sms.parsed)
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                else
                                    MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Text(
                        sms.sender,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Text(
                    if (sms.parsed) "تم التحليل" else "فشل التحليل",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (sms.parsed)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.error
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            sms.amount?.let {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AccountBox,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "المبلغ: $it جنيه",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            sms.transactionId?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "رقم العملية: $it",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            
            if (sms.matched) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "تمت المطابقة",
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SimpleConnectionStatusCard(
    connectionInfo: ConnectionInfo,
    onRestartConnection: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (connectionInfo.isActive) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (connectionInfo.isActive) "🟢 متصل بـ Relay" else "🔴 غير متصل",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Huggingface Relay Server",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                </Column>
                
                Button(
                    onClick = onRestartConnection,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("إعادة الاتصال")
                }
            }
            
            if (connectionInfo.isActive) {
                Divider()
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "الحالة",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "نشط ✓",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "نوع الاتصال",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "WebSocket",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Text(
                    text = "✅ التطبيق متصل بخادم Relay ويستقبل الطلبات",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Text(
                    text = "⚠️ الخدمة متوقفة. اضغط 'إعادة الاتصال' أو تأكد من إعداد رابط Relay في الإعدادات.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

fun getNetworkTypeArabic(type: NetworkDetector.NetworkType): String {
    return when (type) {
        NetworkDetector.NetworkType.LOCAL_ONLY -> "محلي فقط"
        NetworkDetector.NetworkType.PUBLIC_IP -> "IP عام"
        NetworkDetector.NetworkType.BEHIND_NAT -> "خلف NAT"
        NetworkDetector.NetworkType.MOBILE_DATA -> "بيانات الجوال"
        NetworkDetector.NetworkType.UNKNOWN -> "غير معروف"
    }
}

fun getAccessibilityArabic(level: NetworkDetector.AccessibilityLevel): String {
    return when (level) {
        NetworkDetector.AccessibilityLevel.LOCAL_ONLY -> "محلي فقط"
        NetworkDetector.AccessibilityLevel.PUBLIC_READY -> "جاهز للعموم ✅"
        NetworkDetector.AccessibilityLevel.REQUIRES_SETUP -> "يحتاج إعداد ⚙️"
        NetworkDetector.AccessibilityLevel.UNKNOWN -> "غير معروف"
    }
}