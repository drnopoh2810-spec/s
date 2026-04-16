// ═══════════════════════════════════════════════════════════
//  SMS Payment Gateway - Relay Server
//  يعمل كـ Proxy بين التطبيق والموقع
// ═══════════════════════════════════════════════════════════

const express = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');
const WebSocket = require('ws');
const http = require('http');

const app = express();
const PORT = process.env.PORT || 3000;

// إنشاء HTTP Server
const server = http.createServer(app);

// إنشاء WebSocket Server
const wss = new WebSocket.Server({ server });

// تخزين اتصالات الأجهزة
const devices = new Map();

// ═══════════════════════════════════════════════════════════
//  WebSocket - اتصال الأجهزة
// ═══════════════════════════════════════════════════════════

wss.on('connection', (ws, req) => {
    const deviceId = req.url.split('/').pop() || 'default';
    
    console.log(`📱 Device connected: ${deviceId}`);
    
    devices.set(deviceId, {
        ws,
        connected: true,
        connectedAt: new Date()
    });

    // Heartbeat
    ws.isAlive = true;
    ws.on('pong', () => { ws.isAlive = true; });

    ws.on('message', (message) => {
        try {
            const data = JSON.parse(message);
            console.log(`📨 Message from ${deviceId}:`, data.type);
        } catch (e) {
            console.error('Invalid message:', e);
        }
    });

    ws.on('close', () => {
        console.log(`📱 Device disconnected: ${deviceId}`);
        devices.delete(deviceId);
    });

    ws.on('error', (error) => {
        console.error(`❌ WebSocket error for ${deviceId}:`, error);
        devices.delete(deviceId);
    });

    // إرسال رسالة ترحيب
    ws.send(JSON.stringify({
        type: 'connected',
        deviceId,
        publicUrl: `https://${req.headers.host}/api/${deviceId}`
    }));
});

// Heartbeat للحفاظ على الاتصال
setInterval(() => {
    wss.clients.forEach((ws) => {
        if (ws.isAlive === false) {
            return ws.terminate();
        }
        ws.isAlive = false;
        ws.ping();
    });
}, 30000);

// ═══════════════════════════════════════════════════════════
//  HTTP Proxy - توجيه الطلبات للأجهزة
// ═══════════════════════════════════════════════════════════

app.use(express.json());

// Health Check
app.get('/health', (req, res) => {
    res.json({
        status: 'ok',
        devices: devices.size,
        uptime: process.uptime()
    });
});

// معلومات الأجهزة المتصلة
app.get('/devices', (req, res) => {
    const deviceList = Array.from(devices.entries()).map(([id, device]) => ({
        id,
        connected: device.connected,
        connectedAt: device.connectedAt,
        publicUrl: `https://${req.headers.host}/api/${id}`
    }));
    
    res.json({
        count: devices.size,
        devices: deviceList
    });
});

// Proxy للطلبات
app.all('/api/:deviceId/*', async (req, res) => {
    const deviceId = req.params.deviceId;
    const path = req.params[0];
    
    const device = devices.get(deviceId);
    
    if (!device || !device.connected) {
        return res.status(503).json({
            error: 'Device not connected',
            deviceId
        });
    }

    try {
        // إرسال الطلب للجهاز عبر WebSocket
        const requestId = Date.now().toString();
        
        const request = {
            type: 'http_request',
            requestId,
            method: req.method,
            path: '/' + path,
            headers: req.headers,
            body: req.body,
            query: req.query
        };

        // إرسال للجهاز
        device.ws.send(JSON.stringify(request));

        // انتظار الرد (مع timeout)
        const timeout = setTimeout(() => {
            res.status(504).json({ error: 'Gateway timeout' });
        }, 30000);

        // استقبال الرد
        const responseHandler = (message) => {
            try {
                const data = JSON.parse(message);
                
                if (data.requestId === requestId && data.type === 'http_response') {
                    clearTimeout(timeout);
                    device.ws.removeListener('message', responseHandler);
                    
                    res.status(data.statusCode || 200)
                       .set(data.headers || {})
                       .send(data.body);
                }
            } catch (e) {
                console.error('Response parse error:', e);
            }
        };

        device.ws.on('message', responseHandler);

    } catch (error) {
        console.error('Proxy error:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
});

// الصفحة الرئيسية
app.get('/', (req, res) => {
    res.send(`
        <!DOCTYPE html>
        <html dir="rtl" lang="ar">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>SMS Payment Gateway - Relay Server</title>
            <style>
                body {
                    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                    max-width: 800px;
                    margin: 50px auto;
                    padding: 20px;
                    background: #f5f5f5;
                }
                .card {
                    background: white;
                    padding: 30px;
                    border-radius: 10px;
                    box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                    margin-bottom: 20px;
                }
                h1 { color: #2c3e50; margin-top: 0; }
                .status {
                    display: inline-block;
                    padding: 5px 15px;
                    border-radius: 20px;
                    background: #27ae60;
                    color: white;
                    font-weight: bold;
                }
                .info { color: #7f8c8d; margin: 10px 0; }
                code {
                    background: #ecf0f1;
                    padding: 2px 8px;
                    border-radius: 3px;
                    font-family: 'Courier New', monospace;
                }
                .devices {
                    margin-top: 20px;
                }
                .device {
                    background: #ecf0f1;
                    padding: 15px;
                    margin: 10px 0;
                    border-radius: 5px;
                }
            </style>
        </head>
        <body>
            <div class="card">
                <h1>🌐 SMS Payment Gateway Relay</h1>
                <div class="status">✓ يعمل</div>
                <p class="info">الأجهزة المتصلة: <strong id="deviceCount">0</strong></p>
            </div>

            <div class="card">
                <h2>📱 كيفية الاتصال</h2>
                <p>في التطبيق، اذهب إلى الإعدادات وأدخل:</p>
                <code>wss://${req.headers.host}/device/YOUR_DEVICE_ID</code>
            </div>

            <div class="card">
                <h2>🔗 الأجهزة المتصلة</h2>
                <div id="devices" class="devices">
                    <p class="info">جاري التحميل...</p>
                </div>
            </div>

            <script>
                async function loadDevices() {
                    try {
                        const res = await fetch('/devices');
                        const data = await res.json();
                        
                        document.getElementById('deviceCount').textContent = data.count;
                        
                        const devicesDiv = document.getElementById('devices');
                        if (data.count === 0) {
                            devicesDiv.innerHTML = '<p class="info">لا توجد أجهزة متصلة حالياً</p>';
                        } else {
                            devicesDiv.innerHTML = data.devices.map(d => \`
                                <div class="device">
                                    <strong>📱 \${d.id}</strong><br>
                                    <small>متصل منذ: \${new Date(d.connectedAt).toLocaleString('ar-EG')}</small><br>
                                    <code>\${d.publicUrl}</code>
                                </div>
                            \`).join('');
                        }
                    } catch (e) {
                        console.error('Error loading devices:', e);
                    }
                }

                loadDevices();
                setInterval(loadDevices, 5000);
            </script>
        </body>
        </html>
    `);
});

// ═══════════════════════════════════════════════════════════
//  تشغيل السيرفر
// ═══════════════════════════════════════════════════════════

server.listen(PORT, () => {
    console.log(`🚀 Relay Server running on port ${PORT}`);
    console.log(`📱 WebSocket: ws://localhost:${PORT}/device/YOUR_DEVICE_ID`);
    console.log(`🌐 HTTP: http://localhost:${PORT}`);
});
