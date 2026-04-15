# دليل استكشاف الأخطاء - SMS Payment Gateway

## 🔍 المشاكل الشائعة وحلولها

---

### 1. التطبيق لا يستقبل SMS

#### الأعراض:
- الرسائل تصل للهاتف لكن التطبيق لا يعالجها
- لا توجد logs في Logcat

#### الحلول:

**أ. تحقق من الأذونات:**
```bash
# من ADB
adb shell dumpsys package com.sms.paymentgateway | grep permission

# يجب أن ترى:
# android.permission.RECEIVE_SMS: granted=true
# android.permission.READ_SMS: granted=true
```

**ب. تحقق من تشغيل الخدمة:**
```bash
adb shell dumpsys activity services | grep PaymentGatewayService

# يجب أن ترى الخدمة تعمل
```

**ج. أعد تشغيل الخدمة:**
1. افتح التطبيق
2. اضغط "Start Service" مرة أخرى
3. تحقق من ظهور الإشعار

**د. تحقق من BroadcastReceiver:**
```bash
adb shell dumpsys package com.sms.paymentgateway | grep SmsReceiver

# يجب أن يكون enabled=true
```

**هـ. اختبار يدوي:**
```bash
# أرسل SMS تجريبي
adb emu sms send Vodafone "تم استلام 500 جنيه من 01012345678 رقم VC123456"

# راقب Logs
adb logcat | grep "PaymentGateway\|SmsReceiver"
```

---

### 2. API Server لا يستجيب

#### الأعراض:
- `curl` يعطي "Connection refused"
- لا يمكن الوصول للـ API

#### الحلول:

**أ. تحقق من تشغيل الخدمة:**
```bash
adb logcat | grep "API Server"

# يجب أن ترى:
# API Server started on port 8080
```

**ب. تحقق من عنوان IP:**
```bash
# احصل على IP الهاتف
adb shell ip addr show wlan0 | grep inet

# استخدم هذا IP في الطلبات
curl http://<phone-ip>:8080/api/v1/health
```

**ج. تحقق من الشبكة:**
- تأكد أن الهاتف والكمبيوتر على نفس الشبكة
- جرب ping للهاتف:
```bash
ping <phone-ip>
```

**د. تحقق من Firewall:**
- بعض الهواتف لديها firewall مدمج
- تحقق من إعدادات الأمان

**هـ. أعد تشغيل الخدمة:**
```bash
# أوقف الخدمة
adb shell am force-stop com.sms.paymentgateway

# ابدأها مرة أخرى من التطبيق
```

---

### 3. SMS لا يتطابق مع العمليات

#### الأعراض:
- SMS يُستقبل ويُحلل بنجاح
- لكن لا يتطابق مع أي عملية منتظرة

#### الحلول:

**أ. تحقق من البيانات:**
```bash
# عرض SMS المستلم
adb logcat | grep "Parsed SMS"

# عرض العمليات المنتظرة
curl http://localhost:8080/api/v1/transactions \
  -H "Authorization: Bearer YOUR_API_KEY"
```

**ب. تحقق من المبلغ:**
- يجب أن يكون المبلغ متطابق تمامًا
- الفرق المسموح: أقل من 0.01 جنيه

**ج. تحقق من رقم الهاتف:**
- يجب أن يكون الرقم متطابق
- أو على الأقل آخر 10 أرقام

**د. تحقق من فارق التوقيت:**
- الفارق المسموح: ±5 دقائق
- إذا كانت العملية قديمة، لن تتطابق

**هـ. تحقق من Confidence Score:**
```bash
adb logcat | grep "confidence"

# يجب أن يكون أعلى من 0.7 للمطابقة
```

**و. راجع Matching Logic:**
- افتح `TransactionMatcher.kt`
- تحقق من شروط المطابقة
- قد تحتاج تعديل `matchesTimeWindow`

---

### 4. Regex لا يستخرج البيانات

#### الأعراض:
- SMS يُستقبل لكن parsed = false
- أو البيانات المستخرجة خاطئة

#### الحلول:

**أ. اطبع الرسالة الخام:**
```kotlin
// في SmsParser.kt
Timber.d("Raw SMS: $message")
```

**ب. اختبر Regex على regex101.com:**
1. انسخ الرسالة الخام
2. افتح https://regex101.com
3. اختر Kotlin flavor
4. اختبر patterns واحدة تلو الأخرى

**ج. أضف patterns بديلة:**
```kotlin
// مثال: إضافة pattern بديل للمبلغ
val amountPattern1 = """(\d+(?:\.\d{1,2})?)\s*جنيه""".toRegex()
val amountPattern2 = """مبلغ\s*:?\s*(\d+(?:\.\d{1,2})?)""".toRegex()

val amount = amountPattern1.find(message)?.groupValues?.get(1)?.toDoubleOrNull()
    ?: amountPattern2.find(message)?.groupValues?.get(1)?.toDoubleOrNull()
```

**د. راجع WALLET_SMS_PATTERNS.md:**
- قارن الرسالة مع النماذج الموثقة
- قد تحتاج تحديث patterns

**هـ. أضف logging مفصل:**
```kotlin
Timber.d("Amount match: ${amountPattern.find(message)?.value}")
Timber.d("TxId match: ${txIdPattern.find(message)?.value}")
Timber.d("Phone match: ${phonePattern.find(message)?.value}")
```

---

### 5. التطبيق يتوقف بعد فترة

#### الأعراض:
- التطبيق يعمل لساعات ثم يتوقف
- الخدمة لا تعمل في الخلفية

#### الحلول:

**أ. أضف للاستثناءات من Battery Optimization:**

**Xiaomi (MIUI):**
```
Settings > Apps > Manage apps > SMS Payment Gateway
- Autostart: ON
- Battery saver: No restrictions
- Display pop-up windows: ON
```

**Samsung (One UI):**
```
Settings > Apps > SMS Payment Gateway
- Battery > Optimize battery usage: OFF
- Sleeping apps: Remove from list
- Deep sleeping apps: Remove from list
```

**Huawei (EMUI):**
```
Settings > Battery > App launch
- SMS Payment Gateway: Manage manually
  - Auto-launch: ON
  - Secondary launch: ON
  - Run in background: ON
```

**ب. تحقق من Doze Mode:**
```bash
# تعطيل Doze للاختبار
adb shell dumpsys deviceidle whitelist +com.sms.paymentgateway
```

**ج. تحقق من Data Saver:**
- Settings > Network & internet > Data Saver
- أضف التطبيق للاستثناءات

**د. استخدم Foreground Service:**
- التطبيق يستخدم Foreground Service بالفعل
- تأكد من ظهور الإشعار دائمًا

---

### 6. استهلاك البطارية مرتفع

#### الأعراض:
- البطارية تنفد بسرعة
- التطبيق يظهر في قائمة استهلاك البطارية

#### الحلول:

**أ. راقب استهلاك البطارية:**
```bash
adb shell dumpsys batterystats | grep com.sms.paymentgateway
```

**ب. قلل تكرار Cleanup:**
```kotlin
// في PaymentGatewayService.kt
cleanupManager.startPeriodicCleanup(intervalHours = 6) // بدلاً من 1
```

**ج. قلل Logging:**
```kotlin
// في PaymentGatewayApp.kt
if (BuildConfig.DEBUG) {
    Timber.plant(Timber.DebugTree())
}
// لا تستخدم Timber في Production
```

**د. استخدم WorkManager للمهام الدورية:**
- بدلاً من Coroutines دائمة
- WorkManager أكثر كفاءة

---

### 7. قاعدة البيانات كبيرة جدًا

#### الأعراض:
- حجم التطبيق يزداد باستمرار
- بطء في الأداء

#### الحلول:

**أ. تحقق من حجم قاعدة البيانات:**
```bash
adb shell ls -lh /data/data/com.sms.paymentgateway/databases/
```

**ب. قلل مدة الاحتفاظ بالبيانات:**
```kotlin
// في CleanupManager.kt
// حذف SMS أقدم من 3 أيام بدلاً من 7
val smsCutoff = System.currentTimeMillis() - (3 * 24 * 3600000)
```

**ج. نفذ Cleanup يدوي:**
```bash
# من API
curl -X POST http://localhost:8080/api/v1/cleanup \
  -H "Authorization: Bearer YOUR_API_KEY"
```

**د. استخدم VACUUM:**
```kotlin
// في AppDatabase.kt
@Query("VACUUM")
suspend fun vacuum()
```

---

### 8. Webhook لا يصل

#### الأعراض:
- SMS يتطابق بنجاح
- لكن الموقع لا يستقبل إشعار

#### الحلول:

**أ. تحقق من Webhook URL:**
```bash
# من Logcat
adb logcat | grep "Webhook"

# يجب أن ترى:
# Webhook sent successfully
```

**ب. تحقق من الاتصال:**
```bash
# من الهاتف
adb shell ping -c 3 your-website.com
```

**ج. تحقق من HMAC Signature:**
```python
# في موقع الويب
import hmac
import hashlib

def verify_signature(payload, signature, secret):
    computed = hmac.new(
        secret.encode(),
        payload.encode(),
        hashlib.sha256
    ).hexdigest()
    return hmac.compare_digest(computed, signature)
```

**د. استخدم webhook.site للاختبار:**
1. افتح https://webhook.site
2. انسخ الـ URL
3. اضبطه في Settings
4. أرسل SMS تجريبي
5. تحقق من استلام Webhook

**هـ. تحقق من Retry Mechanism:**
```bash
adb logcat | grep "Webhook failed"

# إذا فشل، يجب أن يعيد المحاولة
```

---

### 9. أخطاء في Build

#### الأعراض:
- Gradle sync يفشل
- Build errors

#### الحلول:

**أ. Clean & Rebuild:**
```bash
./gradlew clean
./gradlew build
```

**ب. تحديث Gradle:**
```bash
./gradlew wrapper --gradle-version 8.2
```

**ج. Invalidate Caches:**
```
File > Invalidate Caches / Restart
```

**د. تحقق من JDK:**
```bash
java -version
# يجب أن يكون JDK 17
```

**هـ. تحقق من Dependencies:**
```bash
./gradlew dependencies
```

---

### 10. مشاكل الأذونات في Android 13+

#### الأعراض:
- الأذونات لا تُمنح
- التطبيق يتوقف عند طلب الأذونات

#### الحلول:

**أ. أضف إذن POST_NOTIFICATIONS:**
```xml
<!-- في AndroidManifest.xml -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

**ب. اطلب الأذونات بالترتيب:**
```kotlin
// أولاً: SMS permissions
// ثانيًا: Notification permission
// ثالثًا: Battery optimization
```

**ج. استخدم shouldShowRequestPermissionRationale:**
```kotlin
if (shouldShowRequestPermissionRationale(Manifest.permission.RECEIVE_SMS)) {
    // اشرح للمستخدم لماذا تحتاج الإذن
}
```

---

## 🛠️ أدوات التشخيص

### 1. Logcat Filters المفيدة

```bash
# جميع logs التطبيق
adb logcat | grep "com.sms.paymentgateway"

# SMS فقط
adb logcat | grep "SmsReceiver\|SmsParser"

# API فقط
adb logcat | grep "ApiServer"

# Matching فقط
adb logcat | grep "TransactionMatcher"

# Errors فقط
adb logcat *:E | grep "com.sms.paymentgateway"
```

### 2. Database Inspection

```bash
# الدخول للـ shell
adb shell

# فتح قاعدة البيانات
sqlite3 /data/data/com.sms.paymentgateway/databases/payment_gateway_db

# عرض الجداول
.tables

# عرض العمليات
SELECT * FROM pending_transactions;

# عرض SMS
SELECT * FROM sms_logs ORDER BY receivedAt DESC LIMIT 10;

# الخروج
.quit
```

### 3. Network Debugging

```bash
# مراقبة Network traffic
adb shell tcpdump -i any -s 0 -w /sdcard/capture.pcap

# تحميل الملف
adb pull /sdcard/capture.pcap

# فتحه في Wireshark
```

---

## 📞 الحصول على المساعدة

إذا لم تحل المشكلة:

1. **جمع المعلومات:**
   ```bash
   # Logs
   adb logcat > logs.txt
   
   # Device info
   adb shell getprop > device_info.txt
   
   # App info
   adb shell dumpsys package com.sms.paymentgateway > app_info.txt
   ```

2. **راجع التوثيق:**
   - README.md
   - SETUP_GUIDE.md
   - API_DOCUMENTATION.md

3. **تواصل مع الدعم:**
   - أرفق الـ logs
   - اشرح المشكلة بالتفصيل
   - اذكر خطوات إعادة إنتاج المشكلة

---

**آخر تحديث:** 2026-04-15  
**الإصدار:** 1.0
