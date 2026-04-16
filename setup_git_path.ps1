# إعداد مسارات Git للمشروع
# تشغيل هذا الملف قبل استخدام Git في PowerShell

Write-Host "Setting up Git paths..." -ForegroundColor Yellow

# إضافة مسار Git إلى متغير البيئة PATH
if (Test-Path "C:\Program Files\Git\bin\git.exe") {
    $env:PATH += ";C:\Program Files\Git\bin"
    Write-Host "✓ Git bin path added" -ForegroundColor Green
} else {
    Write-Host "✗ Git not found in default location" -ForegroundColor Red
}

# إضافة مسار Git cmd إذا كان موجوداً
if (Test-Path "C:\Program Files\Git\cmd") {
    $env:PATH += ";C:\Program Files\Git\cmd"
    Write-Host "✓ Git cmd path added" -ForegroundColor Green
}

# التحقق من عمل Git
try {
    $gitVersion = git --version
    Write-Host "✓ Git is working: $gitVersion" -ForegroundColor Green
} catch {
    Write-Host "✗ Git still not working" -ForegroundColor Red
}

Write-Host "`nGit setup completed!" -ForegroundColor Cyan
Write-Host "You can now use Git commands in this PowerShell session." -ForegroundColor White