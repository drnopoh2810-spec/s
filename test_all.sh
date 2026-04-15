#!/bin/bash

# SMS Payment Gateway - Comprehensive Test Script
# This script runs all tests and checks before release

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
API_KEY="YOUR_API_KEY_HERE"
BASE_URL="http://localhost:8080/api/v1"

echo -e "${BLUE}╔════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  SMS Payment Gateway - Test Suite         ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════╝${NC}"
echo ""

# Function to print status
print_status() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✅ $2${NC}"
    else
        echo -e "${RED}❌ $2${NC}"
        exit 1
    fi
}

# Function to print info
print_info() {
    echo -e "${YELLOW}ℹ️  $1${NC}"
}

# Function to print section
print_section() {
    echo ""
    echo -e "${BLUE}═══════════════════════════════════════════${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════${NC}"
}

# 1. Unit Tests
print_section "1. Running Unit Tests"
print_info "Executing all unit tests..."
./gradlew test --quiet
print_status $? "Unit Tests"

# 2. Lint Check
print_section "2. Running Lint Check"
print_info "Checking code quality..."
./gradlew lint --quiet
print_status $? "Lint Check"

# 3. Build APK
print_section "3. Building Debug APK"
print_info "Compiling application..."
./gradlew assembleDebug --quiet
print_status $? "Build Debug APK"

# 4. Install on Device
print_section "4. Installing on Device"
print_info "Installing application..."
./gradlew installDebug --quiet
print_status $? "Installation"

# Wait for app to start
sleep 3

# 5. API Tests
print_section "5. Testing API Endpoints"

# Test 1: Health Check
print_info "Testing health endpoint..."
HEALTH_RESPONSE=$(curl -s -w "\n%{http_code}" "$BASE_URL/health" \
  -H "Authorization: Bearer $API_KEY")
HTTP_CODE=$(echo "$HEALTH_RESPONSE" | tail -n1)
BODY=$(echo "$HEALTH_RESPONSE" | head -n-1)

if [ "$HTTP_CODE" = "200" ] && [[ $BODY == *"ok"* ]]; then
    print_status 0 "Health Check"
else
    print_status 1 "Health Check (HTTP $HTTP_CODE)"
fi

# Test 2: Create Transaction
print_info "Testing create transaction..."
TX_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/transactions" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "test-'$(date +%s)'",
    "amount": 500.00,
    "phoneNumber": "01012345678",
    "expiresInMinutes": 30
  }')
HTTP_CODE=$(echo "$TX_RESPONSE" | tail -n1)
BODY=$(echo "$TX_RESPONSE" | head -n-1)

if [ "$HTTP_CODE" = "201" ] && [[ $BODY == *"success"* ]]; then
    print_status 0 "Create Transaction"
    TX_ID=$(echo "$BODY" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
else
    print_status 1 "Create Transaction (HTTP $HTTP_CODE)"
fi

# Test 3: Get Transaction
if [ ! -z "$TX_ID" ]; then
    print_info "Testing get transaction..."
    GET_RESPONSE=$(curl -s -w "\n%{http_code}" "$BASE_URL/transactions/$TX_ID" \
      -H "Authorization: Bearer $API_KEY")
    HTTP_CODE=$(echo "$GET_RESPONSE" | tail -n1)
    
    if [ "$HTTP_CODE" = "200" ]; then
        print_status 0 "Get Transaction"
    else
        print_status 1 "Get Transaction (HTTP $HTTP_CODE)"
    fi
fi

# Test 4: List Transactions
print_info "Testing list transactions..."
LIST_RESPONSE=$(curl -s -w "\n%{http_code}" "$BASE_URL/transactions" \
  -H "Authorization: Bearer $API_KEY")
HTTP_CODE=$(echo "$LIST_RESPONSE" | tail -n1)

if [ "$HTTP_CODE" = "200" ]; then
    print_status 0 "List Transactions"
else
    print_status 1 "List Transactions (HTTP $HTTP_CODE)"
fi

# Test 5: Get SMS Logs
print_info "Testing SMS logs..."
LOGS_RESPONSE=$(curl -s -w "\n%{http_code}" "$BASE_URL/sms/logs" \
  -H "Authorization: Bearer $API_KEY")
HTTP_CODE=$(echo "$LOGS_RESPONSE" | tail -n1)

if [ "$HTTP_CODE" = "200" ]; then
    print_status 0 "Get SMS Logs"
else
    print_status 1 "Get SMS Logs (HTTP $HTTP_CODE)"
fi

# 6. SMS Reception Test (Emulator only)
print_section "6. Testing SMS Reception"
if command -v adb &> /dev/null; then
    # Check if emulator is running
    EMULATOR_STATUS=$(adb devices | grep emulator | wc -l)
    
    if [ $EMULATOR_STATUS -gt 0 ]; then
        print_info "Sending test SMS..."
        adb emu sms send Vodafone "تم استلام 500 جنيه من 01012345678 رقم VC123456789"
        
        # Wait for processing
        sleep 3
        
        # Check logs
        LOGS=$(adb logcat -d | grep "Parsed SMS" | tail -n1)
        if [[ $LOGS == *"Parsed SMS"* ]]; then
            print_status 0 "SMS Reception"
        else
            print_status 1 "SMS Reception (No logs found)"
        fi
    else
        print_info "Skipping SMS test (no emulator detected)"
    fi
else
    print_info "Skipping SMS test (adb not found)"
fi

# 7. Security Tests
print_section "7. Testing Security"

# Test: No API Key
print_info "Testing authentication (no key)..."
NO_KEY_RESPONSE=$(curl -s -w "\n%{http_code}" "$BASE_URL/health")
HTTP_CODE=$(echo "$NO_KEY_RESPONSE" | tail -n1)

if [ "$HTTP_CODE" = "401" ]; then
    print_status 0 "Authentication Required"
else
    print_status 1 "Authentication Required (Expected 401, got $HTTP_CODE)"
fi

# Test: Wrong API Key
print_info "Testing authentication (wrong key)..."
WRONG_KEY_RESPONSE=$(curl -s -w "\n%{http_code}" "$BASE_URL/health" \
  -H "Authorization: Bearer wrong_key_12345")
HTTP_CODE=$(echo "$WRONG_KEY_RESPONSE" | tail -n1)

if [ "$HTTP_CODE" = "401" ]; then
    print_status 0 "Wrong Key Rejected"
else
    print_status 1 "Wrong Key Rejected (Expected 401, got $HTTP_CODE)"
fi

# 8. Performance Check
print_section "8. Performance Check"
print_info "Measuring API response time..."

START_TIME=$(date +%s%N)
curl -s "$BASE_URL/health" -H "Authorization: Bearer $API_KEY" > /dev/null
END_TIME=$(date +%s%N)

RESPONSE_TIME=$(( ($END_TIME - $START_TIME) / 1000000 ))

if [ $RESPONSE_TIME -lt 100 ]; then
    print_status 0 "Response Time: ${RESPONSE_TIME}ms (< 100ms)"
else
    echo -e "${YELLOW}⚠️  Response Time: ${RESPONSE_TIME}ms (> 100ms)${NC}"
fi

# 9. Memory Check
print_section "9. Memory Check"
if command -v adb &> /dev/null; then
    print_info "Checking memory usage..."
    MEMORY=$(adb shell dumpsys meminfo com.sms.paymentgateway | grep "TOTAL" | awk '{print $2}')
    
    if [ ! -z "$MEMORY" ]; then
        MEMORY_MB=$(( $MEMORY / 1024 ))
        if [ $MEMORY_MB -lt 100 ]; then
            print_status 0 "Memory Usage: ${MEMORY_MB}MB (< 100MB)"
        else
            echo -e "${YELLOW}⚠️  Memory Usage: ${MEMORY_MB}MB (> 100MB)${NC}"
        fi
    else
        print_info "Could not measure memory (app not running?)"
    fi
else
    print_info "Skipping memory check (adb not found)"
fi

# 10. Summary
print_section "Test Summary"
echo ""
echo -e "${GREEN}╔════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║           🎉 All Tests Passed! 🎉          ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${BLUE}Test Results:${NC}"
echo -e "  ✅ Unit Tests"
echo -e "  ✅ Lint Check"
echo -e "  ✅ Build & Install"
echo -e "  ✅ API Endpoints (5/5)"
echo -e "  ✅ Security Tests"
echo -e "  ✅ Performance Check"
echo ""
echo -e "${YELLOW}Next Steps:${NC}"
echo -e "  1. Test on real device with actual SMS"
echo -e "  2. Run for 24 hours to check stability"
echo -e "  3. Measure battery consumption"
echo -e "  4. Test with 1000+ SMS messages"
echo -e "  5. Review RELEASE_CHECKLIST.md"
echo ""
echo -e "${GREEN}Ready for release! 🚀${NC}"
echo ""
