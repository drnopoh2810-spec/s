# دليل التثبيت والتشغيل - SMS Payment Gateway

## المتطلبات الأساسية

### 1. بيئة التطوير
- Android Studio Hedgehog (2023.1.1) أو أحدث
- JDK 17
- Android SDK API Level 26+
- Gradle 8.0+

### 2. الهاتف المستهدف
- Android 8.0 (Oreo) أو أحدث
- شريحة SIM نشطة
- اتصال إنترنت مستمر
- مساحة تخزين كافية (50 MB على الأقل)

---

## خطوات التثبيت

### 1. إعداد المشروع

```bash
# Clone المشروع
git clone <repository-url>
cd sms-payment-gateway

# افتح المشروع في Android Studio
# File > Open > اختر مجلد المشروع
```

### 2. Sync Dependencies

في Android Studio:
1. انتظر حتى يتم تحميل Gradle
2. اضغط على "Sync Now" إذا ظهرت رسالة
3. تأكد من تحميل جميع المكتبات بنجاح

### 3. بناء التطبيق

```bash
# من Terminal في Android Studio
./gradlew assembleDebug

# أو من القائمة
Build > Build Bundle(s) / APK(s) > Build APK(s)
```

### 4. تثبيت التطبيق

**الطريقة الأولى: عبر USB**
```bash
# وصّل الهاتف بالكمبيوتر
# فعّل USB Debugging في إعدادات المطور
./gradlew installDebug

# أو من Android Studio
Run > Run 'app'
```

**الطريقة الثانية: APK مباشر**
1. انسخ ملف APK من `app/build/outputs/apk/debug/app-debug.apk`
2. انقله للهاتف
3. ثبّته يدويًا (قد تحتاج تفعيل "مصادر غير معروفة")

---

## الإعداد الأولي

### 1. منح الأذونات

عند فتح التطبيق لأول مرة:

1. **أذونات SMS**
   - اضغط "Start Service"
   - اقبل أذونات READ_SMS و RECEIVE_SMS

2. **أذونات الإشعارات** (Android 13+)
   - اقبل إذن POST_NOTIFICATIONS

3. **إذن Boot**
   - يُمنح تلقائيًا

### 2. إيقاف Battery Optimization

**مهم جدًا لضمان عمل التطبيق 24/7**

1. اضغط "Battery Optimization Settings"
2. ابحث عن "SMS Payment Gateway"
3. اختر "Don't optimize" أو "غير محسّن"

**أو يدويًا:**
- Settings > Battery > Battery Optimization
- All apps > SMS Payment Gateway > Don't optimize

### 3. إعدادات خاصة بالشركات المصنعة

#### Xiaomi (MIUI)
```
Settings > Apps > Manage apps > SMS Payment Gateway
- Autostart: ON
- Battery saver: No restrictions
- Display pop-up windows while running in background: ON
```

#### Samsung (One UI)
```
Settings > Apps > SMS Payment Gateway
- Battery > Optimize battery usage: OFF
- Sleeping apps: Remove from list
```

#### Huawei (EMUI)
```
Settings > Battery > App launch
- SMS Payment Gateway: Manage manually
  - Auto-launch: ON
  - Secondary launch: ON
  - Run in background: ON
```

---

## التشغيل

### 1. بدء الخدمة

1. افتح التطبيق
2. اضغط "Start Service"
3. ستظهر إشعار دائم: "Service is running and monitoring SMS"

### 2. الحصول على API Key

- API Key يظهر في الشاشة الرئيسية
- انسخه واستخدمه في طلبات API

### 3. اختبار الاتصال

```bash
# من جهاز على نفس الشبكة
curl http://<phone-ip>:8080/api/v1/health \
  -H "Authorization: Bearer YOUR_API_KEY"

# يجب أن ترى:
# {"status":"ok","timestamp":...}
```

---

## الاختبار

### 1. اختبار استقبال SMS

**من ADB:**
```bash
# إرسال SMS تجريبي
adb emu sms send Vodafone "تم استلام 500 جنيه من 01012345678 رقم العملية VC123456789"
```

**من هاتف آخر:**
- أرسل رسالة نصية عادية للهاتف المثبت عليه التطبيق
- تحقق من Logs في Logcat

### 2. اختبار API

**إنشاء عملية منتظرة:**
```bash
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "test-001",
    "amount": 500.00,
    "phoneNumber": "01012345678"
  }'
```

**التحقق من الحالة:**
```bash
curl http://localhost:8080/api/v1/transactions/test-001 \
  -H "Authorization: Bearer YOUR_API_KEY"
```

### 3. اختبار Webhook

1. استخدم webhook.site أو ngrok للحصول على URL
2. في كود التطبيق، اضبط webhook URL
3. أرسل SMS تجريبي
4. تحقق من استلام Webhook

---

## المراقبة والصيانة

### 1. عرض Logs

**من Android Studio:**
```
View > Tool Windows > Logcat
Filter: package:com.sms.paymentgateway
```

**من ADB:**
```bash
adb logcat | grep "PaymentGateway"
```

### 2. فحص قاعدة البيانات

```bash
# الدخول لـ Device File Explorer
adb shell
cd /data/data/com.sms.paymentgateway/databases/
sqlite3 payment_gateway_db

# عرض العمليات
SELECT * FROM pending_transactions;

# عرض SMS logs
SELECT * FROM sms_logs;
```

### 3. مراقبة الأداء

- تحقق من استهلاك البطارية يوميًا
- راقب استهلاك الذاكرة
- تحقق من حجم قاعدة البيانات

---

## استكشاف الأخطاء

### المشكلة: التطبيق لا يستقبل SMS

**الحلول:**
1. تحقق من منح أذونات SMS
2. تأكد من أن الخدمة تعمل (إشعار ظاهر)
3. أعد تشغيل الخدمة
4. أعد تشغيل الهاتف

### المشكلة: API Server لا يستجيب

**الحلول:**
1. تحقق من أن الخدمة تعمل
2. تحقق من عنوان IP الصحيح
3. تأكد من أن الهاتف والجهاز على نفس الشبكة
4. تحقق من Firewall

### المشكلة: التطبيق يتوقف بعد فترة

**الحلول:**
1. أضف التطبيق لقائمة استثناءات البطارية
2. فعّل Autostart (Xiaomi, Huawei)
3. أزل التطبيق من "Sleeping apps" (Samsung)
4. تحقق من إعدادات Data Saver

### المشكلة: SMS لا يتطابق مع العمليات

**الحلول:**
1. تحقق من صيغة الرسالة
2. تأكد من دقة المبلغ ورقم الهاتف
3. تحقق من فارق التوقيت (±5 دقائق)
4. راجع Regex patterns في SmsParser

---

## التحديثات

### تحديث Regex Patterns

إذا تغيرت صيغة رسائل المحافظ:

1. افتح `SmsParser.kt`
2. عدّل الـ Regex patterns
3. أعد بناء التطبيق
4. ثبّت النسخة الجديدة

### تحديث التطبيق

```bash
# بناء نسخة جديدة
./gradlew assembleRelease

# تثبيت التحديث
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## الأمان

### 1. تأمين API Key

- لا تشارك API Key مع أحد
- غيّر API Key كل 3 أشهر
- استخدم HTTPS إذا كان الوصول من خارج الشبكة المحلية

### 2. تأمين الهاتف

- استخدم قفل شاشة قوي
- فعّل تشفير الجهاز
- لا تثبت تطبيقات من مصادر غير موثوقة

### 3. تأمين الشبكة

- استخدم VPN أو Tunnel (ngrok, Cloudflare)
- فعّل IP Whitelist
- راقب Logs للكشف عن محاولات اختراق

---

## الدعم الفني

للمساعدة أو الإبلاغ عن مشاكل:
- راجع API_DOCUMENTATION.md
- راجع README.md
- تواصل مع فريق التطوير
