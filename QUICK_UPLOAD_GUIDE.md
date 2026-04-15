# دليل الرفع السريع - Quick Upload Guide

## 🚀 خطوات الرفع (3 دقائق)

### 1. فحص الملفات (30 ثانية)
```powershell
# تشغيل فحص سريع
.\final_check_before_upload.ps1
```

### 2. رفع التحديثات (1 دقيقة)
```powershell
# الطريقة الأولى: PowerShell (مفضل)
.\upload_to_github.ps1

# الطريقة الثانية: Batch
upload_to_github.bat

# الطريقة الثالثة: Git يدوياً
git add .
git commit -m "🔧 إصلاح مشاكل استقرار الاتصال مع WebSocket"
git push origin main
```

### 3. مراقبة GitHub Actions (1-2 دقيقة)
```powershell
# مراقبة تلقائية
.\monitor_github_actions.ps1

# أو افتح الرابط يدوياً
# https://github.com/drnopoh2810-spec/s/actions
```

---

## ✅ علامات النجاح

### في Terminal/PowerShell:
- ✅ `تم رفع التحديثات بنجاح!`
- ✅ فتح صفحة Actions تلقائياً

### في GitHub Actions:
- ✅ Build Debug APK (أخضر)
- ✅ Build Release APK (أخضر)
- ✅ Upload Artifacts (أخضر)
- ✅ Create Release (إذا كان على main branch)

---

## ❌ في حالة الأخطاء

### خطأ Git:
```bash
# تثبيت Git
# حمّل من: https://git-scm.com/download/win
```

### خطأ Permission:
```bash
# تسجيل دخول Git
git config --global user.name "اسمك"
git config --global user.email "بريدك@example.com"
```

### خطأ Build في Actions:
1. اضغط على الـ workflow الفاشل
2. اقرأ رسالة الخطأ
3. تحقق من RELAY_API_KEY في Secrets
4. أصلح المشكلة وارفع مرة أخرى

---

## 🎯 بعد الرفع الناجح

### 1. تحميل APK:
- من Actions → Artifacts
- أو من Releases (إذا تم إنشاؤه)

### 2. اختبار الاتصال:
```bash
# على Linux/Mac
./test_connection_stability.sh

# مراقبة logs يدوياً
adb logcat | grep "RelayClient"
```

### 3. التحقق من الاستقرار:
- ثبّت APK الجديد
- راقب الاتصال لساعات
- تأكد من عدم الانقطاع

---

## 📞 الدعم السريع

### مشكلة في الرفع؟
1. تشغيل `.\check_files_status.ps1`
2. تأكد من وجود جميع الملفات
3. أعد المحاولة

### مشكلة في Actions؟
1. تحقق من RELAY_API_KEY في GitHub Secrets
2. تأكد من أذونات GitHub Actions
3. راجع CI_CD_SETUP.md

### مشكلة في الاتصال؟
1. راجع CONNECTION_STABILITY_GUIDE.md
2. تأكد من إعدادات الهاتف
3. استخدم أدوات الاختبار

---

## 🎉 النتيجة المتوقعة

**اتصال مستقر 24/7 مع WebSocket بدون انقطاع!**

بعد رفع هذه التحديثات، لن تحتاج لفتح الهاتف وإعادة الاتصال يدوياً مرة أخرى.

---

## 🔗 روابط سريعة

- 📁 [المستودع](https://github.com/drnopoh2810-spec/s)
- 🔄 [Actions](https://github.com/drnopoh2810-spec/s/actions)  
- 📦 [Releases](https://github.com/drnopoh2810-spec/s/releases)
- 📚 [دليل الاستقرار](CONNECTION_STABILITY_GUIDE.md)
- 🔧 [إعداد CI/CD](CI_CD_SETUP.md)