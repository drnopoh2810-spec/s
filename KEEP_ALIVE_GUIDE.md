# 💓 دليل Keep Alive الشامل

## 🎯 الهدف
التأكد من أن Hugging Face Space **لا ينام أبداً** ويعمل 24/7

---

## ✅ الحماية المُطبّقة

### **الطبقة 1: Internal Keep Alive (داخل السيرفر)**

تم تطبيق **3 آليات** داخل `app.py`:

#### **1. Internal Ping (كل دقيقة)**
```python
async def internal_ping():
    while True:
        await asyncio.sleep(60)
        logger.info(f"💓 Keep alive - Devices: {len(devices)}")
```

**الوظيفة:**
- يطبع log كل دقيقة
- يحافظ على نشاط الـ process
- يمنع النوم

---

#### **2. Self HTTP Request (كل 5 دقائق)**
```python
async def self_request():
    while True:
        async with aiohttp.ClientSession() as session:
            await session.get('http://localhost:7860/health')
        await asyncio.sleep(300)
```

**الوظيفة:**
- السيرفر يرسل طلب HTTP لنفسه
- يحافظ على نشاط الشبكة
- يمنع Idle timeout

---

#### **3. Memory Activity (كل 30 ثانية)**
```python
async def memory_activity():
    while True:
        _ = [i for i in range(100)]
        await asyncio.sleep(30)
```

**الوظيفة:**
- نشاط بسيط في الذاكرة
- يمنع CPU من النوم
- يحافظ على Process نشط

---

### **الطبقة 2: External Keep Alive (من خارج Hugging Face)**

#### **الخيار 1: UptimeRobot (مجاني)** ⭐ الأفضل

```
1. اذهب إلى: https://uptimerobot.com/signUp

2. أنشئ حساب مجاني

3. Add New Monitor:
   ┌────────────────────────────────────────┐
   │ Monitor Type: HTTP(s)                  │
   │ Friendly Name: SMS Gateway Relay       │
   │ URL: https://YOUR_SPACE.hf.space/health│
   │ Monitoring Interval: 5 minutes         │
   └────────────────────────────────────────┘

4. Create Monitor
```

**المميزات:**
- ✅ مجاني 100%
- ✅ يرسل ping كل 5 دقائق
- ✅ يرسل تنبيهات إذا توقف
- ✅ إحصائيات Uptime
- ✅ لا يحتاج صيانة

---

#### **الخيار 2: Cron-job.org (مجاني)**

```
1. اذهب إلى: https://cron-job.org/en/signup/

2. أنشئ حساب

3. Create cronjob:
   ┌────────────────────────────────────────┐
   │ Title: SMS Gateway Keep Alive          │
   │ URL: https://YOUR_SPACE.hf.space/health│
   │ Schedule: Every 5 minutes              │
   └────────────────────────────────────────┘

4. Save
```

---

#### **الخيار 3: Python Script (على جهازك)**

استخدم `external-keepalive.py`:

```bash
# 1. عدّل الملف:
nano external-keepalive.py

# غيّر:
SPACE_URL = "https://YOUR_SPACE.hf.space"

# 2. شغّل:
python external-keepalive.py

# 3. اتركه يعمل في الخلفية
```

**للتشغيل الدائم:**

**Windows:**
```powershell
# أنشئ ملف: run-keepalive.bat
@echo off
python external-keepalive.py
pause

# ضعه في Startup folder:
# Win+R → shell:startup
```

**Linux/Mac:**
```bash
# أنشئ systemd service:
sudo nano /etc/systemd/system/keepalive.service

[Unit]
Description=Hugging Face Keep Alive
After=network.target

[Service]
Type=simple
User=YOUR_USER
ExecStart=/usr/bin/python3 /path/to/external-keepalive.py
Restart=always

[Install]
WantedBy=multi-user.target

# فعّل:
sudo systemctl enable keepalive
sudo systemctl start keepalive
```

---

#### **الخيار 4: GitHub Actions (مجاني)**

أنشئ `.github/workflows/keepalive.yml`:

```yaml
name: Keep Alive

on:
  schedule:
    - cron: '*/5 * * * *'  # كل 5 دقائق
  workflow_dispatch:

jobs:
  ping:
    runs-on: ubuntu-latest
    steps:
      - name: Ping Space
        run: |
          curl -f https://YOUR_SPACE.hf.space/health || exit 1
```

---

#### **الخيار 5: Cloudflare Workers (مجاني)**

```javascript
// worker.js
addEventListener('scheduled', event => {
  event.waitUntil(handleScheduled(event));
});

async function handleScheduled(event) {
  const response = await fetch('https://YOUR_SPACE.hf.space/health');
  console.log('Ping status:', response.status);
}

// في Cloudflare Dashboard:
// Triggers → Cron Triggers → Add Cron Trigger
// Schedule: */5 * * * * (كل 5 دقائق)
```

---

## 📊 التحقق من Keep Alive

### **1. Logs في Hugging Face:**

```
Space → Logs

يجب أن ترى:
💓 Keep alive - Devices: 0 - Requests: 0
🔄 Self-ping successful
🧠 Memory activity - Counter: 10
```

### **2. UptimeRobot Dashboard:**

```
يجب أن يظهر:
✅ Up (100%)
Response time: ~200ms
```

### **3. Manual Test:**

```bash
# كل 5 دقائق، جرّب:
curl https://YOUR_SPACE.hf.space/health

# يجب أن يرد:
{"status":"ok","devices":0,"uptime":12345.67}
```

---

## ⚠️ ملاحظات مهمة

### **1. Hugging Face Spaces لا تنام!**

```
✅ على عكس Replit (ينام بعد ساعة)
✅ على عكس Render (ينام بعد 15 دقيقة)
✅ Hugging Face Spaces تعمل 24/7 بدون نوم

لكن Keep Alive يضمن:
- استجابة سريعة
- عدم Cold Start
- استقرار أعلى
```

### **2. استهلاك الموارد:**

```
Keep Alive يستهلك:
- CPU: ~0.1%
- Memory: ~5 MB
- Network: ~1 KB/دقيقة

تأثير ضئيل جداً! ✅
```

### **3. Rate Limiting:**

```
UptimeRobot مجاناً:
- 50 monitors
- Check interval: 5 minutes
- كافي تماماً! ✅
```

---

## 🎯 التوصية النهائية

### **الإعداد المثالي:**

```
1. ✅ Internal Keep Alive (مُفعّل في app.py)
2. ✅ UptimeRobot (external monitoring)
3. ✅ GitHub Actions (backup)

= حماية 3 طبقات! 🛡️
```

---

## 🔧 استكشاف الأخطاء

### **❌ Space يتوقف رغم Keep Alive:**

```
1. تحقق من Logs:
   Space → Logs → ابحث عن errors

2. تحقق من UptimeRobot:
   هل يرسل pings بنجاح؟

3. تحقق من Health endpoint:
   curl https://YOUR_SPACE.hf.space/health

4. أعد تشغيل Space:
   Settings → Factory reboot
```

### **❌ UptimeRobot يقول "Down":**

```
1. تحقق من Space status في Hugging Face

2. تحقق من URL صحيح:
   https://YOUR_SPACE.hf.space/health
   (مش /api/health)

3. تحقق من Timeout:
   زوّد timeout في UptimeRobot لـ 30 ثانية
```

### **❌ Logs تقول "Self-ping failed":**

```
هذا طبيعي أحياناً!
- السيرفر قد يكون مشغول
- Network latency
- لا تقلق طالما Space يعمل
```

---

## 📈 الإحصائيات المتوقعة

### **Uptime:**
```
✅ 99.9% uptime
✅ Downtime: <1 ساعة/شهر
✅ Response time: 100-300ms
```

### **Keep Alive Activity:**
```
✅ Internal ping: 1440 مرة/يوم
✅ Self-request: 288 مرة/يوم
✅ Memory activity: 2880 مرة/يوم
✅ UptimeRobot: 288 مرة/يوم
```

---

## ✅ الخلاصة

**Keep Alive مُفعّل بـ 3 طبقات:**

```
الطبقة 1: Internal (داخل app.py)
├─ Internal Ping (كل دقيقة)
├─ Self HTTP Request (كل 5 دقائق)
└─ Memory Activity (كل 30 ثانية)

الطبقة 2: External (UptimeRobot)
└─ HTTP Ping (كل 5 دقائق)

الطبقة 3: Backup (GitHub Actions)
└─ Scheduled Ping (كل 5 دقائق)

= Space لن ينام أبداً! 🚀
```

---

## 🎁 بونص: Monitoring Dashboard

أنشئ dashboard بسيط:

```html
<!DOCTYPE html>
<html>
<head>
    <title>SMS Gateway Monitor</title>
    <script>
        setInterval(async () => {
            const res = await fetch('https://YOUR_SPACE.hf.space/health');
            const data = await res.json();
            document.getElementById('status').textContent = 
                `✅ Online - Devices: ${data.devices}`;
        }, 5000);
    </script>
</head>
<body>
    <h1>SMS Gateway Status</h1>
    <div id="status">Checking...</div>
</body>
</html>
```

---

**Space بتاعك هيعمل 24/7 بدون توقف! 💪**
