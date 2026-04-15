# تعليمات رفع التحديثات إلى GitHub

## 🚀 طرق رفع التحديثات

### الطريقة 1: استخدام ملف Batch (Windows)
```cmd
# تشغيل الملف
upload_to_github.bat
```

### الطريقة 2: استخدام PowerShell
```powershell
# تشغيل الملف
.\upload_to_github.ps1
```

### الطريقة 3: Git يدوياً
```bash
# إضافة جميع الملفات
git add .

# إنشاء commit
git commit -m "🔧 إصلاح مشاكل استقرار الاتصال مع WebSocket"

# رفع التحديثات
git push origin main
```

---

## 📋 الملفات المعدلة/الجديدة

### ملفات الكود:
- `app/src/main/java/com/sms/paymentgateway/services/RelayClient.kt` ✏️ معدل
- `app/src/main/java/com/sms/paymentgateway/services/PaymentGatewayService.kt` ✏️ معدل
- `app/src/main/java/com/sms/paymentgateway/services/ConnectionMonitor.kt` 🆕 جديد
- `app/build.gradle.kts` ✏️ معدل
- `app/src/main/AndroidManifest.xml` ✏️ معدل

### ملفات CI/CD:
- `.github/workflows/build-apk.yml` ✏️ معدل
- `.github/ISSUE_TEMPLATE/ci_cd_issue.md` 🆕 جديد
- `.github/ISSUE_TEMPLATE/feature_request.md` 🆕 جديد
- `.github/ISSUE_TEMPLATE/bug_report.md` 🆕 جديد
- `.github/pull_request_template.md` 🆕 جديد

### ملفات التوثيق:
- `README.md` ✏️ معدل
- `CONNECTION_STABILITY_GUIDE.md` 🆕 جديد
- `CONNECTION_FIXES_SUMMARY.md` 🆕 جديد
- `CI_CD_SETUP.md` 🆕 جديد
- `QUICK_START.md` 🆕 جديد
- `FIXES_APPLIED.md` 🆕 جديد
- `local.properties.example` 🆕 جديد

### ملفات الاختبار:
- `test_connection_stability.sh` 🆕 جديد

### ملفات الرفع:
- `upload_to_github.bat` 🆕 جديد
- `upload_to_github.ps1` 🆕 جديد
- `UPLOAD_INSTRUCTIONS.md` 🆕 جديد

---

## 🔍 مراقبة GitHub Actions

بعد رفع التحديثات، تحقق من:

### 1. صفحة Actions:
```
https://github.com/drnopoh2810-spec/s/actions
```

### 2. آخر Workflow Run:
- تأكد من أن Build يعمل بنجاح ✅
- تحقق من عدم وجود أخطاء ❌
- راقب مرحلة Release (إذا كان على main branch)

### 3. علامات النجاح:
- ✅ Build Debug APK
- ✅ Build Release APK  
- ✅ Upload Artifacts
- ✅ Create Release (على main branch)

### 4. في حالة الفشل:
1. اضغط على الـ workflow الفاشل
2. اقرأ logs الخطأ
3. أصلح المشكلة
4. ارفع التحديث مرة أخرى

---

## 🛠️ استكشاف الأخطاء

### خطأ: Git غير موجود
```bash
# تثبيت Git على Windows
# حمّل من: https://git-scm.com/download/win
```

### خطأ: Permission denied
```bash
# تأكد من تسجيل الدخول إلى Git
git config --global user.name "اسمك"
git config --global user.email "بريدك@example.com"

# أو استخدم GitHub CLI
gh auth login
```

### خطأ: Branch protection
```bash
# إذا كان main محمي، أنشئ branch جديد
git checkout -b connection-fixes
git push origin connection-fixes
# ثم أنشئ Pull Request
```

### خطأ: Merge conflicts
```bash
# اسحب آخر التحديثات أولاً
git pull origin main
# حل التعارضات يدوياً
# ثم ارفع مرة أخرى
```

---

## 📊 متابعة النتائج

### بعد رفع التحديثات:

1. **تحقق من Build:**
   - اذهب إلى Actions tab
   - تأكد من نجاح Build APK

2. **حمّل APK للاختبار:**
   - من Artifacts في آخر workflow
   - أو من Releases إذا تم إنشاء release

3. **اختبر الاتصال:**
   - ثبّت APK الجديد
   - راقب logs الاتصال
   - استخدم `test_connection_stability.sh`

4. **راقب الاستقرار:**
   - اترك التطبيق يعمل لساعات
   - تحقق من عدم انقطاع الاتصال
   - راجع CONNECTION_STABILITY_GUIDE.md

---

## 🎯 الخطوات التالية

بعد رفع التحديثات بنجاح:

1. ✅ تحقق من نجاح GitHub Actions
2. ✅ حمّل وثبّت APK الجديد  
3. ✅ اختبر استقرار الاتصال
4. ✅ راجع دليل الاستقرار للمستخدمين
5. ✅ وثّق أي مشاكل إضافية

**الهدف: اتصال مستقر 24/7 بدون انقطاع!** 🚀