@echo off
echo 🚀 رفع التحديثات إلى GitHub...
echo ================================

REM التحقق من وجود Git
where git >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ❌ Git غير مثبت. يرجى تثبيت Git أولاً من: https://git-scm.com/download/win
    pause
    exit /b 1
)

echo ✅ Git موجود

REM إضافة جميع الملفات المعدلة
echo 📁 إضافة الملفات المعدلة...
git add .

REM التحقق من وجود تغييرات
git diff --cached --quiet
if %ERRORLEVEL% EQU 0 (
    echo ℹ️  لا توجد تغييرات للرفع
    pause
    exit /b 0
)

REM عرض الملفات التي ستُرفع
echo 📋 الملفات التي ستُرفع:
git diff --cached --name-only

echo.
echo 💾 إنشاء commit...
git commit -m "🔧 إصلاح مشاكل استقرار الاتصال مع WebSocket

✅ تحسينات RelayClient:
- زيادة محاولات إعادة الاتصال إلى 50
- Heartbeat محسّن كل 20 ثانية  
- إعادة اتصال تلقائي ذكي
- مراقبة شبكة محسّنة
- WakeLock ذكي لمنع النوم

✅ إضافة ConnectionMonitor:
- مراقبة مستمرة كل 30 ثانية
- إعادة اتصال قسرية كل 5 دقائق
- مراقبة حالة الخدمة

✅ تحسين PaymentGatewayService:
- إدارة أفضل للخدمات
- مراقبة حالة RelayClient
- رسائل سجل محسّنة

✅ ملفات توثيق جديدة:
- CONNECTION_STABILITY_GUIDE.md
- CONNECTION_FIXES_SUMMARY.md  
- test_connection_stability.sh

🎯 النتيجة: اتصال مستقر 24/7 بدون انقطاع!"

if %ERRORLEVEL% NEQ 0 (
    echo ❌ فشل في إنشاء commit
    pause
    exit /b 1
)

echo ✅ تم إنشاء commit بنجاح

echo 🌐 رفع التحديثات إلى GitHub...
git push origin main

if %ERRORLEVEL% NEQ 0 (
    echo ❌ فشل في رفع التحديثات
    echo 💡 تأكد من:
    echo    1. الاتصال بالإنترنت
    echo    2. صلاحيات الوصول للمستودع
    echo    3. أن branch الحالي هو main
    pause
    exit /b 1
)

echo ✅ تم رفع التحديثات بنجاح!
echo 🔗 تحقق من GitHub Actions: https://github.com/drnopoh2810-spec/s/actions

echo.
echo 🎉 تم الانتهاء! سيتم فتح صفحة Actions...
start https://github.com/drnopoh2810-spec/s/actions

pause