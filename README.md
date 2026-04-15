# SMS Payment Gateway - Android Middleware

تطبيق Android يعمل كوسيط لتأكيد الدفع عبر قراءة رسائل SMS من المحافظ الإلكترونية المصرية.

## المحافظ المدعومة

- Vodafone Cash
- Orange Money
- Etisalat Cash
- Fawry
- InstaPay

## المميزات

✅ قراءة وتحليل رسائل SMS تلقائيًا  
✅ مطابقة العمليات مع طلبات الدفع المنتظرة  
✅ API Server محلي على المنفذ 8080  
✅ Webhook لإشعار الموقع فورًا  
✅ تشفير وأمان متقدم (HMAC)  
✅ يعمل 24/7 في الخلفية  
✅ قاعدة بيانات محلية لتخزين السجلات  
✅ **اتصال مستقر مع الخادم (لا ينقطع)**  
✅ **إعادة اتصال تلقائي ذكي**  
✅ **مراقبة مستمرة لحالة الاتصال**  
✅ **مقاوم لإعدادات توفير البطارية**  

## متطلبات التشغيل

- Android 8.0+ (API Level 26+)
- أذونات SMS (READ_SMS, RECEIVE_SMS)
- **إيقاف Battery Optimization (مهم جداً)**
- **تفعيل Auto-Start للتطبيق**
- اتصال إنترنت مستمر
- **عدم إغلاق التطبيق من Recent Apps**

> ⚠️ **مهم**: لضمان عدم انقطاع الاتصال، راجع [دليل استقرار الاتصال](CONNECTION_STABILITY_GUIDE.md)

## API Endpoints

### POST /api/v1/transactions
إضافة عملية دفع جديدة للانتظار

```json
{
  "id": "unique-tx-id",
  "amount": 500.00,
  "phoneNumber": "01012345678",
  "expectedTxId": "VC123456789",
  "walletType": "VODAFONE_CASH",
  "expiresInMinutes": 30
}
```

### GET /api/v1/transactions/{id}
الاستعلام عن حالة عملية

### GET /api/v1/transactions
قائمة جميع العمليات المنتظرة

### GET /api/v1/sms/logs
سجل جميع الرسائل المستلمة

### GET /api/v1/health
فحص حالة الخدمة

## Authentication

جميع الطلبات تحتاج إلى API Key في الـ Header:

```
Authorization: Bearer YOUR_API_KEY
```

يمكن الحصول على API Key من واجهة التطبيق.

## Webhook Callback

عند تأكيد الدفع، يتم إرسال POST request إلى webhook URL المحدد:

```json
{
  "event": "PAYMENT_CONFIRMED",
  "transactionId": "your-tx-id",
  "smsData": {
    "walletType": "VODAFONE_CASH",
    "walletTxId": "VC123456789",
    "amount": 500.00,
    "senderPhone": "01012345678",
    "timestamp": 1705329000000
  },
  "confidence": 0.98,
  "processedAt": 1705329005000
}
```

## التثبيت والتشغيل

### للمطورين (Development)

1. استنسخ المشروع:
   ```bash
   git clone https://github.com/drnopoh2810-spec/s.git
   cd s
   ```

2. أنشئ ملف `local.properties` في جذر المشروع:
   ```properties
   RELAY_API_KEY=your_api_key_here
   ```
   أو انسخ من الملف المثال:
   ```bash
   cp local.properties.example local.properties
   ```

3. افتح المشروع في Android Studio
4. قم بتثبيت Dependencies
5. قم ببناء التطبيق
6. ثبّت التطبيق على هاتف Android
7. امنح جميع الأذونات المطلوبة
8. اضغط "Start Service"
9. أضف التطبيق لقائمة استثناءات البطارية

### تحميل APK الجاهز

يمكنك تحميل APK مباشرة من:
- **Releases**: [أحدث إصدار](https://github.com/drnopoh2810-spec/s/releases/latest)
- **Actions Artifacts**: من أي build ناجح في [Actions](https://github.com/drnopoh2810-spec/s/actions)

### CI/CD
المشروع يستخدم GitHub Actions لبناء APK تلقائياً. راجع [CI_CD_SETUP.md](CI_CD_SETUP.md) للتفاصيل.

## 🔗 استقرار الاتصال

### المشكلة الشائعة: انقطاع الاتصال
إذا كان الاتصال مع `wss://nopoh22-sms-relay-server.hf.space/device` ينقطع ويحتاج إعادة فتح التطبيق:

### ✅ الحل النهائي:
1. **راجع [دليل استقرار الاتصال](CONNECTION_STABILITY_GUIDE.md)** - دليل شامل
2. **أوقف Battery Optimization** للتطبيق
3. **فعّل Auto-Start** (حسب نوع الهاتف)
4. **لا تغلق التطبيق** من Recent Apps
5. **تأكد من إشعار الخدمة** يظهر دائماً

### 🔧 الإصلاحات المطبقة:
- إعادة اتصال تلقائي ذكي (50 محاولة)
- مراقبة مستمرة للاتصال كل 30 ثانية
- Heartbeat محسّن كل 20 ثانية
- مقاومة لإعدادات توفير البطارية
- إعادة تشغيل تلقائي بعد إعادة تشغيل الهاتف

### 📊 اختبار الاتصال:
```bash
# على Linux/Mac
./test_connection_stability.sh

# مراقبة logs يدوياً
adb logcat | grep "RelayClient"
```

## الأمان

- جميع الطلبات محمية بـ API Key
- Webhook requests موقّعة بـ HMAC-SHA256
- قاعدة البيانات مشفرة
- IP Whitelist (يمكن تفعيله)

## الهيكل التقني

- **Language**: Kotlin
- **Architecture**: Clean Architecture + MVVM
- **Database**: Room (SQLite)
- **DI**: Hilt (Dagger)
- **HTTP Server**: NanoHTTPD
- **HTTP Client**: Retrofit + OkHttp
- **UI**: Jetpack Compose

## الترخيص

هذا المشروع مخصص للاستخدام الداخلي فقط.
