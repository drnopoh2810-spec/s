# دليل إعداد Git - Git Setup Guide

## المشكلة - The Problem
Git مثبت على النظام ولكن غير متاح في PowerShell بسبب عدم وجوده في متغير البيئة PATH.

Git is installed on the system but not available in PowerShell because it's not in the PATH environment variable.

## الحل السريع - Quick Solution

### 1. تشغيل ملف الإعداد المحلي - Run Local Setup File
```powershell
.\setup_git_path.ps1
```

### 2. أو إضافة المسار يدوياً - Or Add Path Manually
```powershell
$env:PATH += ";C:\Program Files\Git\bin"
```

## الحل الدائم - Permanent Solution

### الطريقة الأولى: عبر إعدادات النظام - Method 1: System Settings
1. افتح إعدادات النظام (System Settings)
2. اذهب إلى "متغيرات البيئة" (Environment Variables)
3. أضف `C:\Program Files\Git\bin` إلى متغير PATH
4. أعد تشغيل PowerShell

### الطريقة الثانية: عبر PowerShell Profile - Method 2: PowerShell Profile
```powershell
# إنشاء ملف profile إذا لم يكن موجوداً
if (!(Test-Path $PROFILE)) {
    New-Item -ItemType File -Path $PROFILE -Force
}

# إضافة إعداد Git إلى profile
Add-Content $PROFILE '
# Git Path Setup
if (Test-Path "C:\Program Files\Git\bin\git.exe") {
    $env:PATH += ";C:\Program Files\Git\bin"
}
'
```

## التحقق من العمل - Verification
```powershell
git --version
git status
```

## أوامر Git الأساسية - Basic Git Commands
```powershell
# فحص الحالة
git status

# عرض السجل
git log --oneline -10

# عرض الفروع
git branch -a

# عرض المستودعات البعيدة
git remote -v

# سحب التحديثات
git pull origin main

# رفع التغييرات
git add .
git commit -m "رسالة التحديث"
git push origin main
```

## ملاحظات مهمة - Important Notes
- تأكد من أن Git مثبت في المسار الافتراضي: `C:\Program Files\Git\`
- إذا كان Git مثبت في مسار مختلف، قم بتعديل المسار في الأوامر أعلاه
- بعد إضافة المسار إلى متغيرات البيئة، أعد تشغيل PowerShell

---

✅ **Git يعمل الآن بشكل صحيح في هذا المشروع!**