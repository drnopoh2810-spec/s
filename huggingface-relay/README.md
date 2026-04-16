# 🚀 SMS Gateway Relay - Hugging Face Spaces

## 🎯 المميزات

```
✅ يعمل 24/7 بدون انقطاع
✅ مجاني 100%
✅ لا ينام أبداً (Always Online)
✅ HTTPS تلقائي
✅ سرعة عالية
✅ WebSocket مستقر
✅ رابط ثابت
```

---

## 📋 خطوات النشر على Hugging Face

### **الخطوة 1: إنشاء حساب**

```
1. اذهب إلى: https://huggingface.co/join
2. سجّل حساب مجاني
3. تأكد من البريد الإلكتروني
```

---

### **الخطوة 2: إنشاء Space جديد**

```
1. اذهب إلى: https://huggingface.co/new-space

2. املأ المعلومات:
   ┌─────────────────────────────────────┐
   │ Space name: sms-gateway-relay       │
   │ License: MIT                        │
   │ SDK: Docker                         │
   │ Hardware: CPU basic (free)          │
   │ Visibility: Public                  │
   └─────────────────────────────────────┘

3. اضغط: Create Space
```

---

### **الخطوة 3: رفع الملفات**

#### **الطريقة 1: عبر الواجهة (الأسهل)**

```
1. في صفحة Space، اضغط: Files → Add file

2. أنشئ ملف: Dockerfile
   انسخ المحتوى من الأسفل ↓

3. أنشئ ملف: app.py
   انسخ المحتوى من huggingface-relay/app.py

4. أنشئ ملف: requirements.txt
   انسخ المحتوى من huggingface-relay/requirements.txt

5. اضغط: Commit changes
```

#### **الطريقة 2: عبر Git**

```bash
# Clone the space
git clone https://huggingface.co/spaces/YOUR_USERNAME/sms-gateway-relay
cd sms-gateway-relay

# انسخ الملفات
cp huggingface-relay/* .

# Push
git add .
git commit -m "Initial commit"
git push
```

---

### **الخطوة 4: إنشاء Dockerfile**

أنشئ ملف `Dockerfile` في Space:

```dockerfile
FROM python:3.10-slim

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy app
COPY app.py .

# Expose port
EXPOSE 7860

# Run app
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "7860"]
```

---

### **الخطوة 5: الانتظار (دقيقتين)**

```
Hugging Face سيبني Space تلقائياً
انتظر حتى يظهر: ✅ Running
```

---

### **الخطوة 6: الحصول على الرابط**

```
الرابط سيكون:
https://YOUR_USERNAME-sms-gateway-relay.hf.space

مثال:
https://ahmed-sms-gateway-relay.hf.space
```

---

## 🔗 استخدام الرابط

### **في التطبيق:**

```
اذهب إلى: الإعدادات → خادم الوسيط

أدخل:
wss://YOUR_USERNAME-sms-gateway-relay.hf.space/device/mydevice
```

### **في الموقع:**

```javascript
const BASE_URL = "https://YOUR_USERNAME-sms-gateway-relay.hf.space/api/mydevice/api/v1";
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

## 🎯 التحقق من التشغيل

### **1. Health Check:**
```
https://YOUR_SPACE.hf.space/health
```

**النتيجة المتوقعة:**
```json
{
  "status": "ok",
  "devices": 0,
  "uptime": 123.45,
  "timestamp": "2026-04-16T10:30:00"
}
```

### **2. الصفحة الرئيسية:**
```
https://YOUR_SPACE.hf.space/
```

ستظهر واجهة جميلة تعرض:
- حالة السيرفر
- عدد الأجهزة المتصلة
- كيفية الاتصال

### **3. قائمة الأجهزة:**
```
https://YOUR_SPACE.hf.space/devices
```

---

## ⚙️ الإعدادات المتقدمة

### **1. تفعيل Persistent Storage (اختياري):**

```
في Space Settings:
- Persistent storage: Enable
- Size: 20 GB (free)
```

### **2. تفعيل Secrets (للأمان):**

```
في Space Settings → Secrets:
- Add secret: API_SECRET_KEY
- Value: your_secret_key_here
```

ثم في `app.py`:
```python
import os
API_SECRET = os.getenv("API_SECRET_KEY", "default_key")
```

### **3. Custom Domain (اختياري):**

```
في Space Settings → Custom domain:
- أضف domain خاص بك
- مثال: relay.yourdomain.com
```

---

## 🔧 استكشاف الأخطاء

### **❌ Space لا يعمل:**

```
1. تحقق من Logs:
   Space → Logs → View logs

2. تأكد من Dockerfile صحيح

3. تأكد من requirements.txt صحيح

4. أعد بناء Space:
   Settings → Factory reboot
```

### **❌ WebSocket لا يتصل:**

```
1. تأكد من استخدام wss:// (مش ws://)

2. تأكد من الرابط صحيح:
   wss://YOUR_SPACE.hf.space/device/YOUR_ID

3. تحقق من Firewall في الموبايل
```

### **❌ Timeout errors:**

```
في app.py، زوّد timeout:
await asyncio.wait_for(event.wait(), timeout=60.0)
```

---

## 📊 المراقبة

### **1. Logs:**
```
Space → Logs
```

### **2. Metrics:**
```
Space → Metrics
- CPU usage
- Memory usage
- Network traffic
```

### **3. Uptime:**
```
استخدم UptimeRobot (مجاني):
https://uptimerobot.com

Monitor URL:
https://YOUR_SPACE.hf.space/health
```

---

## 💰 التكلفة

```
✅ Hugging Face Spaces: مجاني 100%
✅ CPU basic: مجاني
✅ Bandwidth: غير محدود
✅ Storage: 20 GB مجاني
✅ Always Online: مجاني

المجموع: 0 جنيه! 🎉
```

---

## 🚀 الترقية (اختياري)

إذا احتجت أداء أعلى:

```
Space Settings → Hardware:
- CPU basic (free) ← الحالي
- CPU upgrade ($0.60/hour)
- GPU T4 ($0.60/hour)
```

**لكن CPU basic كافي تماماً!**

---

## 📝 ملاحظات مهمة

### **1. Always Online:**
```
✅ Hugging Face Spaces لا تنام أبداً
✅ على عكس Replit (ينام بعد ساعة)
✅ على عكس Render (ينام بعد 15 دقيقة)
```

### **2. الأداء:**
```
✅ سرعة عالية
✅ Latency منخفض
✅ يتحمل آلاف الطلبات
```

### **3. الأمان:**
```
✅ HTTPS تلقائي
✅ WebSocket مشفّر (WSS)
✅ يمكن إضافة Authentication
```

---

## 🎁 المميزات الإضافية

### **1. Auto-restart:**
```
إذا حصل crash، Hugging Face يعيد التشغيل تلقائياً
```

### **2. Logs:**
```
جميع الـ logs محفوظة ويمكن الوصول لها
```

### **3. Versioning:**
```
كل commit محفوظ ويمكن الرجوع له
```

### **4. Collaboration:**
```
يمكن إضافة مطورين آخرين للـ Space
```

---

## ✅ الخلاصة

**Hugging Face Spaces هو الحل الأمثل:**

```
✅ مجاني 100%
✅ لا ينام أبداً
✅ سريع ومستقر
✅ سهل النشر
✅ HTTPS تلقائي
✅ Logs و Monitoring
✅ رابط ثابت
```

**بعد النشر، أرسل لي الرابط وأنا هدمجه في التطبيق!** 🚀

---

## 📞 الدعم

إذا واجهت أي مشاكل:

1. تحقق من Logs في Space
2. تأكد من Dockerfile صحيح
3. جرّب Factory reboot
4. اسألني! 😊
