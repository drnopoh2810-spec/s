# دليل الاختبار الشامل - SMS Payment Gateway

## 🧪 استراتيجية الاختبار

---

## 1. Unit Tests

### تشغيل جميع الاختبارات

```bash
# تشغيل جميع Unit Tests
./gradlew test

# تشغيل مع تقرير
./gradlew test --info

# عرض التقرير
open app/build/reports/tests/testDebugUnitTest/index.html
```

### الاختبارات الموجودة

#### ✅ SmsParserTest (9 tests)
```bash
./gradlew test --tests SmsParserTest
```

**Test Cases:**
- ✓ parse Vodafone Cash received message
- ✓ parse Vodafone Cash sent message
- ✓ parse Orange Money message
- ✓ parse InstaPay message
- ✓ parse message with English amount
- ✓ parse message with decimal amount
- ✓ return null for unknown sender
- ✓ parse message without transaction ID
- ✓ confidence score calculation

#### ✅ TransactionMatcherTest (8 tests)
```bash
./gradlew test --tests TransactionMatcherTest
```

**Test Cases:**
- ✓ exact transaction ID match
- ✓ amount and phone match
- ✓ no match for different amount
- ✓ no match for different phone
- ✓ match within time window
- ✓ no match outside time window
- ✓ select best match from multiple candidates

#### ✅ RateLimiterTest (7 tests)
```bash
./gradlew test --tests RateLimiterTest
```

**Test Cases:**
- ✓ allows requests under limit
- ✓ blocks requests over limit
- ✓ different IPs have separate limits
- ✓ reset clears limit for IP
- ✓ clearAll resets all IPs
- ✓ getRequestCount returns correct count
- ✓ old requests are removed from window

#### ✅ SecurityManagerTest (8 tests)
```bash
./gradlew test --tests SecurityManagerTest
```

**Test Cases:**
- ✓ API key is generated on first access
- ✓ validate API key returns true for correct key
- ✓ validate API key returns false for incorrect key
- ✓ HMAC signature is generated correctly
- ✓ HMAC signature verification works
- ✓ IP whitelist allows all when empty
- ✓ IP whitelist allows whitelisted IP
- ✓ IP whitelist blocks non-whitelisted IP

---

## 2. Integration Tests

### إعداد Integration Tests

```kotlin
// في app/src/androidTest/java/
@RunWith(AndroidJUnit4::class)
class SmsProcessingIntegrationTest {
    
    @Test
    fun testEndToEndSmsProcessing() {
        // 1. Create pending transaction
        // 2. Simulate SMS reception
        // 3. Verify matching
        // 4. Verify webhook sent
    }
}
```

### تشغيل Integration Tests

```bash
# على جهاز متصل
./gradlew connectedAndroidTest

# على محاكي محدد
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=SmsProcessingIntegrationTest
```

---

## 3. Manual Testing

### 3.1 اختبار استقبال SMS

#### الطريقة 1: من ADB (المحاكي فقط)

```bash
# Vodafone Cash
adb emu sms send Vodafone "تم استلام 500 جنيه من 01012345678 رقم العملية VC123456789"

# Orange Money
adb emu sms send Orange "تم استلام 750 جنيه من 01112345678 رقم OM123456"

# Etisalat Cash
adb emu sms send Etisalat "Received 600 EGP from 01512345678 Ref: EC12345678"

# Fawry
adb emu sms send Fawry "تم سداد فاتورة بقيمة 450 جنيه - رقم العملية 1234567890"

# InstaPay
adb emu sms send InstaPay "استلمت مبلغ 800 جنيه من 01012345678 رقم IP123456"
```

#### الطريقة 2: من هاتف آخر (جهاز حقيقي)

1. استخدم هاتف ثاني
2. أرسل رسالة نصية للهاتف المثبت عليه التطبيق
3. راقب Logcat

```bash
adb logcat | grep "SmsReceiver\|SmsParser"
```

#### الطريقة 3: من تطبيق SMS Faker (للاختبار)

1. ثبّت تطبيق SMS faker من Play Store
2. أنشئ رسائل تجريبية
3. راقب معالجة التطبيق

---

### 3.2 اختبار API

#### Test 1: Health Check

```bash
curl http://localhost:8080/api/v1/health \
  -H "Authorization: Bearer YOUR_API_KEY"

# Expected Response:
# {
#   "status": "ok",
#   "timestamp": 1705329000000,
#   "service": "SMS Payment Gateway"
# }
```

#### Test 2: Create Transaction

```bash
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "test-001",
    "amount": 500.00,
    "phoneNumber": "01012345678",
    "expectedTxId": "VC123456789",
    "walletType": "VODAFONE_CASH",
    "expiresInMinutes": 30
  }'

# Expected Response:
# {
#   "success": true,
#   "transaction": { ... }
# }
```

#### Test 3: Get Transaction

```bash
curl http://localhost:8080/api/v1/transactions/test-001 \
  -H "Authorization: Bearer YOUR_API_KEY"

# Expected Response:
# {
#   "id": "test-001",
#   "amount": 500.00,
#   "status": "PENDING",
#   ...
# }
```

#### Test 4: List Transactions

```bash
curl http://localhost:8080/api/v1/transactions \
  -H "Authorization: Bearer YOUR_API_KEY"

# Expected Response:
# {
#   "transactions": [ ... ]
# }
```

#### Test 5: Get SMS Logs

```bash
curl http://localhost:8080/api/v1/sms/logs \
  -H "Authorization: Bearer YOUR_API_KEY"

# Expected Response:
# {
#   "logs": [ ... ]
# }
```

---

### 3.3 اختبار End-to-End

#### Scenario 1: Successful Payment Confirmation

```bash
# Step 1: Create pending transaction
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "e2e-test-001",
    "amount": 500.00,
    "phoneNumber": "01012345678"
  }'

# Step 2: Send matching SMS
adb emu sms send Vodafone "تم استلام 500 جنيه من 01012345678 رقم VC123456"

# Step 3: Wait 2 seconds

# Step 4: Check transaction status
curl http://localhost:8080/api/v1/transactions/e2e-test-001 \
  -H "Authorization: Bearer YOUR_API_KEY"

# Expected: status = "MATCHED"
```

#### Scenario 2: No Match (Different Amount)

```bash
# Step 1: Create transaction for 500 EGP
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "e2e-test-002",
    "amount": 500.00,
    "phoneNumber": "01012345678"
  }'

# Step 2: Send SMS with different amount (750 EGP)
adb emu sms send Vodafone "تم استلام 750 جنيه من 01012345678"

# Step 3: Check status
curl http://localhost:8080/api/v1/transactions/e2e-test-002 \
  -H "Authorization: Bearer YOUR_API_KEY"

# Expected: status = "PENDING" (no match)
```

#### Scenario 3: Transaction Expiration

```bash
# Step 1: Create transaction with 1 minute expiry
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "e2e-test-003",
    "amount": 500.00,
    "phoneNumber": "01012345678",
    "expiresInMinutes": 1
  }'

# Step 2: Wait 2 minutes

# Step 3: Check status
curl http://localhost:8080/api/v1/transactions/e2e-test-003 \
  -H "Authorization: Bearer YOUR_API_KEY"

# Expected: status = "EXPIRED"
```

---

## 4. Performance Testing

### 4.1 Load Testing

#### Test 1: Multiple Concurrent Requests

```bash
# استخدم Apache Bench
ab -n 1000 -c 10 \
  -H "Authorization: Bearer YOUR_API_KEY" \
  http://localhost:8080/api/v1/health

# Expected:
# - 100% success rate
# - Average response time < 100ms
```

#### Test 2: Bulk SMS Processing

```bash
# أرسل 100 SMS في دقيقة واحدة
for i in {1..100}; do
  adb emu sms send Vodafone "تم استلام $((500 + i)) جنيه من 01012345678"
  sleep 0.6
done

# راقب الأداء
adb logcat | grep "Processing time"
```

### 4.2 Memory Testing

```bash
# مراقبة استهلاك الذاكرة
adb shell dumpsys meminfo com.sms.paymentgateway

# Expected:
# - Total memory < 100 MB
# - No memory leaks
```

### 4.3 Battery Testing

```bash
# مراقبة استهلاك البطارية
adb shell dumpsys batterystats --reset
# اترك التطبيق يعمل 24 ساعة
adb shell dumpsys batterystats | grep com.sms.paymentgateway

# Expected:
# - Battery drain < 5% per hour
```

---

## 5. Security Testing

### 5.1 Authentication Testing

#### Test 1: No API Key

```bash
curl http://localhost:8080/api/v1/health

# Expected: 401 Unauthorized
```

#### Test 2: Wrong API Key

```bash
curl http://localhost:8080/api/v1/health \
  -H "Authorization: Bearer wrong_key"

# Expected: 401 Unauthorized
```

#### Test 3: Correct API Key

```bash
curl http://localhost:8080/api/v1/health \
  -H "Authorization: Bearer YOUR_API_KEY"

# Expected: 200 OK
```

### 5.2 Rate Limiting Testing

```bash
# أرسل 150 طلب في دقيقة (الحد 100)
for i in {1..150}; do
  curl http://localhost:8080/api/v1/health \
    -H "Authorization: Bearer YOUR_API_KEY"
done

# Expected:
# - First 100: 200 OK
# - Next 50: 429 Too Many Requests
```

### 5.3 IP Whitelist Testing

```bash
# 1. أضف IP للـ whitelist من Settings

# 2. جرب من IP مختلف
curl http://localhost:8080/api/v1/health \
  -H "Authorization: Bearer YOUR_API_KEY"

# Expected: 403 Forbidden
```

---

## 6. Regression Testing

### Checklist قبل كل Release

```
□ جميع Unit Tests تعمل (32+ tests)
□ Integration Tests تعمل
□ Manual E2E scenarios تعمل
□ Performance tests تعمل
□ Security tests تعمل
□ UI tests تعمل
□ اختبار على 3+ أجهزة مختلفة
□ اختبار على Android 8, 10, 12, 13
□ اختبار Battery optimization
□ اختبار بعد إعادة التشغيل
□ اختبار مع شبكة ضعيفة
□ اختبار مع 1000+ SMS
```

---

## 7. Test Data

### نماذج SMS للاختبار

```bash
# Vodafone Cash - Received
"تم استلام 500 جنيه من 01012345678 رقم العملية VC123456789"
"You received 250.50 EGP from 01098765432 Transaction ID: VC987654321"

# Vodafone Cash - Sent
"تم إرسال 300 جنيه إلى 01012345678 رقم العملية VC111222333"

# Orange Money
"تم استلام 750 جنيه من 01112345678 رقم OM123456"
"Orange Money: Received 500 EGP from 01212345678 ID: OM789012"

# Etisalat Cash
"Etisalat Cash: تم استلام 600 جنيه من 01512345678"
"Received 350 EGP via Etisalat Cash - Ref: EC12345678"

# Fawry
"Fawry: تم سداد فاتورة بقيمة 450 جنيه - رقم العملية 1234567890"

# InstaPay
"InstaPay: استلمت مبلغ 800 جنيه من 01012345678 رقم IP123456"
"You received 650 EGP via InstaPay from 01112345678 Ref: IP789012"
```

---

## 8. Automated Testing Script

### test_all.sh

```bash
#!/bin/bash

echo "🧪 Starting Comprehensive Tests..."

# 1. Unit Tests
echo "📝 Running Unit Tests..."
./gradlew test
if [ $? -ne 0 ]; then
    echo "❌ Unit Tests Failed"
    exit 1
fi
echo "✅ Unit Tests Passed"

# 2. Build APK
echo "🔨 Building APK..."
./gradlew assembleDebug
if [ $? -ne 0 ]; then
    echo "❌ Build Failed"
    exit 1
fi
echo "✅ Build Successful"

# 3. Install on Device
echo "📱 Installing on Device..."
./gradlew installDebug
if [ $? -ne 0 ]; then
    echo "❌ Installation Failed"
    exit 1
fi
echo "✅ Installation Successful"

# 4. API Tests
echo "🌐 Testing API..."
API_KEY="YOUR_API_KEY"

# Health Check
HEALTH=$(curl -s http://localhost:8080/api/v1/health \
  -H "Authorization: Bearer $API_KEY")
if [[ $HEALTH == *"ok"* ]]; then
    echo "✅ Health Check Passed"
else
    echo "❌ Health Check Failed"
    exit 1
fi

# Create Transaction
TX_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/transactions \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"id":"test-001","amount":500,"phoneNumber":"01012345678"}')
if [[ $TX_RESPONSE == *"success"* ]]; then
    echo "✅ Create Transaction Passed"
else
    echo "❌ Create Transaction Failed"
    exit 1
fi

# 5. SMS Test
echo "📨 Testing SMS Reception..."
adb emu sms send Vodafone "تم استلام 500 جنيه من 01012345678 رقم VC123456"
sleep 3

# Check Logs
LOGS=$(adb logcat -d | grep "Parsed SMS")
if [[ $LOGS == *"Parsed SMS"* ]]; then
    echo "✅ SMS Reception Passed"
else
    echo "❌ SMS Reception Failed"
    exit 1
fi

echo "🎉 All Tests Passed!"
```

---

## 9. Test Coverage

### قياس Test Coverage

```bash
# تشغيل مع coverage
./gradlew testDebugUnitTest jacocoTestReport

# عرض التقرير
open app/build/reports/jacoco/jacocoTestReport/html/index.html
```

### الهدف المطلوب

```
✅ SmsParser: 95%+ coverage
✅ TransactionMatcher: 90%+ coverage
✅ SecurityManager: 85%+ coverage
✅ ApiServer: 80%+ coverage
✅ Overall: 80%+ coverage
```

---

## 📊 Test Results Template

```markdown
## Test Run: 2026-04-15

### Unit Tests
- Total: 32 tests
- Passed: 32 ✅
- Failed: 0
- Duration: 2.5s

### Integration Tests
- Total: 5 tests
- Passed: 5 ✅
- Failed: 0
- Duration: 15s

### Manual Tests
- SMS Reception: ✅ Pass
- API Endpoints: ✅ Pass
- Matching Logic: ✅ Pass
- Webhook Delivery: ✅ Pass

### Performance
- API Response Time: 45ms (< 100ms) ✅
- SMS Processing Time: 120ms ✅
- Memory Usage: 65 MB (< 100 MB) ✅
- Battery Drain: 3%/hour (< 5%) ✅

### Devices Tested
- Samsung Galaxy S21 (Android 13) ✅
- Xiaomi Redmi Note 10 (Android 12) ✅
- Google Pixel 5 (Android 13) ✅

### Overall Result: ✅ PASS
```

---

**آخر تحديث:** 2026-04-15  
**الإصدار:** 1.0
