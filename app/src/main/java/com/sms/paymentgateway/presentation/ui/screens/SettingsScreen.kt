package com.sms.paymentgateway.presentation.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sms.paymentgateway.presentation.viewmodels.SettingsViewModel
import com.sms.paymentgateway.utils.security.ConnectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val context         = LocalContext.current
    val apiKey          by viewModel.apiKey.collectAsState()
    val webhookUrl      by viewModel.webhookUrl.collectAsState()
    val relayUrl        by viewModel.relayUrl.collectAsState()
    val relayConnected  by viewModel.relayConnected.collectAsState()
    val ipWhitelist     by viewModel.ipWhitelist.collectAsState()
    val connectionCard  by viewModel.connectionCard.collectAsState()

    var showRelayDialog   by remember { mutableStateOf(false) }
    var showWebhookDialog by remember { mutableStateOf(false) }
    var showIpDialog      by remember { mutableStateOf(false) }
    var showApiKeyDialog  by remember { mutableStateOf(false) }
    var showCardDialog    by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات") },
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // ─── بطاقة الاتصال المباشر ─────────────────────────────────────
            item {
                SectionTitle(title = "🔗 رابط الاتصال المباشر")
            }

            item {
                ConnectionCardWidget(
                    card = connectionCard,
                    relayConnected = relayConnected,
                    onCopy = { text -> copyToClipboard(context, "رابط API", text) },
                    onShowDetails = { showCardDialog = true }
                )
            }

            // ─── مفتاح API ─────────────────────────────────────────────────
            item { SectionTitle(title = "🔑 مفتاح API") }

            item {
                SettingsCard(onClick = { showApiKeyDialog = true }) {
                    Row(
                        Modifier.fillMaxWidth(),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("مفتاح API", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${apiKey.take(16)}…",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Row {
                            IconButton(onClick = { copyToClipboard(context, "مفتاح API", apiKey) }) {
                                Icon(Icons.Default.Share, "نسخ")
                            }
                            IconButton(onClick = { showApiKeyDialog = true }) {
                                Icon(Icons.Default.Refresh, "تجديد")
                            }
                        }
                    }
                }
            }

            // ─── Relay Server ───────────────────────────────────────────────
            item { SectionTitle(title = "🌐 خادم الوسيط (Relay Server)") }

            item {
                SettingsCard(onClick = {
                    viewModel.refreshRelayStatus()
                    showRelayDialog = true
                }, containerColor = if (relayConnected)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("رابط خادم الوسيط", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.width(8.dp))
                                Badge(
                                    containerColor = if (relayConnected) Color(0xFF2E7D32) else Color(0xFFB71C1C)
                                ) {
                                    Text(
                                        if (relayConnected) "متصل ✓" else "غير متصل",
                                        color = Color.White
                                    )
                                }
                            }
                            Text(
                                relayUrl.ifEmpty { "لم يُضبط بعد — اضغط للإعداد" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.Edit, "تعديل")
                    }
                }
            }

            // ─── Webhook ────────────────────────────────────────────────────
            item { SectionTitle(title = "📬 Webhook (إشعار تأكيد الدفع)") }

            item {
                SettingsCard(onClick = { showWebhookDialog = true }) {
                    Row(
                        Modifier.fillMaxWidth(),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("رابط Webhook", style = MaterialTheme.typography.titleMedium)
                            Text(
                                webhookUrl.ifEmpty { "لم يُضبط بعد" },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(Icons.Default.Edit, "تعديل")
                    }
                }
            }

            // ─── IP Whitelist ────────────────────────────────────────────────
            item {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    SectionTitle(title = "🔒 قائمة IP المسموح بها")
                    IconButton(onClick = { showIpDialog = true }) {
                        Icon(Icons.Default.Add, "إضافة IP")
                    }
                }
            }

            if (ipWhitelist.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                            Text(
                                "جميع عناوين IP مسموح بها (القائمة فارغة)",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(ipWhitelist) { ip ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            Arrangement.SpaceBetween,
                            Alignment.CenterVertically
                        ) {
                            Text(ip, fontFamily = FontFamily.Monospace)
                            IconButton(onClick = { viewModel.removeIpFromWhitelist(ip) }) {
                                Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                item {
                    TextButton(
                        onClick = { viewModel.clearIpWhitelist() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("مسح القائمة", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    // ─── Dialogs ─────────────────────────────────────────────────────────────

    if (showCardDialog && connectionCard != null) {
        ConnectionCardDialog(
            card = connectionCard!!,
            onDismiss = { showCardDialog = false },
            onCopy = { text, label -> copyToClipboard(context, label, text) }
        )
    }

    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("تجديد مفتاح API") },
            text = { Text("هل أنت متأكد؟ المفتاح القديم سيتوقف عن العمل فوراً.") },
            confirmButton = {
                TextButton(onClick = { viewModel.regenerateApiKey(); showApiKeyDialog = false }) {
                    Text("تجديد", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) { Text("إلغاء") }
            }
        )
    }

    if (showRelayDialog) {
        var url by remember { mutableStateOf(relayUrl) }
        AlertDialog(
            onDismissRequest = { showRelayDialog = false },
            title = { Text("إعداد خادم الوسيط") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أدخل رابط WebSocket لخادم الوسيط:", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "مثال: wss://your-relay.replit.app/device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("رابط WebSocket") },
                        placeholder = { Text("wss://your-relay.replit.app/device") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.updateRelayUrl(url.trim()); showRelayDialog = false }) {
                    Text("حفظ والاتصال")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRelayDialog = false }) { Text("إلغاء") }
            }
        )
    }

    if (showWebhookDialog) {
        var url by remember { mutableStateOf(webhookUrl) }
        AlertDialog(
            onDismissRequest = { showWebhookDialog = false },
            title = { Text("رابط Webhook") },
            text = {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("الرابط") },
                    placeholder = { Text("https://example.com/webhook") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.updateWebhookUrl(url); showWebhookDialog = false }) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWebhookDialog = false }) { Text("إلغاء") }
            }
        )
    }

    if (showIpDialog) {
        var ip by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showIpDialog = false },
            title = { Text("إضافة عنوان IP") },
            text = {
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("عنوان IP") },
                    placeholder = { Text("192.168.1.100") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (ip.isNotBlank()) { viewModel.addIpToWhitelist(ip); showIpDialog = false }
                }) { Text("إضافة") }
            },
            dismissButton = {
                TextButton(onClick = { showIpDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

// ─── Connection Card Widget ────────────────────────────────────────────────

@Composable
fun ConnectionCardWidget(
    card: ConnectionCard?,
    relayConnected: Boolean,
    onCopy: (String) -> Unit,
    onShowDetails: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (card != null && relayConnected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (card == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        "تحذير",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "أضف رابط خادم الوسيط أولاً لإنشاء رابط الاتصال",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Text(
                    "رابط API لموقعك:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Text(
                        card.apiUrl,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onCopy(card.apiUrl) }) {
                        Icon(Icons.Default.Share, "نسخ")
                    }
                }

                Text(
                    "مفتاح API:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Text(
                        "${card.apiKey.take(20)}…",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onCopy(card.apiKey) }) {
                        Icon(Icons.Default.Share, "نسخ")
                    }
                }

                Button(
                    onClick = onShowDetails,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Info, null)
                    Spacer(Modifier.width(6.dp))
                    Text("عرض أمثلة الكود الكامل")
                }
            }
        }
    }
}

@Composable
fun ConnectionCardDialog(
    card: ConnectionCard,
    onDismiss: () -> Unit,
    onCopy: (String, String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("cURL", "JavaScript", "PHP")

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        title = { Text("أمثلة الاتصال المباشر", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "✅ هذا الرابط يعمل مباشرة من موقعك بدون أي إعداد إضافي.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                // رابط API
                Text("رابط API:", style = MaterialTheme.typography.labelMedium)
                CodeBlock(text = card.apiUrl, onCopy = { onCopy(card.apiUrl, "رابط API") })

                // مفتاح API
                Text("مفتاح API:", style = MaterialTheme.typography.labelMedium)
                CodeBlock(text = card.apiKey, onCopy = { onCopy(card.apiKey, "مفتاح API") })

                // Tabs for code examples
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontSize = 12.sp) }
                        )
                    }
                }

                when (selectedTab) {
                    0 -> {
                        CodeBlock(text = card.curlExample, onCopy = { onCopy(card.curlExample, "مثال cURL") })
                    }
                    1 -> {
                        CodeBlock(text = card.jsExample, onCopy = { onCopy(card.jsExample, "مثال JavaScript") })
                    }
                    2 -> {
                        val phpExample = buildPhpExample(card.apiUrl, card.apiKey)
                        CodeBlock(text = phpExample, onCopy = { onCopy(phpExample, "مثال PHP") })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@Composable
private fun CodeBlock(text: String, onCopy: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF444444), RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFD4D4D4),
                fontSize = 10.sp
            )
        )
        IconButton(
            onClick = onCopy,
            modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
        ) {
            Icon(Icons.Default.Share, "نسخ", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

private fun buildPhpExample(url: String, key: String) = """
<?php
\$apiUrl = "$url";
\$apiKey = "$key";
\$data   = ['id'=>'order-1','amount'=>500,'phoneNumber'=>'01012345678'];

\$ctx = stream_context_create(['http'=>[
  'method'  => 'POST',
  'header'  => "Authorization: Bearer \$apiKey\r\nContent-Type: application/json",
  'content' => json_encode(\$data)
]]);
\$response = json_decode(file_get_contents("\$apiUrl/transactions", false, \$ctx), true);
echo "رقم المعاملة: " . \$response['transaction']['id'];
?>""".trimIndent()

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsCard(
    onClick: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}
