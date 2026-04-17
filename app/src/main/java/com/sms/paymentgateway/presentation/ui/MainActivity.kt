package com.sms.paymentgateway.presentation.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sms.paymentgateway.services.PaymentGatewayService
import com.sms.paymentgateway.utils.security.SecurityManager
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var securityManager: SecurityManager
    @Inject lateinit var batteryOptimizationManager: com.sms.paymentgateway.utils.BatteryOptimizationManager

    companion object {
        private const val PREFS_NAME    = "app_prefs"
        private const val PREF_FIRST_RUN = "first_run_done"
    }

    /** مُطلق طلب الأذونات المتعددة */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val denied = perms.entries.filter { !it.value }.map { it.key }
        if (denied.isEmpty()) {
            Toast.makeText(this, "✅ تم منح جميع الأذونات بنجاح", Toast.LENGTH_SHORT).show()
            startGatewayService()
        } else {
            Toast.makeText(
                this,
                "⚠️ بعض الأذونات مرفوضة – قد يتأثر عمل التطبيق",
                Toast.LENGTH_LONG
            ).show()
            Timber.w("أذونات مرفوضة: $denied")
        }
    }

    /** مُطلق إعداد صلاحية إدارة جميع الملفات (Android 11+) */
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()
        ) {
            requestRemainingPermissions()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* ──── طلب الأذونات تلقائياً عند أول تشغيل ──── */
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_FIRST_RUN, true)) {
            prefs.edit().putBoolean(PREF_FIRST_RUN, false).apply()
            checkAndRequestAllPermissions()
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        apiKey            = securityManager.getApiKey(),
                        onStartService    = { checkPermissionsAndStart() },
                        onBatterySettings = { openBatterySettings() }
                    )
                }
            }
        }
    }

    // ─────────────────── Permission helpers ──────────────────────────────────

    /** يطلب جميع الأذونات عند أول تشغيل */
    private fun checkAndRequestAllPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        ) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                try {
                    manageStorageLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    )
                } catch (ex: Exception) {
                    Timber.e(ex, "تعذّر فتح إعدادات الملفات")
                    requestRemainingPermissions()
                }
            }
        } else {
            requestRemainingPermissions()
        }
    }

    /** يطلب صلاحيات SMS والإشعارات والتخزين */
    private fun requestRemainingPermissions() {
        val denied = buildRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (denied.isNotEmpty()) permissionLauncher.launch(denied.toTypedArray())
    }

    /** يبني قائمة الأذونات المطلوبة حسب إصدار Android */
    private fun buildRequiredPermissions(): List<String> = buildList {
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** يتحقق من الأذونات ثم يشغّل الخدمة */
    private fun checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        ) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            return
        }

        val denied = buildRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (denied.isEmpty()) startGatewayService()
        else permissionLauncher.launch(denied.toTypedArray())
    }

    private fun startGatewayService() {
        // التحقق من Battery Optimization
        if (!batteryOptimizationManager.isBatteryOptimizationDisabled()) {
            Toast.makeText(
                this,
                "⚠️ يُنصح بإيقاف Battery Optimization للحصول على أفضل أداء",
                Toast.LENGTH_LONG
            ).show()
            Timber.w("Battery optimization is enabled - service may be killed")
        }
        
        val intent = Intent(this, PaymentGatewayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
        Toast.makeText(this, "🚀 تم تشغيل خدمة البوابة بنجاح", Toast.LENGTH_SHORT).show()
        Timber.i("تم تشغيل الخدمة")
    }

    private fun openBatterySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            batteryOptimizationManager.requestBatteryOptimizationExemption()
        } else {
            Toast.makeText(this, "لا حاجة لهذا الإعداد في هذا الإصدار", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * فتح إعدادات Auto-Start
     */
    private fun openAutoStartSettings() {
        batteryOptimizationManager.openAutoStartSettings()
    }
}

// ─── Navigation ───────────────────────────────────────────────────────────────

@Composable
fun AppNavigation(
    apiKey: String,
    onStartService: () -> Unit,
    onBatterySettings: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon    = { Icon(Icons.Default.Home, "الرئيسية") },
                    label   = { Text("الرئيسية") },
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon    = { Icon(Icons.Default.List, "لوحة التحكم") },
                    label   = { Text("لوحة التحكم") },
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon    = { Icon(Icons.Default.Settings, "الإعدادات") },
                    label   = { Text("الإعدادات") },
                    selected = selectedTab == 2,
                    onClick  = { selectedTab = 2 }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> HomeScreen(apiKey, onStartService, onBatterySettings)
                1 -> com.sms.paymentgateway.presentation.ui.screens.DashboardScreen()
                2 -> com.sms.paymentgateway.presentation.ui.screens.SettingsScreen()
            }
        }
    }
}

// ─── Home Screen ─────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    apiKey: String,
    onStartService: () -> Unit,
    onBatterySettings: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        /* ── Header ── */
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "📱 بوابة دفع SMS",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "تأكيد المدفوعات عبر رسائل المحافظ الإلكترونية",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        /* ── API Key Card ── */
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "🔑 مفتاح API",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row {
                            /* زر النسخ */
                            IconButton(onClick = {
                                copyToClipboardWithFeedback(context, "مفتاح API", apiKey)
                            }) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "نسخ مفتاح API",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            /* زر المشاركة */
                            IconButton(onClick = {
                                shareText(context, "مفتاح API", apiKey)
                            }) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "مشاركة مفتاح API",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Text(
                            apiKey,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        /* ── Action Buttons ── */
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStartService,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(12.dp))
                    Text("تشغيل الخدمة", style = MaterialTheme.typography.titleMedium)
                }

                OutlinedButton(
                    onClick = onBatterySettings,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.BatteryFull, null)
                    Spacer(Modifier.width(12.dp))
                    Text("إعدادات البطارية", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        /* ── Instructions ── */
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "📝 خطوات التشغيل",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    InstructionItem("١", "منح جميع الأذونات المطلوبة (تظهر تلقائياً أول مرة)")
                    InstructionItem("٢", "اضغط «تشغيل الخدمة» لتفعيل الاستماع للرسائل")
                    InstructionItem("٣", "اذهب إلى «الإعدادات» وأضف رابط خادم الوسيط")
                    InstructionItem("٤", "أوقف تحسين البطارية للتطبيق للعمل المستمر")
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun InstructionItem(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    number,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

// ─── Shared Utility Functions (مُتاحة لجميع Screens) ─────────────────────────

/** ينسخ النص للحافظة ويعرض Toast بالتأكيد */
fun copyToClipboardWithFeedback(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
    Toast.makeText(context, "✅ تم النسخ: $label", Toast.LENGTH_SHORT).show()
}

/** يفتح قائمة المشاركة الأصلية في Android */
fun shareText(context: Context, label: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, label)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "مشاركة $label عبر…"))
}
