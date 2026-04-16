package com.sms.paymentgateway.presentation.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) startGatewayService()
        else Timber.w("بعض الأذونات مرفوضة")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        apiKey          = securityManager.getApiKey(),
                        onStartService  = { checkPermissionsAndStart() },
                        onBatterySettings = { openBatterySettings() }
                    )
                }
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val required = buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val denied = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (denied.isEmpty()) startGatewayService()
        else permissionLauncher.launch(denied.toTypedArray())
    }

    private fun startGatewayService() {
        val intent = Intent(this, PaymentGatewayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(intent)
        else
            startService(intent)
        Timber.i("تم تشغيل الخدمة")
    }

    private fun openBatterySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

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
                    icon = { Icon(Icons.Default.Home, "الرئيسية") },
                    label = { Text("الرئيسية") },
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, "لوحة التحكم") },
                    label = { Text("لوحة التحكم") },
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, "الإعدادات") },
                    label = { Text("الإعدادات") },
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

@Composable
fun HomeScreen(
    apiKey: String,
    onStartService: () -> Unit,
    onBatterySettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📱 بوابة دفع SMS", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "تأكيد المدفوعات عبر رسائل المحافظ الإلكترونية",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("🔑 مفتاح API:", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    apiKey,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(onClick = onStartService, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("تشغيل الخدمة")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(onClick = onBatterySettings, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Settings, null)
            Spacer(Modifier.width(8.dp))
            Text("إعدادات استهلاك البطارية")
        }

        Spacer(Modifier.height(32.dp))

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("📝 التعليمات:", style = MaterialTheme.typography.labelLarge)
                Text("١. اضغط «تشغيل الخدمة» ومنح الأذونات")
                Text("٢. اذهب إلى «الإعدادات» وأضف رابط خادم الوسيط")
                Text("٣. سيظهر رابط API جاهز للاستخدام في موقعك")
                Text("٤. أوقف تحسين البطارية للتطبيق للعمل المستمر")
            }
        }
    }
}
