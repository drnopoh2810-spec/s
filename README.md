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

## متطلبات التشغيل

- Android 8.0+ (API Level 26+)
- أذونات SMS (READ_SMS, RECEIVE_SMS)
- إيقاف Battery Optimization
- اتصال إنترنت مستمر

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

1. افتح المشروع في Android Studio
2. قم بتثبيت Dependencies
3. قم ببناء التطبيق
4. ثبّت التطبيق على هاتف Android
5. امنح جميع الأذونات المطلوبة
6. اضغط "Start Service"
7. أضف التطبيق لقائمة استثناءات البطارية

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
