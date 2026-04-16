package com.sms.paymentgateway.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
            // معلومات الاتصال المباشر المحدثة
            item { 
                Text("🔗 حالة الاتصال المباشر", style = MaterialTheme.typography.headlineSmall) 
            }
            
            item {
                EnhancedConnectionStatusCard(
                    connectionInfo = connectionInfo,
                    onRestartConnection = { viewModel.restartConnection() },
                    onAnalyzeAccess = { viewModel.analyzeExternalAccess() }
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

@Composable
fun ConnectionStatusCard(
    connectionInfo: ConnectionInfo,
    onRestartConnection: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (connectionInfo.isActive) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (connectionInfo.isActive) "🟢 الاتصال نشط" else "🔴 الاتصال متوقف",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Button(
                    onClick = onRestartConnection,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("إعادة تشغيل")
                }
            }
            
            if (connectionInfo.isActive) {
                Divider()
                
                Text(
                    text = "رابط الاتصال المباشر:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = connectionInfo.connectionUrl,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(connectionInfo.connectionUrl))
                        }
                    ) {
                        Text("نسخ")
                    }
                }
                
                Text(
                    text = "العملاء المتصلين: ${connectionInfo.connectedClients}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                if (connectionInfo.clientsList.isNotEmpty()) {
                    Text(
                        text = "قائمة العملاء: ${connectionInfo.clientsList.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "الخدمة متوقفة. اضغط 'إعادة تشغيل' لبدء الاتصال المباشر.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun EnhancedConnectionStatusCard(
    connectionInfo: ConnectionInfo,
    onRestartConnection: () -> Unit,
    onAnalyzeAccess: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (connectionInfo.isActive) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // العنوان والأزرار
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (connectionInfo.isActive) "🟢 الاتصال نشط" else "🔴 الاتصال متوقف",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (connectionInfo.networkInfo != null) {
                        Text(
                            text = "نوع الشبكة: ${getNetworkTypeArabic(connectionInfo.networkInfo.networkType)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onAnalyzeAccess,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("تحليل الوصول")
                    }
                    
                    Button(
                        onClick = onRestartConnection,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("إعادة تشغيل")
                    }
                }
            }
            
            if (connectionInfo.isActive) {
                Divider()
                
                // معلومات الشبكة
                if (connectionInfo.networkInfo != null) {
                    NetworkInfoSection(connectionInfo.networkInfo, clipboardManager)
                    
                    Divider()
                }
                
                // الروابط المتاحة
                if (connectionInfo.availableUrls.isNotEmpty()) {
                    Text(
                        text = "الروابط المتاحة:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    
                    connectionInfo.availableUrls.forEach { url ->
                        ConnectionUrlItem(url, clipboardManager)
                    }
                    
                    Divider()
                }
                
                // أفضل رابط للاستخدام
                connectionInfo.bestUrl?.let { bestUrl ->
                    Text(
                        text = "الرابط المُوصى به:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bestUrl,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        
                        TextButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(bestUrl))
                            }
                        ) {
                            Text("نسخ")
                        }
                    }
                }
                
                // إحصائيات الاتصال
                Text(
                    text = "العملاء المتصلين: ${connectionInfo.connectedClients}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                if (connectionInfo.clientsList.isNotEmpty()) {
                    Text(
                        text = "قائمة العملاء: ${connectionInfo.clientsList.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "الخدمة متوقفة. اضغط 'إعادة تشغيل' لبدء الاتصال المباشر.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun NetworkInfoSection(
    networkInfo: NetworkDetector.NetworkInfo,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "معلومات الشبكة:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("IP المحلي:", style = MaterialTheme.typography.bodySmall)
            Text(networkInfo.localIp, style = MaterialTheme.typography.bodySmall)
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("IP العام:", style = MaterialTheme.typography.bodySmall)
            Text(
                networkInfo.publicIp ?: "غير متاح", 
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("الوصول العام:", style = MaterialTheme.typography.bodySmall)
            Text(
                if (networkInfo.isPublicAccessible) "متاح ✅" else "غير متاح ❌",
                style = MaterialTheme.typography.bodySmall,
                color = if (networkInfo.isPublicAccessible) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun ConnectionUrlItem(
    url: NetworkDetector.ConnectionUrl,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (url.accessible) {
                NetworkDetector.AccessibilityLevel.PUBLIC_READY -> MaterialTheme.colorScheme.primaryContainer
                NetworkDetector.AccessibilityLevel.LOCAL_ONLY -> MaterialTheme.colorScheme.secondaryContainer
                NetworkDetector.AccessibilityLevel.REQUIRES_SETUP -> MaterialTheme.colorScheme.tertiaryContainer
                NetworkDetector.AccessibilityLevel.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = url.type.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = getAccessibilityArabic(url.accessible),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (url.accessible) {
                        NetworkDetector.AccessibilityLevel.PUBLIC_READY -> MaterialTheme.colorScheme.primary
                        NetworkDetector.AccessibilityLevel.LOCAL_ONLY -> MaterialTheme.colorScheme.secondary
                        NetworkDetector.AccessibilityLevel.REQUIRES_SETUP -> MaterialTheme.colorScheme.tertiary
                        NetworkDetector.AccessibilityLevel.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            Text(
                text = url.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = url.url,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(url.url))
                    }
                ) {
                    Text("نسخ")
                }
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