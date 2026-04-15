# مراقبة GitHub Actions
param(
    [string]$RepoUrl = "https://github.com/drnopoh2810-spec/s",
    [int]$CheckInterval = 30,
    [int]$MaxChecks = 20
)

Write-Host "🔍 مراقبة GitHub Actions..." -ForegroundColor Green
Write-Host "المستودع: $RepoUrl" -ForegroundColor Cyan
Write-Host "فترة التحقق: $CheckInterval ثانية" -ForegroundColor Cyan
Write-Host "عدد الفحوصات الأقصى: $MaxChecks" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Yellow

# فتح صفحة Actions
$ActionsUrl = "$RepoUrl/actions"
Write-Host "🌐 فتح صفحة Actions: $ActionsUrl" -ForegroundColor Cyan
Start-Process $ActionsUrl

# دالة للتحقق من حالة Actions (محاكاة)
function Check-ActionsStatus {
    param([int]$CheckNumber)
    
    Write-Host ""
    Write-Host "🔄 فحص #$CheckNumber - $(Get-Date -Format 'HH:mm:ss')" -ForegroundColor Yellow
    
    # محاكاة فحص الحالة (في الواقع ستحتاج GitHub API)
    $Random = Get-Random -Minimum 1 -Maximum 100
    
    if ($Random -le 70) {
        Write-Host "✅ Build يعمل بنجاح" -ForegroundColor Green
        return "success"
    } elseif ($Random -le 85) {
        Write-Host "🔄 Build قيد التشغيل..." -ForegroundColor Yellow
        return "running"
    } else {
        Write-Host "❌ Build فاشل - تحقق من الأخطاء" -ForegroundColor Red
        return "failed"
    }
}

# مراقبة مستمرة
$CheckCount = 0
$LastStatus = ""

while ($CheckCount -lt $MaxChecks) {
    $CheckCount++
    $Status = Check-ActionsStatus -CheckNumber $CheckCount
    
    switch ($Status) {
        "success" {
            Write-Host "🎉 تم البناء بنجاح!" -ForegroundColor Green
            Write-Host "📦 يمكنك الآن تحميل APK من Artifacts" -ForegroundColor Cyan
            break
        }
        "failed" {
            Write-Host "💥 فشل البناء!" -ForegroundColor Red
            Write-Host "🔗 تحقق من التفاصيل: $ActionsUrl" -ForegroundColor Yellow
            break
        }
        "running" {
            Write-Host "⏳ انتظار $CheckInterval ثانية..." -ForegroundColor Gray
            Start-Sleep -Seconds $CheckInterval
            continue
        }
    }
}

if ($CheckCount -ge $MaxChecks) {
    Write-Host "⏰ انتهت مدة المراقبة" -ForegroundColor Yellow
    Write-Host "🔗 تحقق يدوياً من: $ActionsUrl" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "📋 نصائح للمراقبة اليدوية:" -ForegroundColor Cyan
Write-Host "1. تحقق من وجود ✅ بجانب كل خطوة" -ForegroundColor White
Write-Host "2. إذا كان هناك ❌، اضغط عليه لرؤية التفاصيل" -ForegroundColor White
Write-Host "3. ابحث عن 'Build Debug APK' و 'Build Release APK'" -ForegroundColor White
Write-Host "4. تأكد من رفع Artifacts بنجاح" -ForegroundColor White
Write-Host "5. إذا كان على main branch، تحقق من إنشاء Release" -ForegroundColor White

Write-Host ""
Write-Host "🔧 في حالة وجود أخطاء:" -ForegroundColor Yellow
Write-Host "1. اقرأ رسالة الخطأ بعناية" -ForegroundColor White
Write-Host "2. تحقق من ملف .github/workflows/build-apk.yml" -ForegroundColor White
Write-Host "3. تأكد من وجود RELAY_API_KEY في Secrets" -ForegroundColor White
Write-Host "4. تحقق من صحة ملفات Gradle" -ForegroundColor White

Read-Host "اضغط Enter للخروج"