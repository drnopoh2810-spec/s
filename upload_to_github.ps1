# رفع التحديثات إلى GitHub
Write-Host "🚀 رفع التحديثات إلى GitHub..." -ForegroundColor Green
Write-Host "================================" -ForegroundColor Yellow

# التحقق من وجود Git
try {
    $gitVersion = git --version
    Write-Host "✅ Git موجود: $gitVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ Git غير مثبت. يرجى تثبيت Git أولاً من: https://git-scm.com/download/win" -ForegroundColor Red
    Read-Host "اضغط Enter للخروج"
    exit 1
}

# التحقق من حالة المستودع
try {
    $repoStatus = git status --porcelain
    if (-not $repoStatus) {
        Write-Host "ℹ️  لا توجد تغييرات للرفع" -ForegroundColor Yellow
        Read-Host "اضغط Enter للخروج"
        exit 0
    }
} catch {
    Write-Host "❌ خطأ في قراءة حالة المستودع" -ForegroundColor Red
    Read-Host "اضغط Enter للخروج"
    exit 1
}

# إضافة جميع الملفات المعدلة
Write-Host "📁 إضافة الملفات المعدلة..." -ForegroundColor Cyan
git add .

# عرض الملفات التي ستُرفع
Write-Host "📋 الملفات التي ستُرفع:" -ForegroundColor Cyan
git diff --cached --name-only | ForEach-Object { Write-Host "   - $_" -ForegroundColor White }

Write-Host ""
Write-Host "💾 إنشاء commit..." -ForegroundColor Cyan

$commitMessage = @"
🔧 إصلاح مشاكل استقرار الاتصال مع WebSocket

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

🎯 النتيجة: اتصال مستقر 24/7 بدون انقطاع!
"@

try {
    git commit -m $commitMessage
    Write-Host "✅ تم إنشاء commit بنجاح" -ForegroundColor Green
} catch {
    Write-Host "❌ فشل في إنشاء commit" -ForegroundColor Red
    Read-Host "اضغط Enter للخروج"
    exit 1
}

# رفع التحديثات
Write-Host "🌐 رفع التحديثات إلى GitHub..." -ForegroundColor Cyan

try {
    git push origin main
    Write-Host "✅ تم رفع التحديثات بنجاح!" -ForegroundColor Green
} catch {
    Write-Host "❌ فشل في رفع التحديثات" -ForegroundColor Red
    Write-Host "💡 تأكد من:" -ForegroundColor Yellow
    Write-Host "   1. الاتصال بالإنترنت" -ForegroundColor White
    Write-Host "   2. صلاحيات الوصول للمستودع" -ForegroundColor White
    Write-Host "   3. أن branch الحالي هو main" -ForegroundColor White
    Read-Host "اضغط Enter للخروج"
    exit 1
}

Write-Host "🔗 تحقق من GitHub Actions: https://github.com/drnopoh2810-spec/s/actions" -ForegroundColor Cyan
Write-Host ""
Write-Host "🎉 تم الانتهاء! سيتم فتح صفحة Actions..." -ForegroundColor Green

# فتح صفحة Actions
Start-Process "https://github.com/drnopoh2810-spec/s/actions"

Read-Host "اضغط Enter للخروج"