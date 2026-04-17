# SMS Payment Gateway - الإصدار المحسّن 🚀

## 📱 نظرة عامة

تطبيق Android متقدم يعمل كوسيط لتأكيد الدفع عبر قراءة رسائل SMS من المحافظ الإلكترونية المصرية، مع ميزات احترافية مستوحاة من أفضل المشاريع العالمية.

---

## 🌟 الميزات الأساسية

### ✅ الميزات الحالية
- قراءة وتحليل رسائل SMS تلقائيًا
- مطابقة العمليات مع طلبات الدفع المنتظرة
- API Server محلي على المنفذ 8080
- Webhook لإشعار الموقع فورًا
- تشفير وأمان متقدم (HMAC)
- يعمل 24/7 في الخلفية
- قاعدة بيانات محلية لتخزين السجلات
- اتصال مستقر مع الخادم (لا ينقطع)
- إعادة اتصال تلقائي ذكي
- مراقبة مستمرة لحالة الاتصال
- مقاوم لإعدادات توفير البطارية

### 🆕 الميزات الجديدة المقترحة

#### 1. نظام إعادة محاولة Webhook الذكي
- إعادة محاولة تلقائية عند فشل إرسال Webhook
- Exponential Backoff (1s, 2s, 4s, 8s, 16s)
- تسجيل شامل لجميع المحاولات
- إشعارات عند الفشل النهائي

#### 2. تشفير End-to-End
- تشفير AES-256 لبيانات الدفع
- المفتاح محفوظ فقط على الهاتف
- حماية كاملة للمعلومات الحساسة

#### 3. إرسال رسائل جماعية (Bulk SMS)
- رفع ملف CSV لإرسال رسائل متعددة
- معالجة دفعات كبيرة بكفاءة
- تقارير تفصيلية عن الإرسال

#### 4. دعم Multi-Device
- ربط عدة هواتف بنفس الحساب
- توزيع الحمل تلقائيًا
- زيادة معدل الإرسال

#### 5. Dashboard Analytics
- إحصائيات تفصيلية عن العمليات
- رسوم بيانية للأداء
- تقارير حسب الوقت والمحفظة

#### 6. نظام القوالب (Templates)
- قوالب جاهزة للرسائل
- متغيرات ديناميكية
- تخصيص سهل

---

## 💳 المحافظ المدعومة

- 📱 Vodafone Cash
- 🍊 Orange Money
- 📞 Etisalat Cash
- 💰 Fawry
- ⚡ InstaPay

---

## 🔧 متطلبات التشغيل

### الأساسية
- Android 8.0+ (API Level 26+)
- أذونات SMS (READ_SMS, RECEIVE_SMS)
- اتصال إنترنت مستمر

### للأداء الأمثل
- ⚠️ **إيقاف Battery Optimization (مهم جداً)**
- ⚠️ **تفعيل Auto-Start للتطبيق**
- ⚠️ **عدم إغلاق التطبيق من Recent Apps**
- 💾 مساحة تخزين كافية للسجلات

---

## 🚀 API Endpoints

### المعاملات (Transactions)

#### POST /api/v1/transactions
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

#### POST /api/v1/transactions/bulk 🆕
إرسال معاملات متعددة عبر CSV

```json
{
  "csvFile": "base64_encoded_csv",
  "columns": {
    "phoneNumber": "phone",
    "amount": "amount",
    "transactionId": "tx_id"
  }
}
```

#### GET /api/v1/transactions/{id}
الاستعلام عن حالة عملية

#### GET /api/v1/transactions
قائمة جميع العمليات المنتظرة

### السجلات (Logs)

#### GET /api/v1/sms/logs
سجل جميع الرسائل المستلمة

#### GET /api/v1/webhooks/logs 🆕
سجل محاولات إرسال Webhook

```bash
# عرض آخر 50 سجل
GET /api/v1/webhooks/logs?limit=50

# سجلات معاملة محددة
GET /api/v1/webhooks/logs?transactionId=TX123
```

### الإحصائيات (Analytics) 🆕

#### GET /api/v1/analytics/dashboard
إحصائيات شاملة عن الأداء

```json
{
  "totalTransactions": 1250,
  "successfulTransactions": 1180,
  "failedTransactions": 70,
  "successRate": 94.4,
  "averageProcessingTime": 2340,
  "transactionsByWallet": {
    "VODAFONE_CASH": 650,
    "ORANGE_MONEY": 400,
    "ETISALAT_CASH": 200
  }
}
```

#### GET /api/v1/analytics/webhooks 🆕
إحصائيات Webhook

### الأجهزة (Devices) 🆕

#### GET /api/v1/devices
قائمة الأجهزة المتصلة

#### POST /api/v1/devices/register
تسجيل جهاز جديد

---

## 🔐 Authentication

جميع الطلبات تحتاج إلى API Key في الـ Header:

```bash
Authorization: Bearer YOUR_API_KEY
```

### إدارة API Keys 🆕

```bash
# تدوير API Key
POST /api/v1/auth/rotate-key
Authorization: Bearer OLD_API_KEY

# Response
{
  "newApiKey": "new_secure_key_here",
  "expiresAt": 1713398400000,
  "gracePeriodHours": 24
}
```

---

## 📡 Webhook Callback

### الهيكل الأساسي

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

### Headers المرسلة 🆕

```
X-Webhook-Signature: hmac_sha256_signature
X-Webhook-Timestamp: 1713398400
X-Transaction-Id: your-tx-id
X-Attempt: 1
```

### التحقق من التوقيع

```javascript
const crypto = require('crypto');

function verifyWebhookSignature(payload, signature, secret, timestamp) {
  // التحقق من freshness (خلال 5 دقائق)
  const now = Math.floor(Date.now() / 1000);
  if (Math.abs(now - timestamp) > 300) {
    return false;
  }
  
  // حساب التوقيع المتوقع
  const data = `${timestamp}.${payload}`;
  const expectedSignature = crypto
    .createHmac('sha256', secret)
    .update(data)
    .digest('base64');
  
  return signature === expectedSignature;
}
```

### نظام إعادة المحاولة 🆕

- **عدد المحاولات**: 5 محاولات كحد أقصى
- **التوقيت**: 1s, 2s, 4s, 8s, 16s (Exponential Backoff)
- **أخطاء 4xx**: لا يتم إعادة المحاولة
- **أخطاء 5xx**: يتم إعادة المحاولة
- **Timeout**: 10 ثوانٍ لكل محاولة

---

## 🔒 الأمان

### الميزات الحالية
- جميع الطلبات محمية بـ API Key
- Webhook requests موقّعة بـ HMAC-SHA256
- قاعدة البيانات مشفرة
- IP Whitelist (يمكن تفعيله)

### الميزات الجديدة 🆕

#### 1. تشفير End-to-End
```kotlin
// تفعيل التشفير من الإعدادات
Settings -> Security -> Enable E2E Encryption
```

#### 2. API Key Rotation
```bash
# تدوير المفتاح كل 30 يوم
POST /api/v1/auth/rotate-key
```

#### 3. IP Whitelist المحسّن
```json
{
  "rules": [
    {
      "ipAddress": "192.168.1.100",
      "description": "Production Server",
      "expiresAt": null
    },
    {
      "subnet": "10.0.0.0/24",
      "description": "Internal Network",
      "expiresAt": 1713398400000
    }
  ]
}
```

---

## 📊 الهيكل التقني

### التقنيات المستخدمة
- **Language**: Kotlin
- **Architecture**: Clean Architecture + MVVM
- **Database**: Room (SQLite)
- **DI**: Hilt (Dagger)
- **HTTP Server**: NanoHTTPD
- **HTTP Client**: Retrofit + OkHttp
- **UI**: Jetpack Compose
- **Encryption**: AES-256-GCM 🆕
- **Background Jobs**: WorkManager 🆕

### هيكل المشروع

```
app/src/main/java/com/sms/paymentgateway/
├── data/
│   ├── entities/
│   │   ├── PendingTransaction.kt
│   │   ├── SmsLog.kt
│   │   ├── WebhookLog.kt 🆕
│   │   └── Device.kt 🆕
│   ├── dao/
│   │   ├── PendingTransactionDao.kt
│   │   ├── SmsLogDao.kt
│   │   ├── WebhookLogDao.kt 🆕
│   │   └── DeviceDao.kt 🆕
│   └── AppDatabase.kt
├── domain/
│   └── models/
│       ├── ParsedSmsData.kt
│       └── WalletType.kt
├── services/
│   ├── ApiServer.kt
│   ├── SmsProcessor.kt
│   ├── WebhookClient.kt
│   ├── WebhookRetryManager.kt 🆕
│   ├── EncryptionManager.kt 🆕
│   ├── DeviceLoadBalancer.kt 🆕
│   └── BulkSmsProcessor.kt 🆕
├── utils/
│   ├── security/
│   │   ├── SecurityManager.kt
│   │   └── RateLimiter.kt
│   └── parser/
│       └── SmsParser.kt
└── presentation/
    ├── ui/
    │   └── screens/
    │       ├── DashboardScreen.kt
    │       ├── SettingsScreen.kt
    │       ├── AnalyticsScreen.kt 🆕
    │       └── DevicesScreen.kt 🆕
    └── viewmodels/
        ├── DashboardViewModel.kt
        ├── SettingsViewModel.kt
        └── AnalyticsViewModel.kt 🆕
```

---

## 📥 التثبيت والتشغيل

### للمطورين (Development)

1. **استنسخ المشروع**:
   ```bash
   git clone https://github.com/drnopoh2810-spec/s.git
   cd s
   ```

2. **أنشئ ملف `local.properties`**:
   ```properties
   RELAY_API_KEY=your_api_key_here
   ```

3. **افتح المشروع في Android Studio**

4. **قم بتثبيت Dependencies**

5. **قم ببناء التطبيق**

6. **ثبّت التطبيق على هاتف Android**

7. **امنح جميع الأذونات المطلوبة**

8. **اضغط "Start Service"**

### تحميل APK الجاهز

- **Releases**: [أحدث إصدار](https://github.com/drnopoh2810-spec/s/releases/latest)
- **Actions Artifacts**: من أي build ناجح في [Actions](https://github.com/drnopoh2810-spec/s/actions)

---

## 📈 خطة التطوير

### المرحلة 1: الأساسيات (أسبوع 1-2) ✅
- [x] نظام إعادة محاولة Webhook
- [x] تحسين Battery Optimization
- [x] Message Expiration System

### المرحلة 2: الميزات المتقدمة (أسبوع 3-4) 🚧
- [ ] تشفير End-to-End
- [ ] Bulk SMS Support
- [ ] Dashboard Analytics

### المرحلة 3: التوسع (أسبوع 5-6) 📋
- [ ] Multi-Device Support
- [ ] SMS Template System
- [ ] API Rate Limiting

### المرحلة 4: التحسينات النهائية (أسبوع 7-8) 📋
- [ ] UI Enhancements
- [ ] Security Improvements
- [ ] Testing & Documentation

---

## 📚 الوثائق

### الأدلة المتاحة

1. **[IMPROVEMENT_RECOMMENDATIONS.md](IMPROVEMENT_RECOMMENDATIONS.md)** 🆕
   - توصيات شاملة للتطوير
   - مقارنة مع المشاريع المشابهة
   - خطة تنفيذ مفصلة

2. **[IMPLEMENTATION_GUIDE_WEBHOOK_RETRY.md](IMPLEMENTATION_GUIDE_WEBHOOK_RETRY.md)** 🆕
   - دليل خطوة بخطوة لتنفيذ نظام Webhook Retry
   - أمثلة كود كاملة
   - اختبارات Unit Tests

3. **[CONNECTION_STABILITY_GUIDE.md](CONNECTION_STABILITY_GUIDE.md)**
   - حل مشاكل انقطاع الاتصال
   - إعدادات Battery Optimization

4. **[API_DOCUMENTATION.md](API_DOCUMENTATION.md)**
   - توثيق شامل لجميع الـ APIs
   - أمثلة طلبات واستجابات

---

## 🧪 الاختبار

### Unit Tests

```bash
# تشغيل جميع الاختبارات
./gradlew test

# اختبارات محددة
./gradlew test --tests WebhookRetryManagerTest
./gradlew test --tests SmsParserTest
```

### Integration Tests

```bash
# اختبار API Server
curl -X GET http://localhost:8080/api/v1/health

# اختبار Webhook
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d @test_transaction.json
```

---

## 🤝 المساهمة

نرحب بالمساهمات! يرجى:

1. Fork المشروع
2. إنشاء branch للميزة (`git checkout -b feature/AmazingFeature`)
3. Commit التغييرات (`git commit -m 'Add some AmazingFeature'`)
4. Push للـ branch (`git push origin feature/AmazingFeature`)
5. فتح Pull Request

---

## 📞 الدعم

### المشاكل الشائعة

#### 1. انقطاع الاتصال
راجع [CONNECTION_STABILITY_GUIDE.md](CONNECTION_STABILITY_GUIDE.md)

#### 2. فشل Webhook
راجع سجلات Webhook:
```bash
GET /api/v1/webhooks/logs?transactionId=YOUR_TX_ID
```

#### 3. مشاكل الأداء
راجع Analytics Dashboard:
```bash
GET /api/v1/analytics/dashboard
```

### التواصل

- **GitHub Issues**: [إنشاء Issue](https://github.com/drnopoh2810-spec/s/issues)
- **Email**: support@example.com
- **Discord**: [انضم للمجتمع](#)

---

## 📄 الترخيص

هذا المشروع مخصص للاستخدام الداخلي فقط.

---

## 🙏 شكر وتقدير

هذا المشروع مستوحى من:
- [httpSMS](https://github.com/NdoleStudio/httpsms) - End-to-end encryption & webhook system
- [textbee.dev](https://github.com/vernu/textbee) - Bulk SMS & multi-device support
- [android-sms-gateway](https://github.com/capcom6/android-sms-gateway) - Battery optimization

---

## 📊 الإحصائيات

![GitHub stars](https://img.shields.io/github/stars/drnopoh2810-spec/s?style=social)
![GitHub forks](https://img.shields.io/github/forks/drnopoh2810-spec/s?style=social)
![GitHub issues](https://img.shields.io/github/issues/drnopoh2810-spec/s)
![GitHub license](https://img.shields.io/github/license/drnopoh2810-spec/s)

---

**آخر تحديث**: 17 أبريل 2026  
**الإصدار**: 2.0.0 (Enhanced)  
**الحالة**: 🚀 قيد التطوير النشط
