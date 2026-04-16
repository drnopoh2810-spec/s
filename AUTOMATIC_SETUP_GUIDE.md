# 🚀 دليل الإعداد التلقائي الكامل

## 🎯 الهدف
جعل التطبيق يعطيك رابط نهائي تلقائياً بمجرد فتحه، بدون أي إعدادات يدوية!

---

## 📋 الخطة الكاملة

### **المرحلة 1: نشر Relay Server (مرة واحدة فقط)**

#### **الخيار 1: Replit (الأسهل)** ⭐

```
1. اذهب إلى: https://replit.com/signup
2. سجّل حساب (مجاني)
3. Create Repl → Node.js
4. اسم الـ Repl: sms-gateway-relay
5. انسخ محتوى الملفات:
   - relay-server/server.js
   - relay-server/package.json
6. اضغط Run
7. احفظ الرابط: https://sms-gateway-relay.YOUR_USERNAME.repl.co
```

#### **الخيار 2: Render**

```
1. https://render.com/register
2. New → Web Service
3. رفع الملفات من relay-server/
4. Build: npm install
5. Start: npm start
6. احفظ الرابط: https://sms-gateway-relay.onrender.com
```

---

### **المرحلة 2: تحديث التطبيق**

سأضيف الكود التالي في التطبيق:

#### **1. إضافة Default Relay URL**

```kotlin
// في RelayClient.kt

companion object {
    private const val TAG = "RelayClient"
    
    // ضع رابط Relay Server بتاعك هنا:
    private const val DEFAULT_RELAY_URL = "wss://YOUR_RELAY_URL/device"
}

fun start() {
    var url = securityManager.getRelayUrl()
    
    // إذا لم يكن هناك URL، استخدم الافتراضي
    if (url.isNullOrBlank()) {
        val deviceId = getDeviceId()
        url = "$DEFAULT_RELAY_URL/$deviceId"
        securityManager.setRelayUrl(url)
        Log.i(TAG, "✅ Using default relay: $url")
    }
    
    connectJob = scope.launch {
        connect(url)
    }
}

private fun getDeviceId(): String {
    val prefs = context.getSharedPreferences("relay_prefs", Context.MODE_PRIVATE)
    var deviceId = prefs.getString("device_id", null)
    
    if (deviceId == null) {
        // توليد Device ID فريد
        deviceId = "device_${System.currentTimeMillis()}_${(1000..9999).random()}"
        prefs.edit().putString("device_id", deviceId).apply()
    }
    
    return deviceId
}
```

#### **2. إضافة Public URL في Dashboard**

```kotlin
// في DashboardViewModel.kt

private val _publicUrl = MutableStateFlow<String?>(null)
val publicUrl: StateFlow<String?> = _publicUrl.asStateFlow()

init {
    viewModelScope.launch {
        relayClient.isConnected.collect { connected ->
            if (connected) {
                // استخراج Public URL من Relay URL
                val relayUrl = securityManager.getRelayUrl()
                if (relayUrl != null) {
                    val publicUrl = convertToPublicUrl(relayUrl)
                    _publicUrl.value = publicUrl
                }
            } else {
                _publicUrl.value = null
            }
        }
    }
}

private fun convertToPublicUrl(relayUrl: String): String {
    // wss://relay.com/device/abc123 → https://relay.com/api/abc123
    val deviceId = relayUrl.substringAfterLast("/")
    val baseUrl = relayUrl
        .replace("wss://", "https://")
        .replace("ws://", "http://")
        .substringBefore("/device")
    
    return "$baseUrl/api/$deviceId/api/v1"
}
```

#### **3. عرض الرابط في Dashboard**

```kotlin
// في DashboardScreen.kt

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val publicUrl by viewModel.publicUrl.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    
    // ...
    
    // بطاقة الرابط العام
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isConnected) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isConnected) Color(0xFF27ae60) else Color(0xFFe74c3c)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isConnected) "✅ متصل بالإنترنت" else "⚠️ غير متصل",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (publicUrl != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "🌐 الرابط العام:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Text(
                        publicUrl!!,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { 
                        copyToClipboard(context, "Public URL", publicUrl!!)
                    }) {
                        Icon(Icons.Default.ContentCopy, "نسخ")
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                Text(
                    "استخدم هذا الرابط في موقعك للاتصال بالتطبيق",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    "جاري الاتصال بالسيرفر...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

---

## 🎯 النتيجة النهائية

### **عند فتح التطبيق:**

```
┌─────────────────────────────────────────────┐
│ 🌐 الاتصال العام                           │
│                                             │
│ ✅ متصل بالإنترنت                          │
│                                             │
│ 🌐 الرابط العام:                          │
│ ┌─────────────────────────────────────┐    │
│ │ https://relay.com/api/device123/... │ 📋 │
│ └─────────────────────────────────────┘    │
│                                             │
│ استخدم هذا الرابط في موقعك                │
└─────────────────────────────────────────────┘
```

---

## 📝 الكود الكامل للتحديثات

### **ملف 1: RelayClient.kt**

أضف في بداية الملف:

```kotlin
companion object {
    private const val TAG = "RelayClient"
    
    // ⚠️ ضع رابط Relay Server بتاعك هنا:
    private const val DEFAULT_RELAY_URL = "wss://sms-gateway-relay.YOUR_USERNAME.repl.co/device"
}
```

أضف هذه الدالة:

```kotlin
/**
 * يولّد Device ID فريد للجهاز
 */
private fun getDeviceId(): String {
    val prefs = context.getSharedPreferences("relay_prefs", Context.MODE_PRIVATE)
    var deviceId = prefs.getString("device_id", null)
    
    if (deviceId == null) {
        deviceId = "device_${System.currentTimeMillis()}_${(1000..9999).random()}"
        prefs.edit().putString("device_id", deviceId).apply()
        Log.i(TAG, "Generated new device ID: $deviceId")
    }
    
    return deviceId
}
```

عدّل دالة `start()`:

```kotlin
fun start() {
    if (isConnected()) {
        Log.w(TAG, "Already connected")
        return
    }

    var url = securityManager.getRelayUrl()
    
    // إذا لم يكن هناك URL، استخدم الافتراضي
    if (url.isNullOrBlank()) {
        val deviceId = getDeviceId()
        url = "$DEFAULT_RELAY_URL/$deviceId"
        securityManager.setRelayUrl(url)
        Log.i(TAG, "✅ Using default relay URL: $url")
    }

    connectJob = scope.launch {
        connect(url)
    }
}
```

### **ملف 2: DashboardViewModel.kt**

أضف في الـ class:

```kotlin
private val _publicUrl = MutableStateFlow<String?>(null)
val publicUrl: StateFlow<String?> = _publicUrl.asStateFlow()

init {
    // ... الكود الموجود ...
    
    // مراقبة الاتصال وتحديث Public URL
    viewModelScope.launch {
        relayClient.isConnected.collect { connected ->
            if (connected) {
                val relayUrl = securityManager.getRelayUrl()
                if (relayUrl != null) {
                    _publicUrl.value = convertToPublicUrl(relayUrl)
                }
            } else {
                _publicUrl.value = null
            }
        }
    }
}

/**
 * يحوّل Relay URL إلى Public URL
 */
private fun convertToPublicUrl(relayUrl: String): String {
    // wss://relay.com/device/abc123 → https://relay.com/api/abc123/api/v1
    val deviceId = relayUrl.substringAfterLast("/")
    val baseUrl = relayUrl
        .replace("wss://", "https://")
        .replace("ws://", "http://")
        .substringBefore("/device")
    
    return "$baseUrl/api/$deviceId/api/v1"
}
```

### **ملف 3: DashboardScreen.kt**

أضف في الـ Composable:

```kotlin
val publicUrl by viewModel.publicUrl.collectAsState()
val isConnected by viewModel.relayConnected.collectAsState()

// ... في LazyColumn ...

item {
    PublicUrlCard(
        publicUrl = publicUrl,
        isConnected = isConnected,
        onCopy = { url -> copyToClipboard(context, "Public URL", url) }
    )
}
```

أضف هذا الـ Composable:

```kotlin
@Composable
private fun PublicUrlCard(
    publicUrl: String?,
    isConnected: Boolean,
    onCopy: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isConnected) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isConnected) Color(0xFF27ae60) else Color(0xFF95a5a6)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isConnected) "✅ متصل بالإنترنت" else "⚠️ جاري الاتصال...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (publicUrl != null) {
                Text(
                    "🌐 الرابط العام:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Text(
                        publicUrl,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onCopy(publicUrl) }) {
                        Icon(Icons.Default.ContentCopy, "نسخ")
                    }
                }
                
                Text(
                    "استخدم هذا الرابط في موقعك للاتصال بالتطبيق",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

---

## ✅ الخلاصة

### **ما تحتاج تعمله:**

1. **انشر Relay Server على Replit** (5 دقائق)
2. **احصل على الرابط** مثل: `wss://sms-gateway-relay.YOUR_USERNAME.repl.co`
3. **أرسل لي الرابط** وأنا هدمجه في الكود
4. **بناء التطبيق**
5. **✅ خلاص! التطبيق هيعطيك الرابط تلقائياً**

---

## 🎁 المميزات النهائية

```
✅ تلقائي 100% - بدون إعدادات يدوية
✅ مجاني 100%
✅ يعمل من أي شبكة
✅ HTTPS تلقائي
✅ رابط ثابت
✅ يظهر في Dashboard فوراً
✅ نسخ بضغطة واحدة
```

---

**هل تريد أن أكمل التطبيق بعد ما تنشر Relay Server؟** 🚀

**أو أرسل لي:**
- Cloudflare Tunnel Token (إذا تريد استخدام Cloudflare)
- أو رابط Relay Server بعد نشره على Replit

وأنا هدمجه في الكود مباشرة! 💪
