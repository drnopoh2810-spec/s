// ============================================================
//  SMS Payment Gateway — API Documentation (JavaScript)
//  هذا مثال على الملف الذي سيحمّله المستخدم من التطبيق
// ============================================================

// ✅ الـ URL و API Key الحقيقيين من الجهاز
const BASE_URL = "http://192.168.1.100:8080/api/v1";  // ← يتم ملؤه تلقائياً
const API_KEY  = "sk_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6";  // ← يتم ملؤه تلقائياً

// ✅ المصادقة مُضمّنة في كل طلب
const headers = {
  "Authorization": `Bearer ${API_KEY}`,  // ← Bearer Token Authentication
  "Content-Type": "application/json"
};

// ============================================================
//  جميع الـ API Endpoints مع المصادقة
// ============================================================

// 1) Health Check - التحقق من حالة الخادم
async function healthCheck() {
  const res = await fetch(`${BASE_URL}/health`, { 
    headers  // ← يستخدم Authorization header
  });
  return res.json();
}

// 2) Create Transaction - إنشاء معاملة جديدة
async function createTransaction(id, amount, phone, walletType = "VODAFONE_CASH") {
  const res = await fetch(`${BASE_URL}/transactions`, {
    method: "POST", 
    headers,  // ← يستخدم Authorization header
    body: JSON.stringify({ 
      id, 
      amount, 
      phoneNumber: phone, 
      walletType, 
      expiresInMinutes: 30 
    })
  });
  return res.json();
}

// 3) Get Transaction Status - الحصول على حالة المعاملة
async function getTransaction(id) {
  const res = await fetch(`${BASE_URL}/transactions/${id}`, { 
    headers  // ← يستخدم Authorization header
  });
  return res.json();
}

// 4) Poll Until Confirmed - الانتظار حتى تأكيد الدفع (max 5 min)
async function waitForPayment(id, intervalMs = 5000, maxMs = 300000) {
  const start = Date.now();
  while (Date.now() - start < maxMs) {
    const tx = await getTransaction(id);  // ← يستخدم المصادقة
    if (tx.status === "MATCHED") return tx;
    await new Promise(r => setTimeout(r, intervalMs));
  }
  throw new Error("Payment timeout");
}

// 5) WebSocket — Real-time Notifications
function connectWebSocket(onPayment) {
  const wsUrl = BASE_URL.replace("http://", "ws://")
                        .replace("https://", "wss://")
                        .replace("/api/v1", "") + "/websocket";
  
  const ws = new WebSocket(wsUrl);
  
  ws.onopen = () => {
    console.log("✅ WebSocket connected");
    ws.send(JSON.stringify({ type: "subscribe" }));
  };
  
  ws.onmessage = (e) => {
    const msg = JSON.parse(e.data);
    if (msg.event === "PAYMENT_CONFIRMED") {
      console.log("💰 Payment confirmed:", msg.data);
      onPayment(msg.data);
    }
  };
  
  ws.onerror = (error) => {
    console.error("❌ WebSocket error:", error);
  };
  
  ws.onclose = () => {
    console.log("🔌 WebSocket disconnected, reconnecting...");
    setTimeout(() => connectWebSocket(onPayment), 5000);
  };
  
  return ws;
}

// ============================================================
//  أمثلة الاستخدام - جاهزة للتشغيل مباشرة!
// ============================================================

// مثال 1: التحقق من حالة الخادم
async function example1_healthCheck() {
  console.log("🔍 Checking server health...");
  const health = await healthCheck();
  console.log("✅ Server status:", health);
}

// مثال 2: إنشاء معاملة والانتظار للتأكيد
async function example2_createAndWait() {
  console.log("💳 Creating transaction...");
  
  // إنشاء معاملة
  const result = await createTransaction(
    "order-12345",      // Order ID
    500.00,             // Amount
    "01012345678",      // Phone number
    "VODAFONE_CASH"     // Wallet type
  );
  
  console.log("✅ Transaction created:", result);
  
  // الانتظار حتى تأكيد الدفع
  console.log("⏳ Waiting for payment confirmation...");
  const confirmed = await waitForPayment("order-12345");
  
  console.log("💰 Payment confirmed!", confirmed);
  return confirmed;
}

// مثال 3: استخدام WebSocket للإشعارات الفورية
function example3_realtimeNotifications() {
  console.log("🔔 Setting up real-time notifications...");
  
  const ws = connectWebSocket((paymentData) => {
    console.log("💰 New payment received:", paymentData);
    
    // هنا يمكنك تحديث واجهة المستخدم
    // أو إرسال إشعار للمستخدم
    // أو تحديث قاعدة البيانات
    
    alert(`Payment confirmed! Amount: ${paymentData.amount} EGP`);
  });
  
  return ws;
}

// مثال 4: سيناريو كامل - موقع تجارة إلكترونية
async function example4_fullEcommerceFlow() {
  console.log("🛒 Starting e-commerce payment flow...");
  
  // 1. المستخدم يضغط "ادفع الآن"
  const orderId = `order-${Date.now()}`;
  const amount = 1500.00;
  const customerPhone = "01012345678";
  
  console.log(`📝 Order ID: ${orderId}`);
  console.log(`💵 Amount: ${amount} EGP`);
  
  // 2. إنشاء معاملة في التطبيق
  const transaction = await createTransaction(
    orderId,
    amount,
    customerPhone,
    "VODAFONE_CASH"
  );
  
  console.log("✅ Transaction created, waiting for payment...");
  
  // 3. عرض رسالة للمستخدم
  console.log(`
    📱 يرجى إرسال ${amount} جنيه من Vodafone Cash
    إلى الرقم: 01012345678
    
    ⏳ في انتظار تأكيد الدفع...
  `);
  
  // 4. الانتظار حتى تأكيد الدفع (مع timeout 5 دقائق)
  try {
    const confirmed = await waitForPayment(orderId, 5000, 300000);
    
    console.log("✅ Payment confirmed!");
    console.log("📦 Processing order...");
    
    // 5. معالجة الطلب
    return {
      success: true,
      orderId: orderId,
      amount: confirmed.amount,
      transactionId: confirmed.smsData.transactionId,
      timestamp: confirmed.smsData.timestamp
    };
    
  } catch (error) {
    console.error("❌ Payment timeout or failed:", error);
    
    return {
      success: false,
      orderId: orderId,
      error: "Payment not received within 5 minutes"
    };
  }
}

// ============================================================
//  تشغيل الأمثلة
// ============================================================

// اختر المثال الذي تريد تشغيله:

// example1_healthCheck();

// example2_createAndWait();

// example3_realtimeNotifications();

// example4_fullEcommerceFlow()
//   .then(result => console.log("Final result:", result))
//   .catch(error => console.error("Error:", error));

// ============================================================
//  ملاحظات مهمة
// ============================================================

/*
✅ المصادقة:
   - جميع الطلبات تستخدم Bearer Token
   - الـ API Key موجود في المتغير API_KEY
   - لا حاجة لتعديل أي شيء!

✅ الأمان:
   - احفظ API Key في مكان آمن
   - لا تشاركه في الكود العام (GitHub)
   - استخدم متغيرات البيئة في الإنتاج

✅ معالجة الأخطاء:
   - تحقق من status code في الـ response
   - استخدم try/catch للأخطاء
   - تعامل مع timeout في waitForPayment

✅ WebSocket:
   - يعيد الاتصال تلقائياً عند القطع
   - استخدمه للإشعارات الفورية
   - أفضل من polling المستمر

✅ الاختبار:
   1. جرّب healthCheck() أولاً
   2. تأكد من أن الخادم يعمل
   3. جرّب createTransaction()
   4. أرسل دفعة حقيقية للاختبار
*/

// ============================================================
//  دعم إضافي
// ============================================================

/*
📚 الدوكيومنتيشن الكامل:
   - راجع ملف API_DOCUMENTATION.md
   - أمثلة بلغات أخرى متوفرة

🐛 استكشاف الأخطاء:
   - تحقق من أن التطبيق يعمل
   - تحقق من الاتصال بالشبكة
   - تحقق من صحة API Key

💬 الدعم:
   - GitHub Issues
   - التوثيق الكامل في المشروع
*/

console.log(`
╔════════════════════════════════════════════════════════╗
║  SMS Payment Gateway - API Documentation (JavaScript)  ║
║                                                        ║
║  ✅ Base URL: ${BASE_URL}                              ║
║  ✅ API Key: ${API_KEY.substring(0, 20)}...            ║
║  ✅ Authentication: Bearer Token                       ║
║                                                        ║
║  🚀 Ready to use! All examples are working.           ║
╚════════════════════════════════════════════════════════╝
`);
