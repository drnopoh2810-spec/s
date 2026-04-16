package com.sms.paymentgateway.presentation.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import com.sms.paymentgateway.presentation.ui.copyToClipboardWithFeedback
import com.sms.paymentgateway.presentation.ui.shareText
import com.sms.paymentgateway.presentation.viewmodels.SettingsViewModel
import com.sms.paymentgateway.services.DocLanguage
import com.sms.paymentgateway.utils.security.ConnectionCard
import kotlinx.coroutines.launch
import java.io.File

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
    var showDocDialog     by remember { mutableStateOf(false) }
    var downloadMessage   by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

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

            // ─── بطاقة الاتصال المباشر ───────────────────────────────────
            item { SectionTitle(title = "🔗 رابط الاتصال المباشر") }

            item {
                ConnectionCardWidget(
                    card = connectionCard,
                    relayConnected = relayConnected,
                    onCopy  = { text, label -> copyToClipboardWithFeedback(context, label, text) },
                    onShare = { text, label -> shareText(context, label, text) },
                    onShowDetails = { showCardDialog = true }
                )
            }

            // ─── مفتاح API ───────────────────────────────────────────────
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
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                Text(
                                    "${apiKey.take(24)}…",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Row {
                            // زر النسخ بأيقونة صحيحة
                            IconButton(onClick = {
                                copyToClipboardWithFeedback(context, "مفتاح API", apiKey)
                            }) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "نسخ مفتاح API",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            // زر المشاركة
                            IconButton(onClick = {
                                shareText(context, "مفتاح API", apiKey)
                            }) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "مشاركة مفتاح API",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                            // زر التجديد
                            IconButton(onClick = { showApiKeyDialog = true }) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "تجديد المفتاح",
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }

            // ─── Relay Server ─────────────────────────────────────────────
            item { SectionTitle(title = "🌐 خادم الوسيط (Relay Server)") }

            item {
                SettingsCard(
                    onClick = {
                        viewModel.refreshRelayStatus()
                        showRelayDialog = true
                    },
                    containerColor = if (relayConnected)
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

            // ─── Webhook ──────────────────────────────────────────────────
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
                        Row {
                            if (webhookUrl.isNotEmpty()) {
                                IconButton(onClick = {
                                    copyToClipboardWithFeedback(context, "رابط Webhook", webhookUrl)
                                }) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "نسخ Webhook",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Icon(Icons.Default.Edit, "تعديل")
                        }
                    }
                }
            }

            // ─── API Documentation Download ───────────────────────────────
            item { SectionTitle(title = "📚 تحميل الدوكيومنتيشن") }

            item {
                SettingsCard(onClick = { showDocDialog = true }) {
                    Row(
                        Modifier.fillMaxWidth(),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("دليل الاتصال بالـ API", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "حمّل أمثلة الكود بـ 8 لغات برمجة",
                                style = MaterialTheme.typography.bodySmall
                            )
                            // مسار التخزين
                            Text(
                                "📁 مسار الحفظ: Downloads/SMS-Gateway/",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Icon(Icons.Default.Download, "تحميل")
                    }
                }
            }

            // رسالة التحميل
            downloadMessage?.let { msg ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (msg.startsWith("✅"))
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            Arrangement.spacedBy(8.dp),
                            Alignment.CenterVertically
                        ) {
                            Icon(
                                if (msg.startsWith("✅")) Icons.Default.Check else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (msg.startsWith("✅"))
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                            Text(msg, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { downloadMessage = null }) {
                                Icon(Icons.Default.Close, "إغلاق")
                            }
                        }
                    }
                }
            }

            // ─── IP Whitelist ─────────────────────────────────────────────
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
                            Row {
                                IconButton(onClick = {
                                    copyToClipboardWithFeedback(context, "عنوان IP", ip)
                                }) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "نسخ IP",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { viewModel.removeIpFromWhitelist(ip) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "حذف",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
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

    // ─── Dialogs ──────────────────────────────────────────────────────────────

    if (showCardDialog && connectionCard != null) {
        ConnectionCardDialog(
            card = connectionCard!!,
            onDismiss = { showCardDialog = false },
            onCopy  = { text, label -> copyToClipboardWithFeedback(context, label, text) },
            onShare = { text, label -> shareText(context, label, text) }
        )
    }

    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("تجديد مفتاح API") },
            text  = { Text("هل أنت متأكد؟ المفتاح القديم سيتوقف عن العمل فوراً.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.regenerateApiKey()
                    showApiKeyDialog = false
                }) { Text("تجديد", color = MaterialTheme.colorScheme.error) }
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
            text  = {
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
                TextButton(onClick = {
                    viewModel.updateRelayUrl(url.trim())
                    showRelayDialog = false
                }) { Text("حفظ والاتصال") }
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
            text  = {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("الرابط") },
                    placeholder = { Text("https://example.com/webhook") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateWebhookUrl(url)
                    showWebhookDialog = false
                }) { Text("حفظ") }
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
            text  = {
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
                    if (ip.isNotBlank()) {
                        viewModel.addIpToWhitelist(ip.trim())
                        showIpDialog = false
                    }
                }) { Text("إضافة") }
            },
            dismissButton = {
                TextButton(onClick = { showIpDialog = false }) { Text("إلغاء") }
            }
        )
    }

    if (showDocDialog) {
        val scope = rememberCoroutineScope()
        DocumentationDownloadDialog(
            onDismiss = { showDocDialog = false },
            onDownload = { lang ->
                scope.launch {
                    val result = viewModel.downloadDocumentation(lang)
                    result.onSuccess { file ->
                        downloadMessage = "✅ تم الحفظ في:\n${file.absolutePath}"
                        showDocDialog = false
                    }.onFailure { error ->
                        downloadMessage = "❌ خطأ: ${error.message}"
                    }
                }
            }
        )
    }
}

// ─── Connection Card Widget ───────────────────────────────────────────────────

@Composable
fun ConnectionCardWidget(
    card: ConnectionCard?,
    relayConnected: Boolean,
    onCopy: (String, String) -> Unit,
    onShare: (String, String) -> Unit,
    onShowDetails: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (card != null && relayConnected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (card == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Warning,
                                "تحذير",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Text(
                        "أضف رابط خادم الوسيط أولاً لإنشاء رابط الاتصال",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                // ── رابط API ──
                Text("رابط API لموقعك:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                        Text(
                            card.apiUrl,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Row {
                        IconButton(onClick = { onCopy(card.apiUrl, "رابط API") }) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "نسخ رابط API",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { onShare(card.apiUrl, "رابط API") }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "مشاركة رابط API",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                // ── مفتاح API ──
                Text("مفتاح API:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Text(
                        "${card.apiKey.take(20)}…",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    Row {
                        IconButton(onClick = { onCopy(card.apiKey, "مفتاح API") }) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "نسخ مفتاح API",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { onShare(card.apiKey, "مفتاح API") }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "مشاركة مفتاح API",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                Button(
                    onClick = onShowDetails,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Code, null)
                    Spacer(Modifier.width(8.dp))
                    Text("عرض أمثلة الكود الكامل", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

// ─── Connection Card Dialog ───────────────────────────────────────────────────

@Composable
fun ConnectionCardDialog(
    card: ConnectionCard,
    onDismiss: () -> Unit,
    onCopy: (String, String) -> Unit,
    onShare: (String, String) -> Unit
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

                Text("رابط API:", style = MaterialTheme.typography.labelMedium)
                CodeBlock(
                    text = card.apiUrl,
                    onCopy  = { onCopy(card.apiUrl, "رابط API") },
                    onShare = { onShare(card.apiUrl, "رابط API") }
                )

                Text("مفتاح API:", style = MaterialTheme.typography.labelMedium)
                CodeBlock(
                    text = card.apiKey,
                    onCopy  = { onCopy(card.apiKey, "مفتاح API") },
                    onShare = { onShare(card.apiKey, "مفتاح API") }
                )

                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick  = { selectedTab = index },
                            text     = { Text(title, fontSize = 12.sp) }
                        )
                    }
                }

                when (selectedTab) {
                    0 -> {
                        val code = buildCurlExample(card.apiUrl, card.apiKey)
                        CodeBlock(
                            text = code,
                            onCopy  = { onCopy(code, "مثال cURL") },
                            onShare = { onShare(code, "مثال cURL") }
                        )
                    }
                    1 -> {
                        val code = buildJsExample(card.apiUrl, card.apiKey)
                        CodeBlock(
                            text = code,
                            onCopy  = { onCopy(code, "مثال JavaScript") },
                            onShare = { onShare(code, "مثال JavaScript") }
                        )
                    }
                    2 -> {
                        val code = buildPhpExample(card.apiUrl, card.apiKey)
                        CodeBlock(
                            text = code,
                            onCopy  = { onCopy(code, "مثال PHP") },
                            onShare = { onShare(code, "مثال PHP") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

// ─── Code Block Component ─────────────────────────────────────────────────────

@Composable
private fun CodeBlock(text: String, onCopy: () -> Unit, onShare: () -> Unit) {
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
            ),
            modifier = Modifier.padding(end = 64.dp)
        )
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.End
        ) {
            // زر النسخ
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "نسخ",
                    tint = Color(0xFFBBBBBB),
                    modifier = Modifier.size(16.dp)
                )
            }
            // زر المشاركة
            IconButton(
                onClick = onShare,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "مشاركة",
                    tint = Color(0xFF88CCFF),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ─── Documentation Download Dialog ───────────────────────────────────────────

@Composable
private fun DocumentationDownloadDialog(
    onDismiss: () -> Unit,
    onDownload: (DocLanguage) -> Unit
) {
    val storagePath = remember {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "SMS-Gateway"
        )
        dir.absolutePath
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تحميل دليل الاتصال بالـ API", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // مسار الحفظ
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Column {
                            Text(
                                "مسار حفظ الملفات:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                storagePath,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "اختر لغة البرمجة لتحميل أمثلة الكود الكاملة:",
                    style = MaterialTheme.typography.bodyMedium
                )

                DocLanguage.values().forEach { lang ->
                    Button(
                        onClick = { onDownload(lang) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text("${lang.label}  (.${lang.ext})")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

// ─── Code Examples ────────────────────────────────────────────────────────────

private fun buildCurlExample(url: String, key: String) = """
curl -X POST "$url/transactions" \
  -H "Authorization: Bearer $key" \
  -H "Content-Type: application/json" \
  -d '{"id":"order-001","amount":500,"phoneNumber":"01012345678"}'
""".trimIndent()

private fun buildJsExample(url: String, key: String) = """
const res = await fetch('$url/transactions', {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer $key',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    id: 'order-001',
    amount: 500,
    phoneNumber: '01012345678'
  })
});
const data = await res.json();
console.log(data);
""".trimIndent()

private fun buildPhpExample(url: String, key: String): String {
    val s = "$"
    return """
<?php
${s}apiUrl = "$url";
${s}apiKey = "$key";
${s}data   = ['id'=>'order-1','amount'=>500,'phoneNumber'=>'01012345678'];

${s}ctx = stream_context_create(['http' => [
  'method'  => 'POST',
  'header'  => "Authorization: Bearer " . ${s}apiKey . "\r\nContent-Type: application/json",
  'content' => json_encode(${s}data)
]]);

${s}response = json_decode(
  file_get_contents(${s}apiUrl . '/transactions', false, ${s}ctx),
  true
);
echo "رقم المعاملة: " . ${s}response['transaction']['id'];
?>
""".trimIndent()
}
