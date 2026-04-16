# 🦆 دليل إعداد DuckDNS - رابط مجاني ثابت

## 🎯 الهدف
الحصول على رابط ثابت مجاني مثل: `http://mysmspay.duckdns.org:8080/api/v1`

---

## 📋 الخطوات الكاملة

### **1️⃣ Port Forwarding في الراوتر**

#### أ) اعرف IP الموبايل:
```
افتح التطبيق → Dashboard
Local IP: 192.168.1.100  ← احفظ هذا الرقم
```

#### ب) ادخل على الراوتر:
```
المتصفح → اكتب أحد هذه العناوين:
- 192.168.1.1
- 192.168.0.1
- 192.168.100.1

Username: admin
Password: admin (أو مكتوب خلف الراوتر)
```

#### ج) أضف Port Forwarding:
```
ابحث عن: Port Forwarding / Virtual Server / NAT

أضف قاعدة:
┌──────────────────────────────────┐
│ Service Name: SMS_Gateway        │
│ External Port: 8080              │
│ Internal IP: 192.168.1.100       │ ← IP الموبايل
│ Internal Port: 8080              │
│ Protocol: TCP أو Both            │
│ Status: Enable ✓                 │
└──────────────────────────────────┘

احفظ → أعد تشغيل الراوتر
```

**أمثلة حسب نوع الراوتر:**

**TP-Link:**
```
Forwarding → Virtual Servers → Add New
```

**D-Link:**
```
Advanced → Port Forwarding
```

**Huawei:**
```
Security → NAT → Port Mapping
```

**ZTE:**
```
Application → Port Forwarding
```

---

### **2️⃣ التسجيل في DuckDNS**

#### أ) افتح الموقع:
```
https://www.duckdns.org
```

#### ب) سجّل دخول:
```
اضغط على أي أيقونة:
- Google
- GitHub
- Reddit
- Twitter

لا يحتاج إنشاء حساب جديد!
```

#### ج) احصل على Token:
```
بعد تسجيل الدخول، هتلاقي:
┌──────────────────────────────────────────┐
│ token: a1b2c3d4-e5f6-7890-abcd-ef123456  │
└──────────────────────────────────────────┘

احفظ هذا Token!
```

---

### **3️⃣ إنشاء Domain مجاني**

#### أ) اعرف Public IP بتاعك:
```
افتح: https://whatismyipaddress.com
أو: https://ipinfo.io/ip

مثال: 41.234.56.78  ← احفظ هذا الرقم
```

#### ب) أنشئ Domain:
```
في DuckDNS:

1. اكتب اسم Domain:
   ┌──────────────┐
   │ mysmspay     │.duckdns.org
   └──────────────┘

2. اكتب Public IP:
   ┌──────────────┐
   │ 41.234.56.78 │
   └──────────────┘

3. اضغط: Add Domain

✅ تم! الآن لديك:
   http://mysmspay.duckdns.org
```

---

### **4️⃣ التحديث التلقائي للـ IP**

لأن Public IP قد يتغير، نحتاج تحديثه تلقائياً:

#### **الطريقة 1: Windows (PowerShell)**

**أ) حدّث الملف:**
```powershell
# افتح: update_duckdns.ps1
# غيّر هذه الأسطر:

$DOMAIN = "mysmspay"           # ← اسم Domain بتاعك
$TOKEN = "a1b2c3d4-e5f6..."    # ← Token من DuckDNS
```

**ب) شغّل السكريبت:**
```powershell
# PowerShell كـ Administrator:
powershell -ExecutionPolicy Bypass -File update_duckdns.ps1
```

**ج) شغّله تلقائياً عند بدء Windows:**
```
1. اضغط Win+R
2. اكتب: shell:startup
3. انسخ shortcut للملف update_duckdns.ps1
```

---

#### **الطريقة 2: Android (Tasker App)**

**أ) حمّل Tasker:**
```
Google Play Store → Tasker
```

**ب) أنشئ Task:**
```
1. New Task: "Update DuckDNS"
2. Add Action: Net → HTTP Get
3. URL: https://www.duckdns.org/update?domains=mysmspay&token=YOUR_TOKEN
4. Save
```

**ج) أنشئ Profile:**
```
1. New Profile: Time → Every 5 minutes
2. Link to Task: "Update DuckDNS"
3. Enable
```

---

#### **الطريقة 3: Linux/Mac (Cron)**

```bash
# افتح crontab:
crontab -e

# أضف هذا السطر:
*/5 * * * * curl "https://www.duckdns.org/update?domains=mysmspay&token=YOUR_TOKEN&ip="

# احفظ واخرج
```

---

#### **الطريقة 4: الراوتر نفسه (الأفضل!)**

بعض الراوترات تدعم DDNS مباشرة:

```
1. ادخل على الراوتر
2. ابحث عن: DDNS / Dynamic DNS
3. اختر: Custom أو DuckDNS
4. املأ:
   - Hostname: mysmspay.duckdns.org
   - Username: (اتركه فارغ)
   - Password: YOUR_TOKEN
   - Update URL: https://www.duckdns.org/update?domains=mysmspay&token=YOUR_TOKEN&ip=%i

5. Enable → Save
```

---

### **5️⃣ اختبار الاتصال**

#### أ) من داخل الشبكة:
```bash
curl http://192.168.1.100:8080/api/v1/health
```

#### ب) من خارج الشبكة:
```bash
# استخدم موبايل بـ 4G/5G (بدون WiFi):
curl http://mysmspay.duckdns.org:8080/api/v1/health
```

#### ج) من المتصفح:
```
http://mysmspay.duckdns.org:8080/api/v1/health
```

**النتيجة المتوقعة:**
```json
{
  "status": "ok",
  "timestamp": "2026-04-16T10:30:00Z"
}
```

---

## 🎯 الرابط النهائي

بعد إتمام الخطوات، استخدم هذا الرابط في موقعك:

```
Base URL: http://mysmspay.duckdns.org:8080/api/v1
API Key: [من التطبيق → الإعدادات]
```

**مثال:**
```javascript
const BASE_URL = "http://mysmspay.duckdns.org:8080/api/v1";
const API_KEY = "abc123xyz...";

fetch(`${BASE_URL}/transactions`, {
  method: "POST",
  headers: {
    "Authorization": `Bearer ${API_KEY}`,
    "Content-Type": "application/json"
  },
  body: JSON.stringify({
    id: "order-001",
    amount: 500,
    phoneNumber: "01012345678",
    walletType: "VODAFONE_CASH"
  })
});
```

---

## ⚠️ ملاحظات مهمة

### 1. **الأمان:**
```
⚠️ HTTP غير آمن!

للحصول على HTTPS مجاني:
- استخدم Cloudflare Tunnel (مجاني)
- أو Ngrok (محدود مجاناً)
- أو Let's Encrypt + Reverse Proxy
```

### 2. **البورت 8080:**
```
بعض مزودي الإنترنت يحجبون البورت 8080

الحل:
- جرّب بورت آخر: 8081, 8082, 8888
- أو استخدم بورت 80 (يحتاج صلاحيات root)
```

### 3. **Firewall:**
```
تأكد أن Firewall الراوتر لا يحجب البورت:
Security → Firewall → Allow Port 8080
```

### 4. **IP ثابت للموبايل:**
```
لمنع تغيير IP الموبايل:
1. ادخل على الراوتر
2. DHCP → Address Reservation
3. أضف MAC Address الموبايل
4. احجز IP: 192.168.1.100
```

---

## 🔍 استكشاف الأخطاء

### ❌ "لا يمكن الوصول للرابط من الخارج"

**الحلول:**
```
1. تأكد Port Forwarding مفعّل في الراوتر
2. تأكد الموبايل شغال والتطبيق يعمل
3. تأكد Public IP صحيح في DuckDNS
4. جرّب موقع: https://www.yougetsignal.com/tools/open-ports/
   - اكتب Public IP والبورت 8080
   - لو مفتوح → Port Forwarding شغال ✓
```

### ❌ "Domain لا يعمل"

**الحلول:**
```
1. تأكد DuckDNS محدّث (اضغط Update في الموقع)
2. انتظر 5 دقائق (DNS propagation)
3. جرّب: ping mysmspay.duckdns.org
   - لو يرد بـ Public IP → Domain شغال ✓
```

### ❌ "IP يتغير باستمرار"

**الحلول:**
```
1. شغّل update_duckdns.ps1 باستمرار
2. أو فعّل DDNS في الراوتر نفسه
3. أو اطلب Static IP من مزود الإنترنت (قد يكون مدفوع)
```

---

## 💰 التكلفة

```
✅ DuckDNS: مجاني 100%
✅ Port Forwarding: مجاني
✅ Domain: مجاني
✅ التحديث التلقائي: مجاني

المجموع: 0 جنيه! 🎉
```

---

## 🚀 بدائل مدفوعة (اختياري)

إذا أردت حل أفضل:

### **1. Cloudflare Tunnel (مجاني!):**
```
- HTTPS مجاني
- لا يحتاج Port Forwarding
- Domain مخصص
- سرعة عالية

https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/
```

### **2. Ngrok (محدود مجاناً):**
```
- سهل جداً
- HTTPS تلقائي
- لكن الرابط يتغير (في المجاني)

الخطة المدفوعة: $8/شهر
```

### **3. Static IP من مزود الإنترنت:**
```
- IP ثابت لا يتغير أبداً
- لا يحتاج DDNS

التكلفة: 50-200 جنيه/شهر (حسب المزود)
```

---

## ✅ الخلاصة

**للحصول على رابط ثابت مجاني:**

1. ✅ Port Forwarding في الراوتر
2. ✅ Domain من DuckDNS
3. ✅ تحديث تلقائي للـ IP
4. ✅ استخدم: `http://mysmspay.duckdns.org:8080/api/v1`

**مجاني 100% وثابت! 🎉**
