# SMS Payment Gateway - Relay Server v2.0

  خادم وسيط متطور لبوابة الدفع عبر SMS، مدعوم بـ PostgreSQL، WebSocket، ودعم كامل للمحافظ الإلكترونية المصرية.

  ## المحافظ المدعومة
  - Vodafone Cash
  - Orange Money
  - Etisalat Cash
  - Fawry
  - InstaPay

  ## المتطلبات
  - Node.js 18+
  - PostgreSQL 14+

  ## التثبيت

  ```bash
  cd relay-server-v2
  npm install
  ```

  ## إعداد البيئة

  ```env
  DATABASE_URL=postgresql://user:password@localhost:5432/sms_gateway
  PORT=5000
  ```

  ## تهيئة قاعدة البيانات

  ```bash
  npm run db:push
  ```

  ## التشغيل

  ```bash
  npm run build
  npm start

  # أو للتطوير
  npm run dev
  ```

  ## واجهة برمجة التطبيقات (API)

  | الطريقة | المسار | الوصف |
  |--------|--------|-------|
  | GET | /api/v1/relay/status | حالة الخادم |
  | GET | /api/v1/devices | قائمة الأجهزة |
  | POST | /api/v1/devices | تسجيل جهاز |
  | DELETE | /api/v1/devices/:id | حذف جهاز |
  | POST | /api/v1/transactions | إنشاء معاملة |
  | GET | /api/v1/transactions/:id | حالة معاملة |
  | GET | /api/v1/transactions | قائمة المعاملات |
  | POST | /api/v1/sms/ingest | استقبال SMS من التطبيق |
  | GET | /api/v1/sms/logs | سجلات SMS |
  | GET | /api/v1/stats | إحصائيات |
  | WS | /device | WebSocket للتطبيق Android |

  ## الاتصال من تطبيق Android

  ```javascript
  const ws = new WebSocket("ws://YOUR_SERVER/device", {
    headers: { "X-Api-Key": "YOUR_DEVICE_API_KEY" }
  });
  ```

  ## إرسال SMS

  ```bash
  curl -X POST https://YOUR_SERVER/api/v1/sms/ingest \
    -H "X-Api-Key: YOUR_DEVICE_API_KEY" \
    -H "Content-Type: application/json" \
    -d '{"sender":"VCASH","body":"تم استلام 500 جنيه من 01012345678 رقم العملية TXN123"}'
  ```
  