# دليل البدء السريع

## 🚀 للمستخدمين (تحميل APK جاهز)

### الخطوة 1: تحميل APK
اذهب إلى [Releases](https://github.com/drnopoh2810-spec/s/releases/latest) وحمّل أحدث APK

### الخطوة 2: التثبيت
1. افتح ملف APK على هاتف Android
2. اسمح بالتثبيت من مصادر غير معروفة (إذا طُلب منك)
3. اضغط "تثبيت"

### الخطوة 3: الإعداد
1. افتح التطبيق
2. امنح صلاحيات قراءة SMS
3. احصل على API Key من الواجهة
4. اضبط Relay Server URL
5. اضغط "Start Service"

### الخطوة 4: الاستخدام
استخدم API من موقعك:
```bash
curl -X POST http://your-relay-server/api/v1/transactions \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "tx-123",
    "amount": 500.00,
    "phoneNumber": "01012345678",
    "walletType": "VODAFONE_CASH"
  }'
```

---

## 👨‍💻 للمطورين (بناء من المصدر)

### المتطلبات
- Android Studio Hedgehog أو أحدث
- JDK 17
- Android SDK 34

### الخطوة 1: استنساخ المشروع
```bash
git clone https://github.com/drnopoh2810-spec/s.git
cd s
```

### الخطوة 2: إعداد API Key
```bash
# انسخ الملف المثال
cp local.properties.example local.properties

# عدّل الملف وأضف API key الخاص بك
nano local.properties
```

### الخطوة 3: البناء
```bash
# بناء Debug APK
./gradlew assembleDebug

# بناء Release APK
./gradlew assembleRelease

# تشغيل الاختبارات
./gradlew test
```

### الخطوة 4: التثبيت على الهاتف
```bash
# عبر ADB
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔧 إعداد CI/CD (لمالكي المشروع)

### الخطوة 1: إضافة GitHub Secret
1. اذهب إلى `Settings` → `Secrets and variables` → `Actions`
2. اضغط `New repository secret`
3. الاسم: `RELAY_API_KEY`
4. القيمة: مفتاح API الخاص بك
5. احفظ

### الخطوة 2: ضبط الأذونات
1. اذهب إلى `Settings` → `Actions` → `General`
2. في `Workflow permissions`:
   - اختر "Read and write permissions"
   - فعّل "Allow GitHub Actions to create and approve pull requests"
3. احفظ

### الخطوة 3: تشغيل Workflow
```bash
# Push إلى main/master لتشغيل تلقائي
git push origin main

# أو شغّل يدوياً من Actions tab
```

---

## 📚 موارد إضافية

- [README.md](README.md) - نظرة عامة على المشروع
- [API_DOCUMENTATION.md](API_DOCUMENTATION.md) - توثيق API كامل
- [CI_CD_SETUP.md](CI_CD_SETUP.md) - دليل CI/CD مفصل
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md) - حل المشاكل الشائعة
- [FIXES_APPLIED.md](FIXES_APPLIED.md) - الإصلاحات الأخيرة

---

## ❓ الأسئلة الشائعة

### كيف أحصل على API Key؟
افتح التطبيق → الإعدادات → انسخ API Key

### كيف أغير Relay Server URL؟
افتح التطبيق → الإعدادات → Relay Server URL → احفظ

### التطبيق لا يقرأ الرسائل؟
1. تحقق من صلاحيات SMS
2. أوقف Battery Optimization
3. تأكد من أن الخدمة تعمل

### كيف أختبر API؟
استخدم Postman collection: `SMS_Payment_Gateway.postman_collection.json`

---

## 🆘 الدعم

إذا واجهت مشاكل:
1. راجع [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
2. تحقق من [Issues](https://github.com/drnopoh2810-spec/s/issues)
3. افتح issue جديد مع تفاصيل المشكلة
