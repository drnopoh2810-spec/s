# نماذج رسائل SMS للمحافظ الإلكترونية المصرية

هذا الملف يحتوي على نماذج رسائل SMS الفعلية من المحافظ الإلكترونية المصرية لتحديث وتحسين Regex patterns.

---

## 📱 Vodafone Cash

### رسائل الاستلام (Received)

```
نموذج 1:
تم استلام مبلغ 500 جنيه من 01012345678 رقم العملية VC123456789

نموذج 2:
You received 250.50 EGP from 01098765432 Transaction ID: VC987654321

نموذج 3:
استلمت 1000 جنيه من محفظة 01112345678 - رقم المرجع: VC555666777
```

### رسائل الإرسال (Sent)

```
نموذج 1:
تم إرسال 300 جنيه إلى 01012345678 رقم العملية VC111222333

نموذج 2:
You sent 150 EGP to 01098765432 Ref: VC444555666
```

### رسائل الفشل (Failed)

```
نموذج 1:
فشلت عملية تحويل 500 جنيه إلى 01012345678 - رصيد غير كافي

نموذج 2:
Transaction failed - Insufficient balance
```

**Regex Patterns المقترحة:**

```kotlin
// Amount
val amountPattern = """(?:مبلغ|amount|استلمت|received|إرسال|sent)\s*:?\s*(\d+(?:\.\d{1,2})?)\s*(?:جنيه|EGP|LE)?""".toRegex(RegexOption.IGNORE_CASE)

// Transaction ID
val txIdPattern = """(?:رقم العملية|رقم المرجع|Transaction ID|Ref|Reference)\s*:?\s*(VC\d{9,12})""".toRegex(RegexOption.IGNORE_CASE)

// Phone Number
val phonePattern = """(?:من|to|from|محفظة)\s*:?\s*(01\d{9})""".toRegex(RegexOption.IGNORE_CASE)
```

---

## 🍊 Orange Money

### رسائل الاستلام

```
نموذج 1:
تم استلام 750 جنيه من 01112345678 رقم OM123456

نموذج 2:
Orange Money: Received 500 EGP from 01212345678 ID: OM789012

نموذج 3:
استلمت تحويل بقيمة 1200 جنيه - رقم العملية OM345678
```

### رسائل الإرسال

```
نموذج 1:
تم تحويل 400 جنيه إلى 01012345678 - Orange Money

نموذج 2:
You transferred 250 EGP via Orange Money Ref: OM111222
```

**Regex Patterns المقترحة:**

```kotlin
// Amount
val amountPattern = """(\d+(?:\.\d{1,2})?)\s*(?:جنيه|EGP)""".toRegex(RegexOption.IGNORE_CASE)

// Transaction ID
val txIdPattern = """(?:رقم|ID|Ref|رقم العملية)\s*:?\s*(OM\d{6,10})""".toRegex(RegexOption.IGNORE_CASE)

// Phone
val phonePattern = """(01\d{9})""".toRegex()
```

---

## 📞 Etisalat Cash

### رسائل الاستلام

```
نموذج 1:
Etisalat Cash: تم استلام 600 جنيه من 01512345678

نموذج 2:
Received 350 EGP via Etisalat Cash - Ref: EC12345678

نموذج 3:
استلمت 900 جنيه - Etisalat Cash
```

**Regex Patterns المقترحة:**

```kotlin
// Amount
val amountPattern = """(\d+(?:\.\d{1,2})?)\s*(?:جنيه|EGP)""".toRegex(RegexOption.IGNORE_CASE)

// Transaction ID
val txIdPattern = """(?:Ref|رقم)\s*:?\s*(EC\d{8,12})""".toRegex(RegexOption.IGNORE_CASE)

// Phone
val phonePattern = """(01\d{9})""".toRegex()
```

---

## 💳 Fawry

### رسائل السداد

```
نموذج 1:
Fawry: تم سداد فاتورة بقيمة 450 جنيه - رقم العملية 1234567890

نموذج 2:
Payment successful: 300 EGP via Fawry Ref: 9876543210

نموذج 3:
تم الدفع بنجاح - المبلغ 550 جنيه
```

**Regex Patterns المقترحة:**

```kotlin
// Amount
val amountPattern = """(?:بقيمة|المبلغ|amount)\s*:?\s*(\d+(?:\.\d{1,2})?)\s*(?:جنيه|EGP)?""".toRegex(RegexOption.IGNORE_CASE)

// Transaction ID
val txIdPattern = """(?:رقم العملية|Ref|Reference)\s*:?\s*(\d{10,15})""".toRegex(RegexOption.IGNORE_CASE)
```

---

## 💸 InstaPay

### رسائل الاستلام

```
نموذج 1:
InstaPay: استلمت مبلغ 800 جنيه من 01012345678 رقم IP123456

نموذج 2:
You received 650 EGP via InstaPay from 01112345678 Ref: IP789012

نموذج 3:
تم استلام تحويل فوري بقيمة 1500 جنيه - InstaPay
```

### رسائل الإرسال

```
نموذج 1:
InstaPay: حولت 400 جنيه إلى 01212345678

نموذج 2:
Transfer successful: 300 EGP to 01312345678 via InstaPay
```

**Regex Patterns المقترحة:**

```kotlin
// Amount
val amountPattern = """(?:مبلغ|amount|بقيمة)\s*:?\s*(\d+(?:\.\d{1,2})?)\s*(?:جنيه|EGP)?""".toRegex(RegexOption.IGNORE_CASE)

// Transaction ID
val txIdPattern = """(?:رقم|Ref|ID)\s*:?\s*(IP\d{6,10})""".toRegex(RegexOption.IGNORE_CASE)

// Phone
val phonePattern = """(?:من|to|from|إلى)\s*:?\s*(01\d{9})""".toRegex(RegexOption.IGNORE_CASE)
```

---

## 🔍 ملاحظات مهمة للتحديث

### 1. التنوع في الصيغ
- المحافظ تستخدم صيغ متعددة (عربي/إنجليزي)
- بعض الرسائل تحتوي على رموز خاصة
- التواريخ قد تكون بصيغ مختلفة

### 2. أرقام العمليات
- Vodafone Cash: VC + 9-12 رقم
- Orange Money: OM + 6-10 أرقام
- Etisalat Cash: EC + 8-12 رقم
- Fawry: 10-15 رقم فقط
- InstaPay: IP + 6-10 أرقام

### 3. أرقام الهواتف
- دائمًا تبدأ بـ 01
- 11 رقم إجمالي (01 + 9 أرقام)
- قد تكون مسبوقة بـ "من" أو "to" أو "from"

### 4. المبالغ
- قد تحتوي على فاصلة عشرية
- قد تكون متبوعة بـ "جنيه" أو "EGP" أو "LE"
- قد تكون مسبوقة بـ "مبلغ" أو "amount"

---

## 📝 كيفية جمع نماذج جديدة

### الطريقة 1: من مستخدمين حقيقيين
1. اطلب من مستخدمين نسخ رسائل SMS
2. احذف المعلومات الحساسة (أرقام حقيقية)
3. استبدلها بأرقام تجريبية

### الطريقة 2: من هاتف تجريبي
1. استخدم هاتف مخصص للاختبار
2. أجرِ عمليات حقيقية بمبالغ صغيرة
3. احفظ الرسائل المستلمة

### الطريقة 3: من الدعم الفني
1. تواصل مع دعم المحافظ
2. اطلب نماذج رسائل SMS
3. وثّق الصيغ الرسمية

---

## ✅ Checklist للتحديث

عند الحصول على نماذج جديدة:

```
□ جمع 10+ رسائل من كل محفظة
□ توثيق جميع الصيغ (عربي/إنجليزي)
□ تحديث Regex patterns في SmsParser.kt
□ إضافة test cases جديدة
□ اختبار على رسائل حقيقية
□ قياس دقة التحليل (يجب أن تكون 95%+)
□ تحديث هذا الملف بالنماذج الجديدة
```

---

## 🚀 خطوات التحديث السريع

1. **افتح SmsParser.kt**
   ```
   app/src/main/java/com/sms/paymentgateway/utils/parser/SmsParser.kt
   ```

2. **حدّث Regex patterns** بناءً على النماذج الجديدة

3. **أضف test cases** في SmsParserTest.kt

4. **اختبر**:
   ```bash
   ./gradlew test
   ```

5. **اختبر على هاتف حقيقي**:
   ```bash
   adb emu sms send Vodafone "تم استلام 500 جنيه من 01012345678 رقم VC123456789"
   ```

---

## 📞 المساعدة

إذا واجهت صعوبة في جمع النماذج:
- راجع SETUP_GUIDE.md
- تواصل مع مستخدمي المحافظ
- استخدم مجموعات Facebook/Telegram للمحافظ

---

**آخر تحديث:** يحتاج تحديث بنماذج حقيقية  
**الحالة:** 🟡 يحتاج نماذج حقيقية من المستخدمين
