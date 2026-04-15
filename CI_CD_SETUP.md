# إعداد CI/CD للمشروع

## نظرة عامة
هذا المشروع يستخدم GitHub Actions لبناء APK تلقائياً عند كل push أو pull request.

## المتطلبات

### 1. إضافة GitHub Secret
يجب إضافة `RELAY_API_KEY` كـ Secret في GitHub:

1. اذهب إلى Settings → Secrets and variables → Actions
2. اضغط على "New repository secret"
3. الاسم: `RELAY_API_KEY`
4. القيمة: مفتاح API الخاص بـ Relay Server
5. احفظ

### 2. التأكد من الأذونات
تأكد من أن GitHub Actions لديه صلاحية الكتابة:
- Settings → Actions → General → Workflow permissions
- اختر "Read and write permissions"
- فعّل "Allow GitHub Actions to create and approve pull requests"

## كيف يعمل Workflow

### Build Job
1. يقوم بـ checkout للكود
2. ينشئ ملف `local.properties` من GitHub Secret
3. يثبت JDK 17
4. يبني Debug APK
5. يبني Release APK (unsigned)
6. يرفع الملفات كـ artifacts

### Release Job
1. يعمل فقط على branch `main` أو `master`
2. يبني APK مرة أخرى
3. ينشئ GitHub Release تلقائياً
4. يرفع APK files للـ release

## الملفات المهمة

### `.github/workflows/build-apk.yml`
ملف الـ workflow الرئيسي

### `app/build.gradle.kts`
يقرأ `RELAY_API_KEY` من:
1. متغيرات البيئة (للـ CI/CD)
2. ملف `local.properties` (للتطوير المحلي)
3. قيمة افتراضية إذا لم يكن أي منهما موجوداً

## التطوير المحلي

أنشئ ملف `local.properties` في جذر المشروع:

```properties
RELAY_API_KEY=your_api_key_here
```

**ملاحظة:** هذا الملف مُستثنى من Git ولن يتم رفعه.

## استكشاف الأخطاء

### خطأ: "RELAY_API_KEY not found"
- تأكد من إضافة Secret في GitHub
- تأكد من كتابة الاسم بشكل صحيح: `RELAY_API_KEY`

### خطأ: "Permission denied"
- تأكد من أذونات GitHub Actions (انظر المتطلبات أعلاه)

### فشل البناء
- تحقق من logs في GitHub Actions
- تأكد من أن جميع dependencies موجودة
- تأكد من توافق إصدارات Gradle و JDK

## تحميل APK

### من Artifacts (لكل build)
1. اذهب إلى Actions tab
2. اختر workflow run
3. قم بتحميل APK من Artifacts section

### من Releases (للإصدارات الرسمية)
1. اذهب إلى Releases tab
2. اختر أحدث release
3. قم بتحميل APK من Assets

## الإصدارات التلقائية

عند push إلى `main` أو `master`:
- يتم إنشاء release تلقائياً
- Tag format: `v{version}-{run_number}`
- مثال: `v1.0-42`

## الأمان

- ✅ `local.properties` مُستثنى من Git
- ✅ API Keys محفوظة في GitHub Secrets
- ✅ لا يتم طباعة القيم الحساسة في logs
- ✅ Artifacts تُحذف تلقائياً بعد 30 يوم

## الدعم

إذا واجهت أي مشاكل:
1. تحقق من logs في GitHub Actions
2. راجع هذا الملف
3. تأكد من جميع المتطلبات
