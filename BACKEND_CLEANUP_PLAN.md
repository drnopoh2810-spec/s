# خطة تنظيف Backend والاعتماد على Huggingface Relay فقط

## المشاكل المكتشفة:

### 1. ازدواجية في نظام الاتصال
- ✅ **RelayClient** - للاتصال عبر huggingface-relay (المطلوب)
- ❌ **DirectConnectionManager** - للاتصال المباشر (غير مطلوب)
- ❌ **ExternalAccessManager** - لإدارة الوصول الخارجي (غير مطلوب)
- ❌ **CloudflareTunnelManager** - لإدارة Cloudflare Tunnel (غير مطلوب)

### 2. PaymentGatewayService يستخدم DirectConnectionManager
```kotlin
// الكود الحالي (خطأ):
directConnectionManager.start()

// المطلوب:
relayClient.start()
```

### 3. DashboardViewModel يعرض معلومات DirectConnection
```kotlin
// الكود الحالي (خطأ):
directConnectionManager.isActive
directConnectionManager.connectionUrl

// المطلوب:
relayClient.isConnected()
relayClient.getConnectionInfo()
```

## الحل:

### 1. تحديث PaymentGatewayService
- حذف DirectConnectionManager
- حذف ExternalAccessManager
- الاعتماد فقط على RelayClient

### 2. تحديث DashboardViewModel
- حذف DirectConnectionManager
- حذف ExternalAccessManager
- الاعتماد فقط على RelayClient

### 3. تحديث SettingsViewModel
- إبقاء RelayClient فقط
- حذف أي إشارات لـ DirectConnection

### 4. حذف الملفات غير المطلوبة
- DirectConnectionManager.kt (سيتم تعطيله)
- ExternalAccessManager.kt (سيتم تعطيله)
- CloudflareTunnelManager.kt (إذا كان موجوداً)

### 5. تحديث UI
- DashboardScreen: عرض معلومات RelayClient فقط
- SettingsScreen: إعدادات RelayClient فقط

## الملفات التي سيتم تعديلها:
1. ✅ PaymentGatewayService.kt
2. ✅ DashboardViewModel.kt
3. ✅ SettingsViewModel.kt (جاهز)
4. ✅ DashboardScreen.kt
5. ✅ RelayClient.kt (تحسينات)
6. ✅ ApiDocumentationGenerator.kt (تحديث URLs)

## النتيجة النهائية:
- اتصال واحد فقط عبر Huggingface Relay
- واجهة نظيفة وواضحة
- لا ازدواجية في الكود
- سهولة الصيانة
