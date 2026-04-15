/**
 * SMS Payment Gateway - Relay Server
 * 
 * Architecture:
 *   Website → HTTPS → Relay Server → WebSocket → Android App
 * 
 * The Android app connects here via WebSocket.
 * Any website can then call the REST API here, and requests are
 * forwarded to the Android device and responses sent back.
 */

const http = require('http');
const https = require('https');
const { WebSocketServer } = require('ws');
const { randomUUID } = require('crypto');

const PORT = process.env.PORT || 5000;

// ─── In-memory state ───────────────────────────────────────────────
// Map of deviceId → WebSocket connection
const connectedDevices = new Map();
// Map of requestId → { resolve, reject, timer } for pending HTTP calls
const pendingRequests = new Map();
// Map of apiKey → deviceId  (set when device authenticates)
const apiKeyToDevice = new Map();

const RELAY_VERSION = '1.0.0';
const REQUEST_TIMEOUT_MS = 30000; // 30 seconds

// ─── HTTP Server ───────────────────────────────────────────────────
const server = http.createServer((req, res) => {
  // CORS – allow any origin
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Api-Key');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  const url = new URL(req.url, `http://localhost:${PORT}`);
  const path = url.pathname;

  // ── Public endpoints (no auth) ──
  if (path === '/' && req.method === 'GET') {
    return serveDashboard(res);
  }

  if (path === '/relay/status' && req.method === 'GET') {
    return json(res, 200, {
      status: 'ok',
      version: RELAY_VERSION,
      connectedDevices: connectedDevices.size,
      uptime: process.uptime()
    });
  }

  // ── Device API endpoints – require API key ──
  if (path.startsWith('/api/v1/')) {
    return handleApiProxy(req, res, path);
  }

  json(res, 404, { error: 'Not found' });
});

// ─── WebSocket Server (for Android device) ─────────────────────────
const wss = new WebSocketServer({ server, path: '/device' });

wss.on('connection', (ws, req) => {
  const apiKey = req.headers['x-api-key'] || req.headers['authorization']?.replace('Bearer ', '');
  
  if (!apiKey) {
    ws.send(JSON.stringify({ type: 'error', message: 'Missing API key' }));
    ws.close(4001, 'Unauthorized');
    return;
  }

  const deviceId = randomUUID();
  ws.deviceId = deviceId;
  ws.apiKey = apiKey;
  ws.isAlive = true;
  ws.connectedAt = new Date().toISOString();

  connectedDevices.set(deviceId, ws);
  apiKeyToDevice.set(apiKey, deviceId);

  console.log(`[${new Date().toISOString()}] Device connected: ${deviceId} (total: ${connectedDevices.size})`);

  ws.send(JSON.stringify({
    type: 'connected',
    deviceId,
    message: 'Connected to relay server',
    relayVersion: RELAY_VERSION
  }));

  // Heartbeat
  ws.on('pong', () => { ws.isAlive = true; });

  ws.on('message', (data) => {
    try {
      const msg = JSON.parse(data.toString());
      handleDeviceMessage(ws, msg);
    } catch (e) {
      console.error('Invalid message from device:', e.message);
    }
  });

  ws.on('close', () => {
    connectedDevices.delete(deviceId);
    apiKeyToDevice.delete(apiKey);
    console.log(`[${new Date().toISOString()}] Device disconnected: ${deviceId} (total: ${connectedDevices.size})`);
    
    // Fail all pending requests for this device
    for (const [reqId, pending] of pendingRequests) {
      if (pending.deviceId === deviceId) {
        clearTimeout(pending.timer);
        pending.reject({ status: 503, body: { error: 'Device disconnected' } });
        pendingRequests.delete(reqId);
      }
    }
  });

  ws.on('error', (err) => {
    console.error('Device WebSocket error:', err.message);
  });
});

// Heartbeat interval – drop dead connections
const heartbeatInterval = setInterval(() => {
  wss.clients.forEach((ws) => {
    if (!ws.isAlive) {
      connectedDevices.delete(ws.deviceId);
      apiKeyToDevice.delete(ws.apiKey);
      return ws.terminate();
    }
    ws.isAlive = false;
    ws.ping();
  });
}, 30000);

wss.on('close', () => clearInterval(heartbeatInterval));

// ─── Handle messages from device ──────────────────────────────────
function handleDeviceMessage(ws, msg) {
  if (msg.type === 'response' && msg.requestId) {
    const pending = pendingRequests.get(msg.requestId);
    if (pending) {
      clearTimeout(pending.timer);
      pendingRequests.delete(msg.requestId);
      pending.resolve({ status: msg.status || 200, body: msg.body });
    }
  } else if (msg.type === 'ping') {
    ws.send(JSON.stringify({ type: 'pong' }));
  }
}

// ─── Forward HTTP → WebSocket → Device ────────────────────────────
async function handleApiProxy(req, res, path) {
  // Extract API key from Authorization header or X-Api-Key
  const apiKey = req.headers['authorization']?.replace('Bearer ', '') || req.headers['x-api-key'];

  if (!apiKey) {
    return json(res, 401, { error: 'Missing API key. Use Authorization: Bearer <key> or X-Api-Key header' });
  }

  // Find device by API key
  const deviceId = apiKeyToDevice.get(apiKey);
  if (!deviceId) {
    return json(res, 503, {
      error: 'Device not connected',
      hint: 'Make sure the Android app is running and connected to this relay server',
      connectedDevices: connectedDevices.size
    });
  }

  const deviceWs = connectedDevices.get(deviceId);
  if (!deviceWs || deviceWs.readyState !== 1) {
    apiKeyToDevice.delete(apiKey);
    connectedDevices.delete(deviceId);
    return json(res, 503, { error: 'Device connection lost' });
  }

  // Read request body
  let body = '';
  await new Promise((resolve) => {
    req.on('data', chunk => body += chunk);
    req.on('end', resolve);
  });

  const requestId = randomUUID();

  // Build the forwarded request
  const forwardedRequest = {
    type: 'request',
    requestId,
    method: req.method,
    path: path.replace('/api/v1', '/api/v1'), // keep as-is, device handles /api/v1/*
    query: new URL(req.url, `http://localhost`).search,
    headers: {
      'content-type': req.headers['content-type'] || 'application/json'
    },
    body: body || null
  };

  // Return promise that resolves when device responds
  try {
    const result = await new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        pendingRequests.delete(requestId);
        reject({ status: 504, body: { error: 'Device response timeout (30s)' } });
      }, REQUEST_TIMEOUT_MS);

      pendingRequests.set(requestId, { resolve, reject, timer, deviceId });
      deviceWs.send(JSON.stringify(forwardedRequest));
    });

    res.setHeader('Content-Type', 'application/json');
    res.writeHead(result.status);
    res.end(typeof result.body === 'string' ? result.body : JSON.stringify(result.body));
  } catch (err) {
    json(res, err.status || 500, err.body || { error: 'Internal relay error' });
  }
}

// ─── Helpers ──────────────────────────────────────────────────────
function json(res, status, data) {
  res.setHeader('Content-Type', 'application/json');
  res.writeHead(status);
  res.end(JSON.stringify(data));
}

function serveDashboard(res) {
  const devices = [];
  connectedDevices.forEach((ws, id) => {
    devices.push({ id, connectedAt: ws.connectedAt });
  });

  const html = `<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
  <title>SMS Payment Gateway - Relay Server</title>
  <style>
    *{box-sizing:border-box;margin:0;padding:0}
    body{font-family:'Segoe UI',sans-serif;background:#0f1117;color:#e1e4e8;min-height:100vh}
    header{background:linear-gradient(135deg,#1a1f2e,#16213e);border-bottom:1px solid #30363d;padding:20px 40px;display:flex;align-items:center;gap:16px}
    .logo{width:48px;height:48px;background:linear-gradient(135deg,#00b09b,#96c93d);border-radius:12px;display:flex;align-items:center;justify-content:center;font-size:24px}
    header h1{font-size:1.5rem;font-weight:700;color:#fff}
    header p{font-size:.85rem;color:#8b949e;margin-top:2px}
    .container{max-width:900px;margin:0 auto;padding:40px 20px}
    .stats{display:grid;grid-template-columns:repeat(3,1fr);gap:16px;margin-bottom:32px}
    .stat{background:#161b22;border:1px solid #30363d;border-radius:12px;padding:20px;text-align:center}
    .stat .num{font-size:2rem;font-weight:700;color:#58a6ff}
    .stat .num.green{color:#3fb950}
    .stat .num.red{color:#f85149}
    .stat .label{font-size:.8rem;color:#8b949e;margin-top:4px}
    .card{background:#161b22;border:1px solid #30363d;border-radius:12px;padding:24px;margin-bottom:20px}
    .card h2{font-size:1.1rem;font-weight:700;color:#fff;margin-bottom:16px;padding-bottom:12px;border-bottom:1px solid #30363d}
    .endpoint{display:flex;align-items:center;gap:10px;padding:10px 0;border-bottom:1px solid #21262d}
    .endpoint:last-child{border-bottom:none}
    .method{padding:3px 10px;border-radius:4px;font-size:.75rem;font-weight:700;min-width:50px;text-align:center}
    .get{background:#1f6feb33;color:#58a6ff}
    .post{background:#2ea04333;color:#3fb950}
    .ws{background:#bc8cff33;color:#bc8cff}
    .path{font-family:monospace;font-size:.85rem;color:#e6edf3;flex:1}
    .desc{font-size:.78rem;color:#8b949e}
    .device-item{background:#21262d;border-radius:8px;padding:12px 16px;margin-bottom:8px;display:flex;justify-content:space-between;align-items:center}
    .device-id{font-family:monospace;font-size:.8rem;color:#79c0ff}
    .device-time{font-size:.75rem;color:#8b949e}
    .online{width:8px;height:8px;border-radius:50%;background:#3fb950;display:inline-block;margin-left:6px;animation:pulse 2s infinite}
    @keyframes pulse{0%,100%{opacity:1}50%{opacity:.4}}
    .code{background:#0d1117;border:1px solid #30363d;border-radius:8px;padding:16px;margin:12px 0;font-family:monospace;font-size:.8rem;color:#e6edf3;overflow-x:auto;white-space:pre-wrap}
    .badge{background:#238636;color:#fff;font-size:.7rem;padding:2px 8px;border-radius:12px;margin-right:8px}
    @media(max-width:600px){.stats{grid-template-columns:1fr}}
  </style>
</head>
<body>
<header>
  <div class="logo">🔗</div>
  <div>
    <h1>SMS Payment Gateway <span class="badge">Relay Server</span></h1>
    <p>خادم الوسيط — يربط موقعك بتطبيق Android عبر HTTPS</p>
  </div>
</header>
<div class="container">
  <div class="stats">
    <div class="stat">
      <div class="num ${devices.length > 0 ? 'green' : 'red'}">${devices.length}</div>
      <div class="label">أجهزة متصلة</div>
    </div>
    <div class="stat">
      <div class="num">${pendingRequests.size}</div>
      <div class="label">طلبات معلقة</div>
    </div>
    <div class="stat">
      <div class="num">${Math.floor(process.uptime() / 60)}</div>
      <div class="label">دقيقة وقت التشغيل</div>
    </div>
  </div>

  <div class="card">
    <h2>📡 الأجهزة المتصلة</h2>
    ${devices.length === 0
      ? '<p style="color:#8b949e;text-align:center;padding:20px">لا يوجد جهاز متصل حالياً. افتح تطبيق Android واضبط رابط الـ Relay.</p>'
      : devices.map(d => `
        <div class="device-item">
          <span class="online"></span>
          <span class="device-id">${d.id.substring(0, 16)}...</span>
          <span class="device-time">${new Date(d.connectedAt).toLocaleString('ar-EG')}</span>
        </div>`).join('')
    }
  </div>

  <div class="card">
    <h2>🌐 API Endpoints للمواقع</h2>
    <div class="endpoint">
      <span class="method get">GET</span>
      <span class="path">/relay/status</span>
      <span class="desc">حالة خادم الوسيط</span>
    </div>
    <div class="endpoint">
      <span class="method post">POST</span>
      <span class="path">/api/v1/transactions</span>
      <span class="desc">إنشاء معاملة دفع جديدة</span>
    </div>
    <div class="endpoint">
      <span class="method get">GET</span>
      <span class="path">/api/v1/transactions/{id}</span>
      <span class="desc">حالة معاملة محددة</span>
    </div>
    <div class="endpoint">
      <span class="method get">GET</span>
      <span class="path">/api/v1/transactions</span>
      <span class="desc">جميع المعاملات</span>
    </div>
    <div class="endpoint">
      <span class="method get">GET</span>
      <span class="path">/api/v1/sms/logs</span>
      <span class="desc">سجلات SMS</span>
    </div>
    <div class="endpoint">
      <span class="method get">GET</span>
      <span class="path">/api/v1/health</span>
      <span class="desc">حالة تطبيق Android</span>
    </div>
    <div class="endpoint">
      <span class="method ws">WS</span>
      <span class="path">/device</span>
      <span class="desc">اتصال WebSocket للتطبيق Android</span>
    </div>
  </div>

  <div class="card">
    <h2>💻 مثال الاستخدام من موقعك</h2>
    <div class="code">// JavaScript - إنشاء معاملة دفع
const response = await fetch('${process.env.REPLIT_DEV_DOMAIN ? 'https://' + process.env.REPLIT_DEV_DOMAIN : 'https://your-relay.replit.app'}/api/v1/transactions', {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer YOUR_DEVICE_API_KEY',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    id: 'order-' + Date.now(),
    amount: 500.00,
    phoneNumber: '01012345678',
    expiresInMinutes: 30
  })
});
const data = await response.json();
console.log(data); // { success: true, transaction: {...} }</div>
  </div>

  <div class="card">
    <h2>📱 إعداد تطبيق Android</h2>
    <p style="color:#8b949e;margin-bottom:12px">في إعدادات التطبيق، أدخل رابط خادم الوسيط:</p>
    <div class="code">${process.env.REPLIT_DEV_DOMAIN ? 'wss://' + process.env.REPLIT_DEV_DOMAIN + '/device' : 'wss://your-relay.replit.app/device'}</div>
  </div>
</div>
<script>setTimeout(() => location.reload(), 10000)</script>
</body>
</html>`;

  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.writeHead(200);
  res.end(html);
}

// ─── Start ─────────────────────────────────────────────────────────
server.listen(PORT, '0.0.0.0', () => {
  console.log(`[${new Date().toISOString()}] SMS Payment Gateway Relay Server v${RELAY_VERSION}`);
  console.log(`[${new Date().toISOString()}] Listening on port ${PORT}`);
  console.log(`[${new Date().toISOString()}] WebSocket endpoint: ws://0.0.0.0:${PORT}/device`);
  console.log(`[${new Date().toISOString()}] API endpoint: http://0.0.0.0:${PORT}/api/v1/*`);
});
