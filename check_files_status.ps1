# فحص حالة الملفات المعدلة
Write-Host "📁 فحص حالة الملفات..." -ForegroundColor Green
Write-Host "=========================" -ForegroundColor Yellow

# قائمة الملفات المهمة المعدلة
$ModifiedFiles = @(
    "app/src/main/java/com/sms/paymentgateway/services/RelayClient.kt",
    "app/src/main/java/com/sms/paymentgateway/services/PaymentGatewayService.kt", 
    "app/build.gradle.kts",
    ".github/workflows/build-apk.yml",
    "app/src/main/AndroidManifest.xml"
)

# قائمة الملفات الجديدة
$NewFiles = @(
    "app/src/main/java/com/sms/paymentgateway/services/ConnectionMonitor.kt",
    "CONNECTION_STABILITY_GUIDE.md",
    "CONNECTION_FIXES_SUMMARY.md",
    "CI_CD_SETUP.md",
    "QUICK_START.md",
    "FIXES_APPLIED.md",
    "local.properties.example",
    "test_connection_stability.sh",
    ".github/ISSUE_TEMPLATE/ci_cd_issue.md",
    ".github/ISSUE_TEMPLATE/feature_request.md", 
    ".github/ISSUE_TEMPLATE/bug_report.md",
    ".github/pull_request_template.md"
)

Write-Host "✏️  الملفات المعدلة:" -ForegroundColor Cyan
foreach ($file in $ModifiedFiles) {
    if (Test-Path $file) {
        Write-Host "   ✅ $file" -ForegroundColor Green
    } else {
        Write-Host "   ❌ $file (غير موجود)" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "🆕 الملفات الجديدة:" -ForegroundColor Cyan
foreach ($file in $NewFiles) {
    if (Test-Path $file) {
        Write-Host "   ✅ $file" -ForegroundColor Green
    } else {
        Write-Host "   ❌ $file (غير موجود)" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "📊 إحصائيات:" -ForegroundColor Yellow
$ExistingModified = ($ModifiedFiles | Where-Object { Test-Path $_ }).Count
$ExistingNew = ($NewFiles | Where-Object { Test-Path $_ }).Count
$TotalModified = $ModifiedFiles.Count
$TotalNew = $NewFiles.Count

Write-Host "   معدلة: $ExistingModified/$TotalModified" -ForegroundColor White
Write-Host "   جديدة: $ExistingNew/$TotalNew" -ForegroundColor White
Write-Host "   المجموع: $($ExistingModified + $ExistingNew)/$($TotalModified + $TotalNew)" -ForegroundColor White

if (($ExistingModified -eq $TotalModified) -and ($ExistingNew -eq $TotalNew)) {
    Write-Host ""
    Write-Host "🎉 جميع الملفات جاهزة للرفع!" -ForegroundColor Green
    Write-Host "💡 يمكنك الآن تشغيل upload_to_github.bat أو upload_to_github.ps1" -ForegroundColor Cyan
} else {
    Write-Host ""
    Write-Host "⚠️  بعض الملفات مفقودة!" -ForegroundColor Yellow
    Write-Host "💡 تأكد من إنشاء جميع الملفات قبل الرفع" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "🔗 روابط مفيدة:" -ForegroundColor Cyan
Write-Host "   GitHub Repo: https://github.com/drnopoh2810-spec/s" -ForegroundColor White
Write-Host "   GitHub Actions: https://github.com/drnopoh2810-spec/s/actions" -ForegroundColor White

Read-Host "اضغط Enter للخروج"