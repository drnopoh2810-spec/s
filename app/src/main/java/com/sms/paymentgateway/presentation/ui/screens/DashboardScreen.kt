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
import com.sms.paymentgateway.data.repository.DashboardAnalytics
import com.sms.paymentgateway.presentation.viewmodels.DashboardViewModel
import com.sms.paymentgateway.presentation.viewmodels.ConnectionInfo
import com.sms.paymentgateway.services.NetworkDetector
import com.sms.paymentgateway.services.SmartTunnelManager

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
            // ─── بطاقة الرابط العام (SmartTunnel) ───────────────────────────
            item {
                Text("🌐 الرابط العام", style = MaterialTheme.typography.headlineSmall)
            }
            item {
                SmartTunnelCard(
                    tunnelState = viewModel.tunnelState.collectAsState().value,
                    clipboardManager = LocalClipboardManager.current
                )
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
            
            // قسم التحليلات
            item {
                Text(
                    "📊 تحليلات اليوم",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item { AnalyticsSection(analytics = viewModel.analytics.collectAsState().value) }
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
                }
                
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

@Composable
fun AnalyticsSection(analytics: DashboardAnalytics?) {
    if (analytics == null) {
        Card(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // SMS Analytics
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📩 إحصائيات الرسائل", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    AnalyticItem("إجمالي", analytics.sms.total.toString())
                    AnalyticItem("محللة", analytics.sms.parsed.toString())
                    AnalyticItem("مطابقة", analytics.sms.matched.toString())
                    AnalyticItem("نسبة التحليل", "%.0f%%".format(analytics.sms.parseRate))
                }
                if (analytics.sms.byWallet.isNotEmpty()) {
                    Text("توزيع المحافظ:", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    analytics.sms.byWallet.entries.sortedByDescending { it.value }.take(4).forEach { (wallet, count) ->
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text(wallet, style = MaterialTheme.typography.bodySmall)
                            Text(count.toString(), style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Transaction Analytics
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("💰 إحصائيات المعاملات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    AnalyticItem("إجمالي", analytics.transactions.total.toString())
                    AnalyticItem("مطابقة", analytics.transactions.matched.toString())
                    AnalyticItem("منتهية", analytics.transactions.expired.toString())
                    AnalyticItem("معلقة", analytics.transactions.pending.toString())
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    AnalyticItem("إجمالي المبالغ", "%.0f ج".format(analytics.transactions.totalAmount))
                    AnalyticItem("متوسط الثقة", "%.0f%%".format(analytics.transactions.avgConfidence))
                }
            }
        }

        // Webhook Analytics
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🔔 إحصائيات Webhook", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    AnalyticItem("إجمالي", analytics.webhooks.total.toString())
                    AnalyticItem("ناجح", analytics.webhooks.success.toString())
                    AnalyticItem("فاشل", analytics.webhooks.failed.toString())
                    AnalyticItem("نسبة النجاح", "%.0f%%".format(analytics.webhooks.successRate))
                }
                if (analytics.webhooks.avgProcessingTimeMs > 0) {
                    Text(
                        "متوسط وقت المعالجة: %.0f ms".format(analytics.webhooks.avgProcessingTimeMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AnalyticItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ─── SmartTunnel Card ────────────────────────────────────────────────────────

@Composable
fun SmartTunnelCard(
    tunnelState: SmartTunnelManager.TunnelState,
    clipboardManager: ClipboardManager
) {
    val isActive = tunnelState.status == SmartTunnelManager.TunnelStatus.ACTIVE
    val isConnecting = tunnelState.status == SmartTunnelManager.TunnelStatus.CONNECTING ||
                       tunnelState.status == SmartTunnelManager.TunnelStatus.RECONNECTING

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = when {
                isActive -> MaterialTheme.colorScheme.primaryContainer
                isConnecting -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.errorContainer
            }
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            isActive -> "🟢"
                            isConnecting -> "🟡"
                            else -> "🔴"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                    Column {
                        Text(
                            text = "SmartTunnel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (tunnelState.status) {
                                SmartTunnelManager.TunnelStatus.ACTIVE -> "نشط ✓"
                                SmartTunnelManager.TunnelStatus.CONNECTING -> "جاري الاتصال..."
                                SmartTunnelManager.TunnelStatus.RECONNECTING -> "إعادة الاتصال..."
                                SmartTunnelManager.TunnelStatus.CONNECTED -> "متصل، جاري التسجيل..."
                                SmartTunnelManager.TunnelStatus.ERROR -> "خطأ"
                                SmartTunnelManager.TunnelStatus.DISCONNECTED -> "غير متصل"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }

            // الرابط العام
            if (isActive && tunnelState.publicUrl != null) {
                HorizontalDivider()

                Text(
                    text = "رابطك العام:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // الرابط مع زر نسخ
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tunnelState.publicUrl!!,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                            maxLines = 2
                        )
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(tunnelState.publicUrl!!))
                            }
                        ) {
                            Icon(
                                Icons.Default.AccountBox,
                                contentDescription = "نسخ الرابط",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // مثال الاستخدام
                Text(
                    text = "مثال: POST ${tunnelState.publicUrl}/api/v1/transactions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )

                // إحصائيات
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "طلبات معالجة: ${tunnelState.requestsHandled}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    tunnelState.relayServer?.let {
                        Text(
                            text = "عبر: $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (!isActive && !isConnecting) {
                // رسالة عند عدم الاتصال
                Text(
                    text = tunnelState.error ?: "التطبيق يحاول الاتصال تلقائياً...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (isConnecting) {
                Text(
                    text = "جاري إنشاء رابطك العام...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
