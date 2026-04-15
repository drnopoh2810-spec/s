#!/bin/bash

# اختبار استقرار الاتصال - Connection Stability Test
# يراقب logs التطبيق ويحلل استقرار الاتصال

echo "🔍 بدء اختبار استقرار الاتصال..."
echo "=================================="

# التحقق من وجود ADB
if ! command -v adb &> /dev/null; then
    echo "❌ ADB غير مثبت. يرجى تثبيت Android SDK Platform Tools"
    exit 1
fi

# التحقق من اتصال الجهاز
if ! adb devices | grep -q "device$"; then
    echo "❌ لا يوجد جهاز Android متصل"
    echo "تأكد من:"
    echo "1. تفعيل USB Debugging"
    echo "2. توصيل الجهاز بـ USB"
    echo "3. الموافقة على ADB debugging"
    exit 1
fi

echo "✅ جهاز Android متصل"

# متغيرات الاختبار
PACKAGE_NAME="com.sms.paymentgateway"
TEST_DURATION=${1:-300}  # 5 دقائق افتراضياً
LOG_FILE="connection_test_$(date +%Y%m%d_%H%M%S).log"

echo "📱 اسم التطبيق: $PACKAGE_NAME"
echo "⏱️  مدة الاختبار: $TEST_DURATION ثانية"
echo "📄 ملف السجل: $LOG_FILE"
echo ""

# بدء مراقبة logs
echo "🚀 بدء مراقبة الاتصال..."
echo "اضغط Ctrl+C لإيقاف الاختبار"
echo ""

# إحصائيات
CONNECTIONS=0
DISCONNECTIONS=0
HEARTBEATS=0
RECONNECTS=0
ERRORS=0

# دالة تحليل السجل
analyze_log() {
    local line="$1"
    local timestamp=$(date '+%H:%M:%S')
    
    if [[ $line == *"WebSocket متصل بنجاح"* ]]; then
        CONNECTIONS=$((CONNECTIONS + 1))
        echo "[$timestamp] ✅ اتصال ناجح (#$CONNECTIONS)"
    elif [[ $line == *"WebSocket مغلق"* ]] || [[ $line == *"فشل الاتصال"* ]]; then
        DISCONNECTIONS=$((DISCONNECTIONS + 1))
        echo "[$timestamp] ❌ انقطاع اتصال (#$DISCONNECTIONS)"
    elif [[ $line == *"Heartbeat sent"* ]]; then
        HEARTBEATS=$((HEARTBEATS + 1))
        if (( HEARTBEATS % 10 == 0 )); then
            echo "[$timestamp] 💓 Heartbeat #$HEARTBEATS"
        fi
    elif [[ $line == *"سيتم إعادة الاتصال"* ]]; then
        RECONNECTS=$((RECONNECTS + 1))
        echo "[$timestamp] 🔄 محاولة إعادة اتصال (#$RECONNECTS)"
    elif [[ $line == *"ERROR"* ]] || [[ $line == *"خطأ"* ]]; then
        ERRORS=$((ERRORS + 1))
        echo "[$timestamp] ⚠️  خطأ (#$ERRORS): $(echo $line | cut -d':' -f4-)"
    fi
}

# دالة عرض الإحصائيات
show_stats() {
    echo ""
    echo "📊 إحصائيات الاختبار:"
    echo "======================"
    echo "🔗 اتصالات ناجحة: $CONNECTIONS"
    echo "❌ انقطاعات: $DISCONNECTIONS"
    echo "💓 Heartbeats: $HEARTBEATS"
    echo "🔄 محاولات إعادة اتصال: $RECONNECTS"
    echo "⚠️  أخطاء: $ERRORS"
    echo ""
    
    if (( CONNECTIONS > 0 )); then
        local uptime_ratio=$(( (HEARTBEATS * 20) / (TEST_DURATION > 0 ? TEST_DURATION : 1) ))
        echo "📈 نسبة الاستقرار: ${uptime_ratio}%"
        
        if (( uptime_ratio >= 90 )); then
            echo "✅ الاتصال مستقر جداً"
        elif (( uptime_ratio >= 70 )); then
            echo "⚠️  الاتصال مقبول"
        else
            echo "❌ الاتصال غير مستقر"
        fi
    fi
}

# دالة التنظيف عند الإيقاف
cleanup() {
    echo ""
    echo "🛑 إيقاف الاختبار..."
    show_stats
    echo "📄 تم حفظ السجل في: $LOG_FILE"
    exit 0
}

# تسجيل إشارة الإيقاف
trap cleanup SIGINT SIGTERM

# بدء المراقبة
START_TIME=$(date +%s)
adb logcat -c  # مسح logs السابقة

# مراقبة logs مع تحليل فوري
adb logcat | grep -E "(RelayClient|ConnectionMonitor|PaymentGatewayService)" | while read line; do
    echo "$line" >> "$LOG_FILE"
    analyze_log "$line"
    
    # التحقق من انتهاء وقت الاختبار
    CURRENT_TIME=$(date +%s)
    ELAPSED=$((CURRENT_TIME - START_TIME))
    
    if (( ELAPSED >= TEST_DURATION )); then
        echo ""
        echo "⏰ انتهى وقت الاختبار ($TEST_DURATION ثانية)"
        kill -INT $$
    fi
done

# إذا وصل هنا، فقد انتهى الاختبار
cleanup