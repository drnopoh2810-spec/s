# Webhook API Documentation

## 📋 نظرة عامة

توثيق شامل لـ Webhook APIs الجديدة في SMS Payment Gateway.

---

## 🔐 Authentication

جميع الطلبات تحتاج إلى API Key:

```http
Authorization: Bearer YOUR_API_KEY
```

---

## 📡 Webhook Endpoints

### 1. Get Webhook Logs

الحصول على سجلات محاولات إرسال Webhook.

**Endpoint**: `GET /api/v1/webhooks/logs`

**Query Parameters**:
- `transactionId` (optional): معرف المعاملة للتصفية
- `limit` (optional): عدد السجلات (default: 100)

**Request Example**:
```bash
# جميع السجلات
curl -X GET "http://localhost:8080/api/v1/webhooks/logs?limit=50" \
  -H "Authorization: Bearer YOUR_API_KEY"

# سجلات معاملة محددة
curl -X GET "http://localhost:8080/api/v1/webhooks/logs?transactionId=TX123" \
  -H "Authorization: Bearer YOUR_API_KEY"
```

**Response Example**:
```json
{
  "logs": [
    {
      "id": 1,
      "transactionId": "TX123",
      "webhookUrl": "https://example.com/webhook",
      "attempt": 1,
      "status": "SUCCESS",
      "httpStatusCode": 200,
      "errorMessage": null,
      "requestPayload": "{\"event\":\"PAYMENT_CONFIRMED\",\"transactionId\":\"TX123\"}",
      "responseBody": "{\"status\":\"received\"}",
      "timestamp": 1713398400000,
      "processingTimeMs": 245
    },
    {
      "id": 2,
      "transactionId": "TX124",
      "webhookUrl": "https://example.com/webhook",
      "attempt": 1,
      "status": "RETRYING",
      "httpStatusCode": 503,
      "errorMessage": "HTTP 503: Service Unavailable",
      "requestPayload": "{\"event\":\"PAYMENT_CONFIRMED\",\"transactionId\":\"TX124\"}",
      "responseBody": null,
      "timestamp": 1713398450000,
      "processingTimeMs": 5000
    }
  ],
  "count": 2,
  "transactionId": null,
  "limit": 50
}
```

**Response Fields**:
- `id`: معرف السجل
- `transactionId`: معرف المعاملة
- `webhookUrl`: عنوان Webhook
- `attempt`: رقم المحاولة (1-5)
- `status`: حالة المحاولة (PENDING, SUCCESS, FAILED, RETRYING)
- `httpStatusCode`: HTTP status code إن وجد
- `errorMessage`: رسالة الخطأ إن وجدت
- `requestPayload`: البيانات المرسلة (JSON string)
- `responseBody`: الرد المستلم
- `timestamp`: وقت المحاولة (Unix timestamp)
- `processingTimeMs`: وقت المعالجة بالميلي ثانية

---

### 2. Get Webhook Statistics

الحصول على إحصائيات شاملة عن Webhooks.

**Endpoint**: `GET /api/v1/webhooks/stats`

**Query Parameters**:
- `startTime` (optional): بداية الفترة (Unix timestamp، default: آخر 24 ساعة)
- `endTime` (optional): نهاية الفترة (Unix timestamp، default: الآن)

**Request Example**:
```bash
# إحصائيات آخر 24 ساعة
curl -X GET "http://localhost:8080/api/v1/webhooks/stats" \
  -H "Authorization: Bearer YOUR_API_KEY"

# إحصائيات فترة محددة
curl -X GET "http://localhost:8080/api/v1/webhooks/stats?startTime=1713312000000&endTime=1713398400000" \
  -H "Authorization: Bearer YOUR_API_KEY"
```

**Response Example**:
```json
{
  "period": {
    "startTime": 1713312000000,
    "endTime": 1713398400000,
    "durationMs": 86400000
  },
  "attempts": {
    "total": 150,
    "success": 142,
    "failed": 5,
    "retrying": 2,
    "pending": 1
  },
  "transactions": {
    "unique": 145,
    "withRetries": 8
  },
  "performance": {
    "successRate": "94.67%",
    "averageProcessingTimeMs": "234.56"
  }
}
```

**Response Fields**:

**period**:
- `startTime`: بداية الفترة
- `endTime`: نهاية الفترة
- `durationMs`: مدة الفترة بالميلي ثانية

**attempts**:
- `total`: إجمالي المحاولات
- `success`: المحاولات الناجحة
- `failed`: المحاولات الفاشلة
- `retrying`: المحاولات قيد الإعادة
- `pending`: المحاولات المنتظرة

**transactions**:
- `unique`: عدد المعاملات الفريدة
- `withRetries`: عدد المعاملات التي احتاجت إعادة محاولة

**performance**:
- `successRate`: نسبة النجاح
- `averageProcessingTimeMs`: متوسط وقت المعالجة

---

## 📤 Webhook Payload Format

عند إرسال Webhook، يتم إرسال البيانات التالية:

### Headers

```http
Content-Type: application/json
X-Webhook-Signature: <HMAC-SHA256 signature>
X-Webhook-Timestamp: <Unix timestamp>
X-Transaction-Id: <transaction ID>
X-Attempt: <attempt number>
User-Agent: SMS-Payment-Gateway/2.0
```

### Body

```json
{
  "event": "PAYMENT_CONFIRMED",
  "transactionId": "TX123",
  "smsData": {
    "walletType": "VODAFONE_CASH",
    "walletTxId": "VC123456789",
    "amount": 500.00,
    "senderPhone": "01012345678",
    "timestamp": 1713398400000
  },
  "confidence": 0.98,
  "processedAt": 1713398405000
}
```

---

## 🔒 Webhook Signature Verification

للتحقق من صحة Webhook:

### JavaScript/Node.js

```javascript
const crypto = require('crypto');

function verifyWebhookSignature(payload, signature, secret, timestamp) {
  // 1. التحقق من freshness (خلال 5 دقائق)
  const now = Math.floor(Date.now() / 1000);
  if (Math.abs(now - timestamp) > 300) {
    return false;
  }
  
  // 2. حساب التوقيع المتوقع
  const data = `${timestamp}.${payload}`;
  const expectedSignature = crypto
    .createHmac('sha256', secret)
    .update(data)
    .digest('base64');
  
  // 3. المقارنة
  return signature === expectedSignature;
}

// الاستخدام
app.post('/webhook', (req, res) => {
  const payload = JSON.stringify(req.body);
  const signature = req.headers['x-webhook-signature'];
  const timestamp = parseInt(req.headers['x-webhook-timestamp']);
  const secret = 'YOUR_API_KEY';
  
  if (!verifyWebhookSignature(payload, signature, secret, timestamp)) {
    return res.status(401).json({ error: 'Invalid signature' });
  }
  
  // معالجة Webhook
  console.log('Payment confirmed:', req.body.transactionId);
  res.json({ status: 'received' });
});
```

### PHP

```php
<?php
function verifyWebhookSignature($payload, $signature, $secret, $timestamp) {
    // 1. التحقق من freshness
    $now = time();
    if (abs($now - $timestamp) > 300) {
        return false;
    }
    
    // 2. حساب التوقيع المتوقع
    $data = $timestamp . '.' . $payload;
    $expectedSignature = base64_encode(
        hash_hmac('sha256', $data, $secret, true)
    );
    
    // 3. المقارنة
    return hash_equals($signature, $expectedSignature);
}

// الاستخدام
$payload = file_get_contents('php://input');
$signature = $_SERVER['HTTP_X_WEBHOOK_SIGNATURE'];
$timestamp = intval($_SERVER['HTTP_X_WEBHOOK_TIMESTAMP']);
$secret = 'YOUR_API_KEY';

if (!verifyWebhookSignature($payload, $signature, $secret, $timestamp)) {
    http_response_code(401);
    echo json_encode(['error' => 'Invalid signature']);
    exit;
}

// معالجة Webhook
$data = json_decode($payload, true);
echo json_encode(['status' => 'received']);
?>
```

### Python

```python
import hmac
import hashlib
import base64
import time
import json

def verify_webhook_signature(payload, signature, secret, timestamp):
    # 1. التحقق من freshness
    now = int(time.time())
    if abs(now - timestamp) > 300:
        return False
    
    # 2. حساب التوقيع المتوقع
    data = f"{timestamp}.{payload}"
    expected_signature = base64.b64encode(
        hmac.new(
            secret.encode(),
            data.encode(),
            hashlib.sha256
        ).digest()
    ).decode()
    
    # 3. المقارنة
    return hmac.compare_digest(signature, expected_signature)

# الاستخدام (Flask)
from flask import Flask, request, jsonify

app = Flask(__name__)

@app.route('/webhook', methods=['POST'])
def webhook():
    payload = request.get_data(as_text=True)
    signature = request.headers.get('X-Webhook-Signature')
    timestamp = int(request.headers.get('X-Webhook-Timestamp'))
    secret = 'YOUR_API_KEY'
    
    if not verify_webhook_signature(payload, signature, secret, timestamp):
        return jsonify({'error': 'Invalid signature'}), 401
    
    # معالجة Webhook
    data = json.loads(payload)
    print(f"Payment confirmed: {data['transactionId']}")
    return jsonify({'status': 'received'})
```

---

## 🔄 Webhook Retry Logic

### كيف يعمل نظام إعادة المحاولة؟

1. **عدد المحاولات**: 5 محاولات كحد أقصى
2. **Exponential Backoff**: 1s, 2s, 4s, 8s, 16s
3. **Client Errors (4xx)**: لا يتم إعادة المحاولة
4. **Server Errors (5xx)**: يتم إعادة المحاولة
5. **Network Errors**: يتم إعادة المحاولة

### مثال Timeline

```
Attempt 1: Immediate (0s)     → Failed (503)
Attempt 2: After 1s           → Failed (503)
Attempt 3: After 2s (total 3s) → Failed (503)
Attempt 4: After 4s (total 7s) → Failed (503)
Attempt 5: After 8s (total 15s) → Success (200) ✅
```

### Status Codes

| Code | Description | Retry? |
|------|-------------|--------|
| 200-299 | Success | ❌ No |
| 400-499 | Client Error | ❌ No |
| 500-599 | Server Error | ✅ Yes |
| Network Error | Connection failed | ✅ Yes |

---

## 📊 Best Practices

### 1. Webhook Endpoint

```javascript
// ✅ Good: Idempotent endpoint
app.post('/webhook', async (req, res) => {
  const transactionId = req.body.transactionId;
  
  // تحقق من أن المعاملة لم تُعالج من قبل
  const existing = await db.findTransaction(transactionId);
  if (existing) {
    return res.json({ status: 'already_processed' });
  }
  
  // معالجة المعاملة
  await db.saveTransaction(req.body);
  
  // رد سريع
  res.json({ status: 'received' });
});
```

### 2. Response Time

```javascript
// ✅ Good: رد سريع ثم معالجة
app.post('/webhook', async (req, res) => {
  // رد فوري
  res.json({ status: 'received' });
  
  // معالجة في background
  processWebhook(req.body).catch(console.error);
});
```

### 3. Error Handling

```javascript
// ✅ Good: معالجة الأخطاء بشكل صحيح
app.post('/webhook', async (req, res) => {
  try {
    await processPayment(req.body);
    res.json({ status: 'success' });
  } catch (error) {
    console.error('Webhook error:', error);
    // رد بـ 500 لإعادة المحاولة
    res.status(500).json({ error: 'Processing failed' });
  }
});
```

---

## 🧪 Testing

### Test Webhook Locally

```bash
# 1. استخدم ngrok لعمل tunnel
ngrok http 3000

# 2. استخدم URL من ngrok في التطبيق
https://abc123.ngrok.io/webhook

# 3. أرسل معاملة تجريبية
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "test-001",
    "amount": 100.00,
    "phoneNumber": "01012345678",
    "walletType": "VODAFONE_CASH"
  }'

# 4. راقب السجلات
curl -X GET "http://localhost:8080/api/v1/webhooks/logs?transactionId=test-001" \
  -H "Authorization: Bearer YOUR_API_KEY"
```

---

## 📞 Support

إذا واجهت مشكلة:

1. **راجع السجلات**: `GET /api/v1/webhooks/logs`
2. **راجع الإحصائيات**: `GET /api/v1/webhooks/stats`
3. **تحقق من التوقيع**: استخدم أمثلة الكود أعلاه
4. **راجع Logs**: `adb logcat | grep "WebhookRetryManager"`

---

**آخر تحديث**: 17 أبريل 2026  
**الإصدار**: 2.0.0
