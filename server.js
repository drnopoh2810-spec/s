const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 5000;

const html = `<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
  <title>SMS Payment Gateway - توثيق API</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: 'Segoe UI', Tahoma, sans-serif; background: #0f1117; color: #e1e4e8; min-height: 100vh; }
    header { background: linear-gradient(135deg, #1a1f2e 0%, #16213e 100%); border-bottom: 1px solid #30363d; padding: 20px 40px; display: flex; align-items: center; gap: 16px; }
    .logo { width: 48px; height: 48px; background: linear-gradient(135deg, #00b09b, #96c93d); border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 24px; }
    header h1 { font-size: 1.5rem; font-weight: 700; color: #fff; }
    header p { font-size: 0.85rem; color: #8b949e; margin-top: 2px; }
    .badge { background: #238636; color: #fff; font-size: 0.7rem; padding: 2px 8px; border-radius: 12px; margin-right: 8px; }
    .container { max-width: 1100px; margin: 0 auto; padding: 40px 20px; }
    .grid { display: grid; grid-template-columns: 260px 1fr; gap: 32px; }
    .sidebar { position: sticky; top: 20px; height: fit-content; }
    .sidebar h3 { font-size: 0.75rem; text-transform: uppercase; letter-spacing: 1px; color: #8b949e; margin-bottom: 12px; padding: 0 8px; }
    .nav-item { display: block; padding: 8px 12px; border-radius: 8px; color: #c9d1d9; text-decoration: none; font-size: 0.9rem; transition: all 0.2s; margin-bottom: 2px; cursor: pointer; border: none; background: none; width: 100%; text-align: right; }
    .nav-item:hover, .nav-item.active { background: #21262d; color: #58a6ff; }
    .method-badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 0.7rem; font-weight: 700; margin-left: 8px; }
    .get { background: #1f6feb33; color: #58a6ff; }
    .post { background: #2ea04333; color: #3fb950; }
    .section { background: #161b22; border: 1px solid #30363d; border-radius: 12px; padding: 28px; margin-bottom: 24px; }
    .section h2 { font-size: 1.2rem; font-weight: 700; color: #fff; margin-bottom: 16px; padding-bottom: 12px; border-bottom: 1px solid #30363d; }
    .endpoint-header { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
    .endpoint-path { font-family: 'Courier New', monospace; font-size: 0.95rem; color: #e6edf3; background: #0d1117; padding: 8px 16px; border-radius: 8px; flex: 1; border: 1px solid #30363d; }
    p { color: #8b949e; font-size: 0.9rem; line-height: 1.6; margin-bottom: 12px; }
    .code-block { background: #0d1117; border: 1px solid #30363d; border-radius: 8px; padding: 16px; margin: 12px 0; overflow-x: auto; }
    .code-block pre { font-family: 'Courier New', monospace; font-size: 0.82rem; color: #e6edf3; line-height: 1.6; white-space: pre-wrap; }
    .param-table { width: 100%; border-collapse: collapse; margin: 12px 0; font-size: 0.85rem; }
    .param-table th { text-align: right; padding: 8px 12px; background: #21262d; color: #8b949e; font-weight: 600; font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.5px; }
    .param-table td { padding: 8px 12px; border-top: 1px solid #21262d; color: #c9d1d9; vertical-align: top; }
    .param-table td code { background: #21262d; padding: 2px 6px; border-radius: 4px; font-family: monospace; font-size: 0.8rem; color: #79c0ff; }
    .required { color: #f85149; font-size: 0.7rem; } 
    .optional { color: #8b949e; font-size: 0.7rem; }
    .status-list { display: flex; flex-wrap: wrap; gap: 8px; margin: 12px 0; }
    .status-chip { padding: 4px 12px; border-radius: 20px; font-size: 0.78rem; font-weight: 600; }
    .pending { background: #9e6a0333; color: #e3b341; }
    .matched { background: #2ea04333; color: #3fb950; }
    .expired { background: #6e768133; color: #8b949e; }
    .cancelled { background: #f8514933; color: #f85149; }
    .info-box { background: #1f6feb1a; border: 1px solid #1f6feb55; border-radius: 8px; padding: 14px 16px; margin: 12px 0; font-size: 0.85rem; color: #79c0ff; }
    .stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 32px; }
    .stat-card { background: #161b22; border: 1px solid #30363d; border-radius: 12px; padding: 20px; text-align: center; }
    .stat-card .num { font-size: 2rem; font-weight: 700; color: #58a6ff; }
    .stat-card .label { font-size: 0.8rem; color: #8b949e; margin-top: 4px; }
    .wallet-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; margin: 12px 0; }
    .wallet-item { background: #21262d; border-radius: 8px; padding: 10px 14px; font-size: 0.85rem; display: flex; align-items: center; gap: 8px; }
    .wallet-dot { width: 8px; height: 8px; border-radius: 50%; }
    @media (max-width: 768px) { .grid { grid-template-columns: 1fr; } .sidebar { display: none; } .stats { grid-template-columns: 1fr; } .wallet-grid { grid-template-columns: 1fr; } }
  </style>
</head>
<body>
<header>
  <div class="logo">📱</div>
  <div>
    <h1>SMS Payment Gateway <span class="badge">v1.0</span></h1>
    <p>بوابة دفع SMS الذكية للمحافظ الإلكترونية المصرية</p>
  </div>
</header>

<div class="container">
  <div class="stats">
    <div class="stat-card"><div class="num">5</div><div class="label">نقاط API</div></div>
    <div class="stat-card"><div class="num">5</div><div class="label">محافظ مدعومة</div></div>
    <div class="stat-card"><div class="num">8080</div><div class="label">المنفذ المحلي</div></div>
  </div>

  <div class="grid">
    <div class="sidebar">
      <h3>التنقل</h3>
      <a class="nav-item active" href="#overview">نظرة عامة</a>
      <a class="nav-item" href="#auth">المصادقة</a>
      <a class="nav-item" href="#health"><span class="method-badge get">GET</span> فحص الصحة</a>
      <a class="nav-item" href="#create-tx"><span class="method-badge post">POST</span> إنشاء معاملة</a>
      <a class="nav-item" href="#get-tx"><span class="method-badge get">GET</span> حالة المعاملة</a>
      <a class="nav-item" href="#list-tx"><span class="method-badge get">GET</span> قائمة المعاملات</a>
      <a class="nav-item" href="#sms-logs"><span class="method-badge get">GET</span> سجلات SMS</a>
      <a class="nav-item" href="#wallets">المحافظ المدعومة</a>
      <a class="nav-item" href="#webhook">Webhook</a>
    </div>

    <div>
      <div class="section" id="overview">
        <h2>🏠 نظرة عامة</h2>
        <p>SMS Payment Gateway هو تطبيق Android يعمل كوسيط ذكي لأتمتة تأكيد المدفوعات عبر المحافظ الإلكترونية المصرية. يشغل خادم API محلي على المنفذ <code style="background:#21262d;padding:2px 6px;border-radius:4px;font-family:monospace;color:#79c0ff">8080</code> يمكن لأي موقع الاتصال به.</p>
        <div class="info-box">
          📌 عنوان API الأساسي: <strong>http://&lt;phone-ip&gt;:8080/api/v1</strong>
        </div>
        <p>طريقة العمل: يقوم موقعك بإنشاء معاملة معلقة → يستلم الهاتف رسالة SMS من المحفظة → يطابق التطبيق الرسالة مع المعاملة → يرسل Webhook لموقعك بتأكيد الدفع.</p>
      </div>

      <div class="section" id="auth">
        <h2>🔐 المصادقة</h2>
        <p>جميع الطلبات تتطلب مفتاح API في رأس Authorization:</p>
        <div class="code-block">
          <pre>Authorization: Bearer YOUR_API_KEY</pre>
        </div>
        <p>احصل على مفتاح API من الشاشة الرئيسية للتطبيق.</p>
      </div>

      <div class="section" id="health">
        <h2>❤️ فحص الصحة</h2>
        <div class="endpoint-header">
          <span class="method-badge get" style="font-size:0.85rem;padding:4px 12px;">GET</span>
          <span class="endpoint-path">/health</span>
        </div>
        <p>التحقق من أن الخدمة تعمل بشكل صحيح.</p>
        <div class="code-block">
          <pre>{
  "status": "ok",
  "timestamp": 1705329000000,
  "service": "SMS Payment Gateway"
}</pre>
        </div>
      </div>

      <div class="section" id="create-tx">
        <h2>➕ إنشاء معاملة معلقة</h2>
        <div class="endpoint-header">
          <span class="method-badge post" style="font-size:0.85rem;padding:4px 12px;">POST</span>
          <span class="endpoint-path">/transactions</span>
        </div>
        <p>إضافة معاملة دفع جديدة وانتظار تأكيد SMS.</p>
        <table class="param-table">
          <tr><th>الحقل</th><th>النوع</th><th>الحالة</th><th>الوصف</th></tr>
          <tr><td><code>id</code></td><td>string</td><td><span class="required">مطلوب</span></td><td>معرّف فريد للمعاملة</td></tr>
          <tr><td><code>amount</code></td><td>number</td><td><span class="required">مطلوب</span></td><td>المبلغ المتوقع</td></tr>
          <tr><td><code>phoneNumber</code></td><td>string</td><td><span class="required">مطلوب</span></td><td>رقم الهاتف المُرسِل</td></tr>
          <tr><td><code>expectedTxId</code></td><td>string</td><td><span class="optional">اختياري</span></td><td>رقم عملية المحفظة المتوقع</td></tr>
          <tr><td><code>walletType</code></td><td>string</td><td><span class="optional">اختياري</span></td><td>نوع المحفظة</td></tr>
          <tr><td><code>expiresInMinutes</code></td><td>number</td><td><span class="optional">اختياري</span></td><td>مدة الانتهاء (افتراضي: 30)</td></tr>
        </table>
        <div class="code-block">
          <pre>// طلب
{
  "id": "order-12345",
  "amount": 500.00,
  "phoneNumber": "01012345678",
  "walletType": "VODAFONE_CASH",
  "expiresInMinutes": 30
}

// استجابة
{
  "success": true,
  "transaction": {
    "id": "order-12345",
    "amount": 500.00,
    "status": "PENDING",
    "createdAt": "2024-01-15T14:30:00Z"
  }
}</pre>
        </div>
      </div>

      <div class="section" id="get-tx">
        <h2>🔍 حالة المعاملة</h2>
        <div class="endpoint-header">
          <span class="method-badge get" style="font-size:0.85rem;padding:4px 12px;">GET</span>
          <span class="endpoint-path">/transactions/{id}</span>
        </div>
        <p>الاستعلام عن حالة معاملة محددة.</p>
        <div class="status-list">
          <span class="status-chip pending">PENDING - منتظر</span>
          <span class="status-chip matched">MATCHED - مطابق</span>
          <span class="status-chip expired">EXPIRED - منتهي</span>
          <span class="status-chip cancelled">CANCELLED - ملغي</span>
        </div>
        <div class="code-block">
          <pre>{
  "id": "order-12345",
  "amount": 500.00,
  "status": "MATCHED",
  "confidence": 0.98,
  "matchedAt": "2024-01-15T14:30:05Z"
}</pre>
        </div>
      </div>

      <div class="section" id="list-tx">
        <h2>📋 قائمة المعاملات</h2>
        <div class="endpoint-header">
          <span class="method-badge get" style="font-size:0.85rem;padding:4px 12px;">GET</span>
          <span class="endpoint-path">/transactions</span>
        </div>
        <p>الحصول على جميع المعاملات المعلقة.</p>
      </div>

      <div class="section" id="sms-logs">
        <h2>📨 سجلات SMS</h2>
        <div class="endpoint-header">
          <span class="method-badge get" style="font-size:0.85rem;padding:4px 12px;">GET</span>
          <span class="endpoint-path">/sms/logs</span>
        </div>
        <p>عرض جميع رسائل SMS المستلمة والمعالجة.</p>
      </div>

      <div class="section" id="wallets">
        <h2>💳 المحافظ المدعومة</h2>
        <div class="wallet-grid">
          <div class="wallet-item"><span class="wallet-dot" style="background:#ee1c2e"></span>Vodafone Cash</div>
          <div class="wallet-item"><span class="wallet-dot" style="background:#ff6600"></span>Orange Money</div>
          <div class="wallet-item"><span class="wallet-dot" style="background:#00b0f0"></span>Etisalat Cash (WE)</div>
          <div class="wallet-item"><span class="wallet-dot" style="background:#007dc5"></span>Fawry</div>
          <div class="wallet-item"><span class="wallet-dot" style="background:#6c3cbf"></span>InstaPay</div>
        </div>
      </div>

      <div class="section" id="webhook">
        <h2>🔔 Webhook</h2>
        <p>عند تأكيد الدفع، يرسل التطبيق تلقائياً طلب POST إلى Webhook URL المضبوط مع توقيع HMAC-SHA256.</p>
        <div class="code-block">
          <pre>// Webhook Payload
{
  "event": "PAYMENT_CONFIRMED",
  "transactionId": "order-12345",
  "smsData": {
    "walletType": "VODAFONE_CASH",
    "amount": 500.00,
    "senderPhone": "01012345678",
    "timestamp": 1705329000000
  },
  "confidence": 0.98,
  "processedAt": 1705329005000
}

// Headers
X-Signature: hmac-sha256-signature</pre>
        </div>
      </div>
    </div>
  </div>
</div>
</body>
</html>`;

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(html);
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`SMS Payment Gateway - API Docs running on port ${PORT}`);
});
