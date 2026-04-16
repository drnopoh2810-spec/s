# دليل الاتصال المباشر - Direct Connection Guide

## 🚀 نظرة عامة

تم تطوير النظام ليعمل بدون خادم وسيط! الآن التطبيق يولد URL مباشر ويعمل كخادم مستقل يمكن للمواقع الاتصال به مباشرة.

## ✨ الميزات الجديدة

### 🔗 الاتصال المباشر
- **لا حاجة لخادم وسيط**: التطبيق يعمل كخادم مستقل
- **URL مباشر**: يتم توليد رابط مباشر للاتصال
- **WebSocket مدمج**: اتصال فوري ومستمر
- **Heartbeat تلقائي**: كل 30 ثانية للحفاظ على الاتصال

### 📱 واجهة محدثة
- **لوحة تحكم جديدة**: عرض حالة الاتصال المباشر
- **نسخ الرابط**: إمكانية نسخ URL الاتصال المباشر
- **مراقبة العملاء**: عرض عدد المواقع المتصلة
- **إعادة تشغيل سريع**: زر لإعادة تشغيل الاتصال

---

## 🛠️ كيفية العمل

### 1️⃣ بدء التطبيق
```
التطبيق يبدأ تلقائياً ويولد:
- عنوان IP المحلي
- رابط الاتصال المباشر
- خادم WebSocket مدمج
```

### 2️⃣ الحصول على الرابط
```
من لوحة التحكم:
1. افتح التطبيق
2. انظر لقسم "حالة الاتصال المباشر"
3. انسخ الرابط المعروض
4. استخدمه في موقعك
```

### 3️⃣ الاتصال من الموقع
```javascript
// مثال للاتصال من موقع ويب
const directUrl = "http://192.168.1.100:8080/api/v1/connect?key=YOUR_API_KEY";

// اتصال HTTP عادي
fetch(directUrl)
  .then(response => response.json())
  .then(data => {
    console.log('Connected:', data);
    // استخدم websocketUrl للاتصال المباشر
    connectWebSocket(data.websocketUrl);
  });

// اتصال WebSocket للإشعارات الفورية
function connectWebSocket(wsUrl) {
  const ws = new WebSocket(wsUrl);
  
  ws.onopen = () => {
    console.log('WebSocket connected');
    // اشتراك في الإشعارات
    ws.send(JSON.stringify({type: 'subscribe'}));
  };
  
  ws.onmessage = (event) => {
    const data = JSON.parse(event.data);
    if (data.event === 'PAYMENT_CONFIRMED') {
      console.log('Payment confirmed:', data.data);
      // معالجة تأكيد الدفعة
    }
  };
}
```

---

## 📡 API Endpoints

### الاتصال المباشر
```
GET /api/v1/connect?key=API_KEY
```
**الاستجابة:**
```json
{
  "status": "connected",
  "serverTime": 1705329004000,
  "apiVersion": "2.0.0",
  "connectionType": "direct",
  "websocketUrl": "ws://192.168.1.100:8080/websocket",
  "httpUrl": "http://192.168.1.100:8080/api/v1",
  "heartbeatInterval": 30000,
  "features": [
    "real-time-notifications",
    "transaction-tracking",
    "sms-processing",
    "webhook-callbacks"
  ]
}
```

### معلومات الاتصال
```
GET /api/v1/connection-info
```
**الاستجابة:**
```json
{
  "localIp": "192.168.1.100",
  "port": 8080,
  "directUrl": "http://192.168.1.100:8080/api/v1/connect?key=abc123",
  "websocketUrl": "ws://192.168.1.100:8080/websocket",
  "apiKey": "abc123",
  "isActive": true,
  "timestamp": 1705329004000
}
```

### فحص الحالة
```
GET /api/v1/health
```
**الاستجابة:**
```json
{
  "status": "ok",
  "timestamp": 1705329004000,
  "service": "SMS Payment Gateway",
  "version": "2.0.0",
  "connectionType": "direct",
  "localIp": "192.168.1.100"
}
```

---

## 🔄 WebSocket Events

### الأحداث الواردة (من التطبيق)

#### تأكيد الدفعة
```json
{
  "event": "PAYMENT_CONFIRMED",
  "data": {
    "transactionId": "order-12345",
    "smsData": {
      "walletType": "VODAFONE_CASH",
      "walletTxId": "VC123456789",
      "amount": 500.00,
      "senderPhone": "01012345678",
      "timestamp": 1705329004000,
      "confidence": 0.98
    },
    "status": "confirmed"
  },
  "timestamp": 1705329005000
}
```

#### استقبال SMS
```json
{
  "event": "SMS_RECEIVED",
  "data": {
    "sender": "vodafone",
    "parsed": true,
    "walletType": "VODAFONE_CASH",
    "status": "received"
  },
  "timestamp": 1705329004000
}
```

#### إنشاء معاملة
```json
{
  "event": "TRANSACTION_CREATED",
  "data": {
    "transactionId": "order-12345",
    "amount": 500.00,
    "phoneNumber": "01012345678",
    "status": "created"
  },
  "timestamp": 1705329004000
}
```

#### Heartbeat
```json
{
  "type": "heartbeat",
  "timestamp": 1705329004000,
  "status": "alive"
}
```

### الأحداث الصادرة (إلى التطبيق)

#### Ping
```json
{
  "type": "ping"
}
```

#### اشتراك في الإشعارات
```json
{
  "type": "subscribe"
}
```

---

## 🔧 الإعدادات والتكوين

### متطلبات الشبكة
- **المنفذ**: 8080 (افتراضي)
- **البروتوكول**: HTTP/WebSocket
- **الشبكة**: WiFi أو بيانات الجوال

### الأمان
- **API Key**: مطلوب لجميع الطلبات
- **IP Whitelist**: اختياري
- **Rate Limiting**: 100 طلب/دقيقة
- **HMAC Signatures**: للـ webhooks

### إعدادات الاتصال
```kotlin
// في DirectConnectionManager
companion object {
    private const val DEFAULT_PORT = 8080
    private const val HEARTBEAT_INTERVAL = 30_000L // 30 ثانية
    private const val CONNECTION_TIMEOUT = 60_000L // 60 ثانية
}
```

---

## 🚨 استكشاف الأخطاء

### المشاكل الشائعة

#### 1. لا يمكن الوصول للرابط
```
الحلول:
✓ تأكد من اتصال الهاتف بنفس الشبكة
✓ تحقق من إعدادات الجدار الناري
✓ جرب إعادة تشغيل الاتصال من التطبيق
```

#### 2. انقطاع الاتصال المستمر
```
الحلول:
✓ تحقق من استقرار الشبكة
✓ تأكد من عدم دخول الهاتف في وضع السكون
✓ راجع إعدادات توفير البطارية
```

#### 3. عدم استقبال الإشعارات
```
الحلول:
✓ تأكد من الاشتراك في WebSocket
✓ تحقق من صحة API Key
✓ راجع سجلات التطبيق
```

### سجلات التطبيق
```
العلامات المهمة:
- DirectConnectionManager: حالة الاتصال المباشر
- ApiServer: طلبات HTTP
- WebSocketHandler: اتصالات WebSocket
- SmsProcessor: معالجة الرسائل
```

---

## 📋 مثال كامل للتكامل

### HTML + JavaScript
```html
<!DOCTYPE html>
<html>
<head>
    <title>SMS Payment Gateway - Direct Connection</title>
</head>
<body>
    <div id="status">جاري الاتصال...</div>
    <div id="payments"></div>

    <script>
        class PaymentGateway {
            constructor(baseUrl, apiKey) {
                this.baseUrl = baseUrl;
                this.apiKey = apiKey;
                this.ws = null;
            }

            async connect() {
                try {
                    // الاتصال الأولي
                    const response = await fetch(
                        `${this.baseUrl}/api/v1/connect?key=${this.apiKey}`
                    );
                    const data = await response.json();
                    
                    document.getElementById('status').textContent = 'متصل ✓';
                    
                    // بدء WebSocket
                    this.connectWebSocket(data.websocketUrl);
                    
                    return data;
                } catch (error) {
                    document.getElementById('status').textContent = 'خطأ في الاتصال ✗';
                    console.error('Connection failed:', error);
                }
            }

            connectWebSocket(wsUrl) {
                this.ws = new WebSocket(wsUrl);
                
                this.ws.onopen = () => {
                    console.log('WebSocket connected');
                    this.ws.send(JSON.stringify({type: 'subscribe'}));
                };
                
                this.ws.onmessage = (event) => {
                    const message = JSON.parse(event.data);
                    this.handleMessage(message);
                };
                
                this.ws.onclose = () => {
                    console.log('WebSocket disconnected');
                    // إعادة الاتصال بعد 5 ثوان
                    setTimeout(() => this.connect(), 5000);
                };
            }

            handleMessage(message) {
                if (message.event === 'PAYMENT_CONFIRMED') {
                    this.onPaymentConfirmed(message.data);
                } else if (message.type === 'heartbeat') {
                    console.log('Heartbeat received');
                }
            }

            onPaymentConfirmed(data) {
                const paymentsDiv = document.getElementById('payments');
                const paymentElement = document.createElement('div');
                paymentElement.innerHTML = `
                    <h3>دفعة مؤكدة ✓</h3>
                    <p>رقم المعاملة: ${data.transactionId}</p>
                    <p>المبلغ: ${data.smsData.amount} جنيه</p>
                    <p>المحفظة: ${data.smsData.walletType}</p>
                    <p>الثقة: ${(data.smsData.confidence * 100).toFixed(1)}%</p>
                `;
                paymentsDiv.appendChild(paymentElement);
            }

            async createTransaction(transactionData) {
                const response = await fetch(`${this.baseUrl}/api/v1/transactions`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${this.apiKey}`
                    },
                    body: JSON.stringify(transactionData)
                });
                return response.json();
            }
        }

        // الاستخدام
        const gateway = new PaymentGateway(
            'http://192.168.1.100:8080', // عنوان IP الخاص بالهاتف
            'your-api-key-here'
        );

        gateway.connect().then(data => {
            console.log('Gateway connected:', data);
        });
    </script>
</body>
</html>
```

---

## 🎯 الخلاصة

### المزايا الجديدة
✅ **لا حاجة لخادم وسيط**  
✅ **اتصال مباشر وسريع**  
✅ **إشعارات فورية عبر WebSocket**  
✅ **سهولة التكامل**  
✅ **مراقبة مباشرة للاتصالات**  

### التحسينات
🚀 **أداء أفضل**: لا توجد وسطاء  
🔒 **أمان محسن**: اتصال مباشر مشفر  
📱 **واجهة محدثة**: معلومات الاتصال المباشر  
⚡ **استجابة أسرع**: WebSocket مدمج  

### الخطوات التالية
1. اختبر الاتصال المباشر
2. تكامل مع موقعك
3. مراقبة الأداء
4. تحسين الإعدادات حسب الحاجة

---

**🎉 النظام جاهز للاستخدام مع الاتصال المباشر!**