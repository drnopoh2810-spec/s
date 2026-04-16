# ═══════════════════════════════════════════════════════════
#  DuckDNS Auto Update Script
#  يحدث IP تلقائياً كل 5 دقائق
# ═══════════════════════════════════════════════════════════

# ضع معلوماتك هنا:
$DOMAIN = "mysmspay"  # ← غيّر هذا لاسم Domain بتاعك
$TOKEN = "YOUR_TOKEN_HERE"  # ← من https://www.duckdns.org

# ═══════════════════════════════════════════════════════════

Write-Host "🦆 DuckDNS Auto Updater Started..." -ForegroundColor Green
Write-Host "Domain: $DOMAIN.duckdns.org" -ForegroundColor Cyan
Write-Host "Press Ctrl+C to stop" -ForegroundColor Yellow
Write-Host ""

while ($true) {
    try {
        # تحديث IP
        $url = "https://www.duckdns.org/update?domains=$DOMAIN&token=$TOKEN&ip="
        $response = Invoke-WebRequest -Uri $url -UseBasicParsing
        
        $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
        
        if ($response.Content -eq "OK") {
            Write-Host "[$timestamp] ✅ IP Updated Successfully" -ForegroundColor Green
        } else {
            Write-Host "[$timestamp] ❌ Update Failed: $($response.Content)" -ForegroundColor Red
        }
    }
    catch {
        Write-Host "[$timestamp] ⚠️ Error: $($_.Exception.Message)" -ForegroundColor Yellow
    }
    
    # انتظر 5 دقائق
    Start-Sleep -Seconds 300
}
