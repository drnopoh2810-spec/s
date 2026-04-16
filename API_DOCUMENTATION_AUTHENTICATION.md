# 🔐 دوكيومنتيشن المصادقة (Authentication) في التطبيق

## ✅ نعم، المصادقة موجودة في الدوكيومنتيشن!

---

## 📚 ما يحتويه الدوكيومنتيشن

### **1. معلومات المصادقة الأساسية**

كل ملف دوكيومنتيشن يحتوي على:

```
Base URL : http://PHONE_IP:8080/api/v1
API Key  : YOUR_ACTUAL_API_KEY_HERE
```

---

### **2. طريقة المصادقة في كل طلب**

#### **في cURL:**
```bash
curl -X GET "$url/health" \
  -H "Authorization: Bearer $key"
```

#### **في JavaScript:**
```javascript
const headers = {
  "Authorization": `Bearer ${API_KEY}`,
  "Content-Type": "application/json"
};
```

#### **في Python:**
```python
HEADERS = {
    "Authorization": f"Bearer {API_KEY}", 
    "Content-Type": "application/json"
}
```

#### **في PHP:**
```php
CURLOPT_HTTPHEADER => [
    'Authorization: Bearer ' . API_KEY,
    'Content-Type: application/json'
]
```

#### **في Kotlin:**
```kotlin
.header("Authorization", "Bearer ${API_KEY}")
```

#### **في Java:**
```java
.header("Authorization", "Bearer " + API_KEY)
```

#### **في Dart:**
```dart
'Authorization': 'Bearer ${apiKey}'
```

#### **في C#:**
```csharp
client.DefaultRequestHeaders.Add("Authorization", $"Bearer {ApiKey}");
```

---

## 🎯 كيف يعمل في التطبيق؟

### **عند تحميل الدوكيومنتيشن:**

1. **المستخدم يفتح التطبيق**
2. **يذهب إلى الإعدادات**
3. **يضغط "تحميل الدوكيومنتيشن"**
4. **يختار اللغة (مثلاً: JavaScript)**

### **ما يحدث:**

```kotlin
// في ApiDocumentationGenerator.kt
fun generate(lang: DocLanguage): String {
    // 1. يحصل على الـ URL الفعلي من الجهاز
    val url = securityManager.buildDirectApiUrl() 
              ?: "http://PHONE_IP:8080/api/v1"
    
    // 2. يحصل على الـ API Key الفعلي
    val key = securityManager.getApiKey()
    
    // 3. يولد الدوكيومنتيشن مع البيانات الحقيقية
    return when (lang) {
        DocLanguage.JAVASCRIPT -> buildJs(url, key)
        // ... باقي اللغات
    }
}
```

### **النتيجة:**

الملف المُحمّل يحتوي على:
- ✅ الـ URL الفعلي للجهاز (مثلاً: `http://192.168.1.100:8080/api/v1`)
- ✅ الـ API Key الفعلي (مثلاً: `abc123xyz789...`)
- ✅ جميع الأمثلة جاهزة للاستخدام مباشرة!

---

## 📋 مثال كامل من الملف المُحمّل

### **JavaScript (sms_gateway_api.js):**

```javascript
// ============================================================
//  SMS Payment Gateway — API Documentation (JavaScript)
// ============================================================
const BASE_URL = "http://192.168.1.100:8080/api/v1";  // ✅ URL حقيقي
const API_KEY  = "sk_abc123xyz789def456ghi789jkl012";  // ✅ API Key حقيقي

const headers = {
  "Authorization": `Bearer ${API_KEY}`,  // ✅ المصادقة موجودة
  "Content-Type": "application/json"
};

// 1) Health Check
async function healthCheck() {
  const res = await fetch(`${BASE_URL}/health`, { headers });  // ✅ يستخدم المصادقة
  return res.json();
}

// 2) Create Transaction
async function createTransaction(id, amount, phone, walletType = "VODAFONE_CASH") {
  const res = await fetch(`${BASE_URL}/transactions`, {
    method: "POST", 
    headers,  // ✅ يستخدم المصادقة
    body: JSON.stringify({ 
      id, amount, phoneNumber: phone, 
      walletType, expiresInMinutes: 30 
    })
  });
  return res.json();
}

// 3) Get Transaction Status
async function getTransaction(id) {
  const res = await fetch(`${BASE_URL}/transactions/${id}`, { 
    headers  // ✅ يستخدم المصادقة
  });
  return res.json();
}

// ─── Usage Example ───────────────────────────────────────────
(async () => {
  // جاهز للاستخدام مباشرة! 🚀
  await createTransaction("order-001", 500, "01012345678");
  const tx = await getTransaction("order-001");
  console.log(tx);
})();
```

---

## 🔒 أمان المصادقة

### **في التطبيق:**

1. **API Key يُولّد تلقائياً:**
   ```kotlin
   // في SecurityManager.kt
   private fun generateApiKey(): String {
       val random = SecureRandom()
       val bytes = ByteArray(32)
       random.nextBytes(bytes)
       return "sk_" + bytes.joinToString("") { 
           "%02x".format(it) 
       }
   }
   ```

2. **يُحفظ بشكل آمن:**
   ```kotlin
   private val prefs = context.getSharedPreferences(
       "payment_gateway_secure", 
       Context.MODE_PRIVATE
   )
   ```

3. **يُستخدم في كل طلب:**
   ```kotlin
   // في ApiServer.kt
   private fun authenticate(call: ApplicationCall): Boolean {
       val authHeader = call.request.headers["Authorization"]
       val token = authHeader?.removePrefix("Bearer ")?.trim()
       return securityManager.validateApiKey(token ?: "")
   }
   ```

---

## 📊 جميع Endpoints مع المصادقة

### **في كل ملف دوكيومنتيشن:**

| Endpoint | Method | المصادقة |
|----------|--------|----------|
| `/health` | GET | ✅ Bearer Token |
| `/transactions` | POST | ✅ Bearer Token |
| `/transactions/{id}` | GET | ✅ Bearer Token |
| `/transactions` | GET | ✅ Bearer Token |
| `/sms/logs` | GET | ✅ Bearer Token |
| `/connection-info` | GET | ✅ Bearer Token |

---

## 🎨 مثال من كل لغة

### **cURL:**
```bash
curl -X GET "http://192.168.1.100:8080/api/v1/health" \
  -H "Authorization: Bearer sk_abc123..."
```

### **Python:**
```python
HEADERS = {"Authorization": f"Bearer {API_KEY}"}
response = requests.get(f"{BASE_URL}/health", headers=HEADERS)
```

### **PHP:**
```php
$headers = ['Authorization: Bearer ' . API_KEY];
curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);
```

### **Kotlin:**
```kotlin
Request.Builder()
    .header("Authorization", "Bearer ${API_KEY}")
    .build()
```

---

## ✅ التحقق من المصادقة

### **في التطبيق:**

```kotlin
// في ApiServer.kt
install(Authentication) {
    bearer("auth-bearer") {
        authenticate { credential ->
            if (securityManager.validateApiKey(credential.token)) {
                UserIdPrincipal("api-user")
            } else {
                null  // ❌ رفض الطلب
            }
        }
    }
}

// استخدام المصادقة
authenticate("auth-bearer") {
    get("/transactions") {
        // ✅ هنا فقط إذا المصادقة نجحت
    }
}
```

---

## 🚀 كيف يستخدمها المطور؟

### **الخطوات:**

1. **تحميل الدوكيومنتيشن من التطبيق**
   - الإعدادات → تحميل الدوكيومنتيشن → JavaScript

2. **فتح الملف المُحمّل**
   - `Downloads/sms_gateway_api.js`

3. **نسخ الكود مباشرة**
   - الـ URL و API Key موجودين
   - المصادقة مُضمّنة في كل طلب
   - جاهز للاستخدام فوراً!

4. **الاستخدام في الموقع:**
   ```javascript
   // نسخ ولصق من الملف المُحمّل
   const BASE_URL = "http://192.168.1.100:8080/api/v1";
   const API_KEY  = "sk_abc123...";
   
   // استخدام مباشر
   await createTransaction("order-001", 500, "01012345678");
   ```

---

## 📝 ملاحظات مهمة

### **1. API Key فريد لكل جهاز:**
- كل تطبيق يولد API Key خاص به
- لا يمكن استخدام نفس الـ Key على أجهزة مختلفة

### **2. الأمان:**
- ✅ Bearer Token Authentication
- ✅ HTTPS مدعوم (عند استخدام Relay)
- ✅ Rate Limiting مُفعّل
- ✅ IP Whitelist (اختياري)

### **3. التجديد:**
- يمكن تجديد API Key من الإعدادات
- الدوكيومنتيشن الجديد سيحتوي على الـ Key الجديد

---

## 🎉 الخلاصة

**نعم، المصادقة موجودة بالكامل في الدوكيومنتيشن!**

- ✅ كل ملف يحتوي على API Key الفعلي
- ✅ كل طلب يستخدم `Authorization: Bearer TOKEN`
- ✅ جاهز للاستخدام مباشرة بدون تعديل
- ✅ يعمل في جميع اللغات الـ 8

---

## 📚 الملفات ذات الصلة

- `ApiDocumentationGenerator.kt` - يولد الدوكيومنتيشن
- `SecurityManager.kt` - يدير API Keys
- `ApiServer.kt` - يتحقق من المصادقة
- `SettingsViewModel.kt` - يوفر وظيفة التحميل

---

**جرّب الآن:**
1. افتح التطبيق
2. الإعدادات → تحميل الدوكيومنتيشن
3. اختر أي لغة
4. افتح الملف المُحمّل
5. ستجد كل شيء جاهز! 🚀
