# SMS Payment Gateway — توثيق API الكامل

## المعمارية

```
موقعك (أي دومين)
      │
      │  HTTPS  (Authorization: Bearer API_KEY)
      ▼
┌─────────────────────────────────────┐
│       Relay Server (Replit)         │
│  https://your-relay.replit.app      │
│  - يستقبل طلبات المواقع عبر HTTPS   │
│  - يمرر الطلبات للهاتف عبر WebSocket│
└─────────────────┬───────────────────┘
                  │
                  │  WebSocket (wss://)
                  │  اتصال دائم مشفر
                  ▼
┌─────────────────────────────────────┐
│        هاتف Android                 │
│  - يراقب رسائل SMS                  │
│  - يطابق الدفعات تلقائياً           │
│  - يرسل Webhook عند التأكيد         │
└─────────────────────────────────────┘
```

---

## الإعداد السريع

### 1. تثبيت التطبيق
قم بتثبيت APK على هاتف Android (يُشترط Android 8.0+)

### 2. إعداد Relay Server
في إعدادات التطبيق (Settings → Relay Server URL):
```
wss://your-relay.replit.app/device
```

### 3. الحصول على API Key
من الشاشة الرئيسية للتطبيق (Settings → API Key)

### 4. استخدام API من موقعك
```javascript
const RELAY = 'https://your-relay.replit.app';
const API_KEY = 'your-api-key-from-app';

const headers = {
  'Authorization': `Bearer ${API_KEY}`,
  'Content-Type': 'application/json'
};
```

---

## Base URL

```
https://your-relay.replit.app/api/v1
```

> استبدل `your-relay.replit.app` بالدومين الفعلي للـ Relay Server بعد النشر.

---

## المصادقة

جميع الطلبات تتطلب API Key في الـ header:

```http
Authorization: Bearer YOUR_API_KEY
```

أو بديلاً:
```http
X-Api-Key: YOUR_API_KEY
```

---

## Endpoints

---

### 1. فحص حالة Relay Server

```http
GET /relay/status
```

لا تحتاج مصادقة. تتحقق من أن الخادم يعمل وكم جهاز متصل.

**Response:**
```json
{
  "status": "ok",
  "version": "1.0.0",
  "connectedDevices": 1,
  "uptime": 3600
}
```

---

### 2. فحص حالة التطبيق (Android)

```http
GET /api/v1/health
Authorization: Bearer YOUR_API_KEY
```

**Response:**
```json
{
  "status": "ok",
  "timestamp": 1705329000000,
  "service": "SMS Payment Gateway"
}
```

**أكواد الاستجابة:**
- `200` — التطبيق يعمل
- `503` — الهاتف غير متصل بالـ Relay

---

### 3. إنشاء معاملة دفع

```http
POST /api/v1/transactions
Authorization: Bearer YOUR_API_KEY
Content-Type: application/json
```

**Request Body:**
```json
{
  "id": "order-12345",
  "amount": 500.00,
  "phoneNumber": "01012345678",
  "expectedTxId": "VC123456789",
  "walletType": "VODAFONE_CASH",
  "expiresInMinutes": 30
}
```

| الحقل | النوع | الحالة | الوصف |
|-------|-------|--------|-------|
| `id` | string | مطلوب | معرّف فريد للمعاملة |
| `amount` | number | مطلوب | المبلغ المتوقع بالجنيه |
| `phoneNumber` | string | مطلوب | رقم الهاتف المُرسِل (01xxxxxxxxx) |
| `expectedTxId` | string | اختياري | رقم عملية المحفظة للمطابقة الدقيقة |
| `walletType` | string | اختياري | نوع المحفظة |
| `expiresInMinutes` | number | اختياري | مدة الانتهاء (افتراضي: 30 دقيقة) |

**أنواع المحافظ المدعومة:**
- `VODAFONE_CASH` — فودافون كاش
- `ORANGE_MONEY` — أورنج موني
- `ETISALAT_CASH` — اتصالات كاش (WE)
- `FAWRY` — فوري
- `INSTAPAY` — انستاباي

**Response (201 Created):**
```json
{
  "success": true,
  "transaction": {
    "id": "order-12345",
    "amount": 500.00,
    "phoneNumber": "01012345678",
    "status": "PENDING",
    "createdAt": "2024-01-15T14:30:00.000Z",
    "expiresAt": "2024-01-15T15:00:00.000Z"
  }
}
```

---

### 4. التحقق من حالة معاملة

```http
GET /api/v1/transactions/{id}
Authorization: Bearer YOUR_API_KEY
```

**Response (200 OK):**
```json
{
  "id": "order-12345",
  "amount": 500.00,
  "phoneNumber": "01012345678",
  "status": "MATCHED",
  "confidence": 0.98,
  "createdAt": "2024-01-15T14:30:00.000Z",
  "matchedAt": "2024-01-15T14:30:05.000Z"
}
```

**قيم الـ status:**

| القيمة | المعنى |
|--------|--------|
| `PENDING` | في انتظار رسالة SMS |
| `MATCHED` | تم استلام SMS ومطابقة الدفعة ✅ |
| `EXPIRED` | انتهت المدة المحددة |
| `CANCELLED` | تم الإلغاء |

---

### 5. قائمة جميع المعاملات

```http
GET /api/v1/transactions
Authorization: Bearer YOUR_API_KEY
```

**Response:**
```json
{
  "transactions": [
    {
      "id": "order-12345",
      "amount": 500.00,
      "status": "PENDING",
      "createdAt": "2024-01-15T14:30:00.000Z"
    }
  ]
}
```

---

### 6. سجلات رسائل SMS

```http
GET /api/v1/sms/logs
Authorization: Bearer YOUR_API_KEY
```

**Response:**
```json
{
  "logs": [
    {
      "id": 1,
      "sender": "Vodafone",
      "message": "تم استلام 500 جنيه من 01012345678 رقم العملية VC123456789",
      "receivedAt": "2024-01-15T14:30:04.000Z",
      "walletType": "VODAFONE_CASH",
      "amount": 500.00,
      "transactionId": "VC123456789",
      "phoneNumber": "01012345678",
      "parsed": true,
      "matched": true,
      "confidence": 0.98
    }
  ]
}
```

---

## Webhook (إشعارات تلقائية)

عند تأكيد الدفع، يرسل التطبيق تلقائياً `POST` لـ Webhook URL.

**Headers:**
```http
Content-Type: application/json
X-Signature: <hmac-sha256>
```

**Payload:**
```json
{
  "event": "PAYMENT_CONFIRMED",
  "transactionId": "order-12345",
  "smsData": {
    "walletType": "VODAFONE_CASH",
    "walletTxId": "VC123456789",
    "amount": 500.00,
    "senderPhone": "01012345678",
    "timestamp": 1705329004000
  },
  "confidence": 0.98,
  "processedAt": 1705329005000
}
```

**التحقق من صحة Webhook (Node.js):**
```javascript
const crypto = require('crypto');

app.post('/webhook', express.raw({ type: 'application/json' }), (req, res) => {
  const sig = req.headers['x-signature'];
  const computed = crypto.createHmac('sha256', 'YOUR_WEBHOOK_SECRET')
    .update(req.body).digest('hex');
  
  if (computed !== sig) return res.status(400).send('Invalid signature');
  
  const data = JSON.parse(req.body);
  if (data.event === 'PAYMENT_CONFIRMED') {
    // ✅ الدفعة مؤكدة
    console.log('Paid:', data.transactionId, 'Amount:', data.smsData.amount);
  }
  res.sendStatus(200);
});
```

---

## أمثلة كاملة

### JavaScript
```javascript
const RELAY  = 'https://your-relay.replit.app';
const API_KEY = 'your-api-key';
const headers = { 'Authorization': `Bearer ${API_KEY}`, 'Content-Type': 'application/json' };

// 1. إنشاء معاملة
const res = await fetch(`${RELAY}/api/v1/transactions`, {
  method: 'POST', headers,
  body: JSON.stringify({ id: 'order-' + Date.now(), amount: 500, phoneNumber: '01012345678' })
});
const { transaction } = await res.json();

// 2. Polling للتحقق
async function waitForPayment(id) {
  for (let i = 0; i < 60; i++) {
    await new Promise(r => setTimeout(r, 5000));
    const r = await fetch(`${RELAY}/api/v1/transactions/${id}`, { headers });
    const tx = await r.json();
    if (tx.status === 'MATCHED') return { success: true, confidence: tx.confidence };
    if (tx.status === 'EXPIRED') return { success: false };
  }
  return { success: false };
}
```

### PHP
```php
<?php
$relay  = 'https://your-relay.replit.app';
$apiKey = 'your-api-key';

$ctx = stream_context_create(['http' => [
  'method'  => 'POST',
  'header'  => "Authorization: Bearer $apiKey\r\nContent-Type: application/json",
  'content' => json_encode(['id'=>'order-1','amount'=>500,'phoneNumber'=>'01012345678'])
]]);
$response = json_decode(file_get_contents("$relay/api/v1/transactions", false, $ctx), true);
echo "Transaction ID: " . $response['transaction']['id'];
?>
```

### Python
```python
import requests, time

RELAY   = 'https://your-relay.replit.app'
API_KEY = 'your-api-key'
headers = {'Authorization': f'Bearer {API_KEY}'}

tx = requests.post(f'{RELAY}/api/v1/transactions', headers=headers,
  json={'id': 'order-1', 'amount': 500.0, 'phoneNumber': '01012345678'}).json()

for _ in range(20):
    time.sleep(5)
    st = requests.get(f"{RELAY}/api/v1/transactions/{tx['transaction']['id']}", headers=headers).json()
    if st['status'] == 'MATCHED':
        print(f"✅ Confirmed! Confidence: {st['confidence']}")
        break
```

### cURL
```bash
# إنشاء معاملة
curl -X POST https://your-relay.replit.app/api/v1/transactions \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"id":"order-1","amount":500,"phoneNumber":"01012345678"}'

# التحقق من الحالة
curl https://your-relay.replit.app/api/v1/transactions/order-1 \
  -H "Authorization: Bearer YOUR_API_KEY"

# حالة Relay Server
curl https://your-relay.replit.app/relay/status
```

---

## أكواد الخطأ

| الكود | المعنى | الحل |
|-------|--------|------|
| `401` | API Key مفقود أو خاطئ | تحقق من API Key في إعدادات التطبيق |
| `403` | IP محظور | أضف IP موقعك في Whitelist |
| `429` | تجاوز حد الطلبات | قلّل معدل الطلبات |
| `503` | الهاتف غير متصل | تأكد من تشغيل التطبيق والاتصال بـ Relay |
| `504` | انتهت مهلة الطلب | الهاتف متصل لكن لم يرد في 30 ثانية |

---

## ملاحظات مهمة

1. **اتصال مستمر:** يحتاج الهاتف لاتصال إنترنت مستمر لاستقبال الطلبات
2. **صلاحيات SMS:** يجب منح التطبيق صلاحية قراءة الرسائل عند تثبيته
3. **تحسين البطارية:** يُنصح بإيقاف Battery Optimization للتطبيق
4. **الدقة:** نسبة الثقة `confidence ≥ 0.7` تعني مطابقة ناجحة

---

## GitHub & الموارد

- **الكود المصدري:** https://github.com/drnopoh2810-spec/s
- **Postman Collection:** `SMS_Payment_Gateway.postman_collection.json`
- **دليل الإعداد:** `SETUP_GUIDE.md`
- **دليل الاختبار:** `TESTING_GUIDE.md`
