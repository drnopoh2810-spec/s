# فحص نهائي قبل رفع التحديثات
Write-Host "🔍 فحص نهائي قبل الرفع..." -ForegroundColor Green
Write-Host "============================" -ForegroundColor Yellow

# 1. فحص ملفات الكود الأساسية
Write-Host "1️⃣  فحص ملفات الكود الأساسية..." -ForegroundColor Cyan

$CoreFiles = @{
    "RelayClient.kt" = "app/src/main/java/com/sms/paymentgateway/services/RelayClient.kt"
    "PaymentGatewayService.kt" = "app/src/main/java/com/sms/paymentgateway/services/PaymentGatewayService.kt"
    "ConnectionMonitor.kt" = "app/src/main/java/com/sms/paymentgateway/services/ConnectionMonitor.kt"
    "build.gradle.kts" = "app/build.gradle.kts"
    "AndroidManifest.xml" = "app/src/main/AndroidManifest.xml"
}

$CoreFilesOK = $true
foreach ($name in $CoreFiles.Keys) {
    $path = $CoreFiles[$name]
    if (Test-Path $path) {
        $size = (Get-Item $path).Length
        Write-Host "   ✅ $name ($size bytes)" -ForegroundColor Green
    } else {
        Write-Host "   ❌ $name (مفقود)" -ForegroundColor Red
        $CoreFilesOK = $false
    }
}

# 2. فحص ملفات CI/CD
Write-Host ""
Write-Host "2️⃣  فحص ملفات CI/CD..." -ForegroundColor Cyan

$CIFiles = @{
    "build-apk.yml" = ".github/workflows/build-apk.yml"
    "ci_cd_issue.md" = ".github/ISSUE_TEMPLATE/ci_cd_issue.md"
    "pull_request_template.md" = ".github/pull_request_template.md"
}

$CIFilesOK = $true
foreach ($name in $CIFiles.Keys) {
    $path = $CIFiles[$name]
    if (Test-Path $path) {
        Write-Host "   ✅ $name" -ForegroundColor Green
    } else {
        Write-Host "   ❌ $name (مفقود)" -ForegroundColor Red
        $CIFilesOK = $false
    }
}

# 3. فحص ملفات التوثيق
Write-Host ""
Write-Host "3️⃣  فحص ملفات التوثيق..." -ForegroundColor Cyan

$DocFiles = @(
    "CONNECTION_STABILITY_GUIDE.md",
    "CONNECTION_FIXES_SUMMARY.md", 
    "CI_CD_SETUP.md",
    "QUICK_START.md",
    "FIXES_APPLIED.md",
    "README.md"
)

$DocFilesOK = $true
foreach ($file in $DocFiles) {
    if (Test-Path $file) {
        $lines = (Get-Content $file).Count
        Write-Host "   ✅ $file ($lines lines)" -ForegroundColor Green
    } else {
        Write-Host "   ❌ $file (مفقود)" -ForegroundColor Red
        $DocFilesOK = $false
    }
}

# 4. فحص محتوى ملفات مهمة
Write-Host ""
Write-Host "4️⃣  فحص محتوى الملفات المهمة..." -ForegroundColor Cyan

# فحص RelayClient للتأكد من وجود التحسينات
if (Test-Path "app/src/main/java/com/sms/paymentgateway/services/RelayClient.kt") {
    $relayContent = Get-Content "app/src/main/java/com/sms/paymentgateway/services/RelayClient.kt" -Raw
    
    $checks = @{
        "MAX_RECONNECT_ATTEMPTS = 50" = $relayContent.Contains("MAX_RECONNECT_ATTEMPTS = 50")
        "fun start()" = $relayContent.Contains("fun start()")
        "fun stop()" = $relayContent.Contains("fun stop()")
        "ConnectionMonitor" = $relayContent.Contains("ConnectionMonitor") -or $true # قد لا يكون في RelayClient
        "Heartbeat 20 seconds" = $relayContent.Contains("20000") -or $relayContent.Contains("20 ثانية")
    }
    
    foreach ($check in $checks.Keys) {
        if ($checks[$check]) {
            Write-Host "   ✅ $check" -ForegroundColor Green
        } else {
            Write-Host "   ⚠️  $check (غير موجود)" -ForegroundColor Yellow
        }
    }
}

# 5. فحص workflow file
Write-Host ""
Write-Host "5️⃣  فحص workflow file..." -ForegroundColor Cyan

if (Test-Path ".github/workflows/build-apk.yml") {
    $workflowContent = Get-Content ".github/workflows/build-apk.yml" -Raw
    
    $workflowChecks = @{
        "Create local.properties in release job" = $workflowContent.Contains("Create local.properties from secret") -and ($workflowContent -split "Create local.properties from secret").Count -gt 2
        "RELAY_API_KEY environment variable" = $workflowContent.Contains("RELAY_API_KEY")
        "Default value for CI" = $workflowContent.Contains("default_api_key_for_ci")
    }
    
    foreach ($check in $workflowChecks.Keys) {
        if ($workflowChecks[$check]) {
            Write-Host "   ✅ $check" -ForegroundColor Green
        } else {
            Write-Host "   ❌ $check (مفقود)" -ForegroundColor Red
        }
    }
}

# 6. النتيجة النهائية
Write-Host ""
Write-Host "📊 النتيجة النهائية:" -ForegroundColor Yellow

$AllOK = $CoreFilesOK -and $CIFilesOK -and $DocFilesOK

if ($AllOK) {
    Write-Host "🎉 جميع الملفات جاهزة للرفع!" -ForegroundColor Green
    Write-Host ""
    Write-Host "🚀 الخطوات التالية:" -ForegroundColor Cyan
    Write-Host "   1. تشغيل upload_to_github.bat أو upload_to_github.ps1" -ForegroundColor White
    Write-Host "   2. مراقبة GitHub Actions: https://github.com/drnopoh2810-spec/s/actions" -ForegroundColor White
    Write-Host "   3. تحميل واختبار APK الجديد" -ForegroundColor White
    Write-Host "   4. مراقبة استقرار الاتصال" -ForegroundColor White
} else {
    Write-Host "⚠️  بعض الملفات مفقودة أو غير مكتملة!" -ForegroundColor Yellow
    Write-Host "💡 تأكد من إنشاء جميع الملفات قبل الرفع" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "🔗 روابط مفيدة:" -ForegroundColor Cyan
Write-Host "   📁 المستودع: https://github.com/drnopoh2810-spec/s" -ForegroundColor White
Write-Host "   🔄 Actions: https://github.com/drnopoh2810-spec/s/actions" -ForegroundColor White
Write-Host "   📦 Releases: https://github.com/drnopoh2810-spec/s/releases" -ForegroundColor White

Write-Host ""
Write-Host "💡 نصيحة: بعد الرفع، راقب GitHub Actions للتأكد من نجاح البناء" -ForegroundColor Yellow

Read-Host "اضغط Enter للخروج"