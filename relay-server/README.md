# 🌐 SMS Gateway Relay Server

## نشر على Replit (مجاني)

### الخطوات:

1. **إنشاء حساب:**
   ```
   https://replit.com/signup
   ```

2. **إنشاء Repl جديد:**
   ```
   - اضغط: Create Repl
   - Template: Node.js
   - Title: sms-gateway-relay
   ```

3. **رفع الملفات:**
   ```
   - انسخ محتوى server.js
   - انسخ محتوى package.json
   - الصق في Replit
   ```

4. **تشغيل:**
   ```
   اضغط: Run
   ```

5. **الحصول على الرابط:**
   ```
   سيظهر رابط مثل:
   https://sms-gateway-relay.YOUR_USERNAME.repl.co
   ```

6. **استخدام في التطبيق:**
   ```
   WebSocket URL:
   wss://sms-gateway-relay.YOUR_USERNAME.repl.co/device/mydevice
   
   Public API URL:
   https://sms-gateway-relay.YOUR_USERNAME.repl.co/api/mydevice
   ```

---

## نشر على Render (مجاني)

### الخطوات:

1. **إنشاء حساب:**
   ```
   https://render.com/register
   ```

2. **إنشاء Web Service:**
   ```
   - New → Web Service
   - Connect GitHub repo أو رفع الملفات
   - Environment: Node
   - Build Command: npm install
   - Start Command: npm start
   ```

3. **الحصول على الرابط:**
   ```
   https://sms-gateway-relay.onrender.com
   ```

---

## نشر على Railway (مجاني)

### الخطوات:

1. **إنشاء حساب:**
   ```
   https://railway.app
   ```

2. **New Project:**
   ```
   - Deploy from GitHub repo
   - أو: Empty Project → Add Service
   ```

3. **الحصول على الرابط:**
   ```
   https://sms-gateway-relay.up.railway.app
   ```

---

## الاستخدام

### في التطبيق:

```kotlin
// في الإعدادات، أدخل:
Relay URL: wss://YOUR_RELAY_URL/device/mydevice

// التطبيق سيتصل تلقائياً ويحصل على:
Public URL: https://YOUR_RELAY_URL/api/mydevice
```

### في الموقع:

```javascript
const BASE_URL = "https://YOUR_RELAY_URL/api/mydevice/api/v1";
const API_KEY = "YOUR_API_KEY";

fetch(`${BASE_URL}/transactions`, {
  method: "POST",
  headers: {
    "Authorization": `Bearer ${API_KEY}`,
    "Content-Type": "application/json"
  },
  body: JSON.stringify({
    id: "order-001",
    amount: 500,
    phoneNumber: "01012345678"
  })
});
```

---

## المميزات

- ✅ مجاني 100%
- ✅ يعمل من أي شبكة
- ✅ HTTPS تلقائي
- ✅ رابط ثابت
- ✅ WebSocket مستقر
- ✅ Heartbeat تلقائي
- ✅ إعادة اتصال تلقائية

---

## الصيانة

### Keep Alive (لمنع النوم):

```javascript
// أضف في server.js:
setInterval(() => {
  console.log('Keep alive ping');
}, 5 * 60 * 1000); // كل 5 دقائق
```

### Monitoring:

```
https://YOUR_RELAY_URL/health
https://YOUR_RELAY_URL/devices
```
