"""
═══════════════════════════════════════════════════════════
 SMS Payment Gateway - Relay Server for Hugging Face
 يعمل 24/7 بدون انقطاع
═══════════════════════════════════════════════════════════
"""

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Request, HTTPException
from fastapi.responses import HTMLResponse, JSONResponse
from fastapi.middleware.cors import CORSMiddleware
import asyncio
import json
import time
from datetime import datetime
from typing import Dict, Optional
import logging

# إعداد Logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# إنشاء FastAPI App
app = FastAPI(
    title="SMS Gateway Relay",
    description="Relay server for SMS Payment Gateway - Always Online",
    version="1.0.0"
)

# CORS - السماح لجميع المصادر
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# تخزين الأجهزة المتصلة
devices: Dict[str, dict] = {}

# تخزين الطلبات المعلقة
pending_requests: Dict[str, dict] = {}


# ═══════════════════════════════════════════════════════════
#  WebSocket - اتصال الأجهزة
# ═══════════════════════════════════════════════════════════

@app.websocket("/device/{device_id}")
async def websocket_device(websocket: WebSocket, device_id: str):
    """اتصال WebSocket للأجهزة"""
    await websocket.accept()
    
    logger.info(f"📱 Device connected: {device_id}")
    
    # تخزين معلومات الجهاز
    devices[device_id] = {
        "websocket": websocket,
        "connected": True,
        "connected_at": datetime.now().isoformat(),
        "last_heartbeat": time.time()
    }
    
    # إرسال رسالة ترحيب
    try:
        await websocket.send_json({
            "type": "connected",
            "device_id": device_id,
            "public_url": f"/api/{device_id}",
            "message": "Connected successfully to relay server"
        })
    except Exception as e:
        logger.error(f"Error sending welcome message: {e}")
    
    try:
        # Heartbeat task
        async def heartbeat():
            while device_id in devices:
                try:
                    await websocket.send_json({"type": "ping"})
                    await asyncio.sleep(30)
                except:
                    break
        
        heartbeat_task = asyncio.create_task(heartbeat())
        
        # استقبال الرسائل
        while True:
            try:
                data = await websocket.receive_text()
                message = json.loads(data)
                
                # تحديث آخر heartbeat
                if device_id in devices:
                    devices[device_id]["last_heartbeat"] = time.time()
                
                # معالجة الرسائل
                msg_type = message.get("type")
                
                if msg_type == "pong":
                    # Heartbeat response
                    pass
                
                elif msg_type == "http_response":
                    # رد على طلب HTTP
                    request_id = message.get("request_id")
                    if request_id in pending_requests:
                        pending_requests[request_id]["response"] = message
                        pending_requests[request_id]["event"].set()
                
                else:
                    logger.info(f"📨 Message from {device_id}: {msg_type}")
                    
            except WebSocketDisconnect:
                break
            except json.JSONDecodeError:
                logger.error(f"Invalid JSON from {device_id}")
            except Exception as e:
                logger.error(f"Error receiving message: {e}")
                break
        
        heartbeat_task.cancel()
        
    except Exception as e:
        logger.error(f"WebSocket error for {device_id}: {e}")
    
    finally:
        # تنظيف عند قطع الاتصال
        if device_id in devices:
            del devices[device_id]
        logger.info(f"📱 Device disconnected: {device_id}")


# ═══════════════════════════════════════════════════════════
#  HTTP Proxy - توجيه الطلبات للأجهزة
# ═══════════════════════════════════════════════════════════

@app.api_route("/api/{device_id}/{path:path}", methods=["GET", "POST", "PUT", "DELETE", "PATCH"])
async def proxy_request(device_id: str, path: str, request: Request):
    """توجيه طلبات HTTP للأجهزة عبر WebSocket"""
    
    # التحقق من اتصال الجهاز
    if device_id not in devices or not devices[device_id]["connected"]:
        raise HTTPException(
            status_code=503,
            detail=f"Device '{device_id}' is not connected"
        )
    
    device = devices[device_id]
    websocket = device["websocket"]
    
    try:
        # قراءة Body
        body = None
        try:
            body = await request.json()
        except:
            try:
                body = (await request.body()).decode()
            except:
                pass
        
        # إنشاء Request ID
        request_id = f"{device_id}_{int(time.time() * 1000)}"
        
        # إعداد الطلب
        http_request = {
            "type": "http_request",
            "request_id": request_id,
            "method": request.method,
            "path": f"/{path}",
            "headers": dict(request.headers),
            "query": dict(request.query_params),
            "body": body
        }
        
        # إنشاء Event للانتظار
        event = asyncio.Event()
        pending_requests[request_id] = {
            "event": event,
            "response": None
        }
        
        # إرسال الطلب للجهاز
        await websocket.send_json(http_request)
        
        # انتظار الرد (مع timeout)
        try:
            await asyncio.wait_for(event.wait(), timeout=30.0)
        except asyncio.TimeoutError:
            del pending_requests[request_id]
            raise HTTPException(status_code=504, detail="Gateway timeout")
        
        # الحصول على الرد
        response_data = pending_requests[request_id]["response"]
        del pending_requests[request_id]
        
        if not response_data:
            raise HTTPException(status_code=500, detail="No response from device")
        
        # إرجاع الرد
        return JSONResponse(
            content=response_data.get("body", {}),
            status_code=response_data.get("status_code", 200),
            headers=response_data.get("headers", {})
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Proxy error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ═══════════════════════════════════════════════════════════
#  Monitoring & Health
# ═══════════════════════════════════════════════════════════

@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {
        "status": "ok",
        "devices": len(devices),
        "uptime": time.time(),
        "timestamp": datetime.now().isoformat()
    }


@app.get("/devices")
async def list_devices():
    """قائمة الأجهزة المتصلة"""
    device_list = []
    
    for device_id, device in devices.items():
        device_list.append({
            "id": device_id,
            "connected": device["connected"],
            "connected_at": device["connected_at"],
            "last_heartbeat": device["last_heartbeat"],
            "public_url": f"/api/{device_id}"
        })
    
    return {
        "count": len(devices),
        "devices": device_list
    }


@app.get("/", response_class=HTMLResponse)
async def root():
    """الصفحة الرئيسية"""
    return """
    <!DOCTYPE html>
    <html dir="rtl" lang="ar">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>SMS Gateway Relay - Hugging Face</title>
        <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body {
                font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                min-height: 100vh;
                display: flex;
                align-items: center;
                justify-content: center;
                padding: 20px;
            }
            .container {
                max-width: 800px;
                width: 100%;
            }
            .card {
                background: white;
                border-radius: 20px;
                padding: 40px;
                box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                margin-bottom: 20px;
                animation: slideUp 0.5s ease;
            }
            @keyframes slideUp {
                from { opacity: 0; transform: translateY(30px); }
                to { opacity: 1; transform: translateY(0); }
            }
            h1 {
                color: #2c3e50;
                margin-bottom: 10px;
                font-size: 2em;
            }
            .status {
                display: inline-block;
                padding: 8px 20px;
                border-radius: 25px;
                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                color: white;
                font-weight: bold;
                margin: 20px 0;
                animation: pulse 2s infinite;
            }
            @keyframes pulse {
                0%, 100% { transform: scale(1); }
                50% { transform: scale(1.05); }
            }
            .info {
                background: #f8f9fa;
                padding: 20px;
                border-radius: 10px;
                margin: 15px 0;
                border-left: 4px solid #667eea;
            }
            .info strong {
                color: #667eea;
                font-size: 1.5em;
            }
            code {
                background: #2c3e50;
                color: #ecf0f1;
                padding: 3px 10px;
                border-radius: 5px;
                font-family: 'Courier New', monospace;
                font-size: 0.9em;
            }
            .devices {
                margin-top: 20px;
            }
            .device {
                background: linear-gradient(135deg, #667eea15 0%, #764ba215 100%);
                padding: 20px;
                margin: 10px 0;
                border-radius: 10px;
                border: 2px solid #667eea;
            }
            .device strong {
                color: #667eea;
                font-size: 1.2em;
            }
            .footer {
                text-align: center;
                color: white;
                margin-top: 20px;
                font-size: 0.9em;
            }
            .emoji {
                font-size: 1.5em;
                margin-left: 10px;
            }
        </style>
    </head>
    <body>
        <div class="container">
            <div class="card">
                <h1><span class="emoji">🌐</span>SMS Gateway Relay</h1>
                <div class="status">✓ يعمل على Hugging Face</div>
                
                <div class="info">
                    <p>الأجهزة المتصلة: <strong id="deviceCount">0</strong></p>
                </div>
            </div>

            <div class="card">
                <h2><span class="emoji">📱</span>كيفية الاتصال</h2>
                <p style="margin: 15px 0;">في التطبيق، اذهب إلى الإعدادات وأدخل:</p>
                <code id="wsUrl">wss://YOUR_SPACE.hf.space/device/YOUR_DEVICE_ID</code>
            </div>

            <div class="card">
                <h2><span class="emoji">🔗</span>الأجهزة المتصلة</h2>
                <div id="devices" class="devices">
                    <p style="color: #7f8c8d;">جاري التحميل...</p>
                </div>
            </div>
            
            <div class="footer">
                <p>🚀 Powered by Hugging Face Spaces</p>
                <p>⚡ Always Online - No Sleep Mode</p>
            </div>
        </div>

        <script>
            // تحديث WebSocket URL
            const wsUrl = window.location.host;
            document.getElementById('wsUrl').textContent = `wss://${wsUrl}/device/YOUR_DEVICE_ID`;
            
            async function loadDevices() {
                try {
                    const res = await fetch('/devices');
                    const data = await res.json();
                    
                    document.getElementById('deviceCount').textContent = data.count;
                    
                    const devicesDiv = document.getElementById('devices');
                    if (data.count === 0) {
                        devicesDiv.innerHTML = '<p style="color: #7f8c8d;">لا توجد أجهزة متصلة حالياً</p>';
                    } else {
                        devicesDiv.innerHTML = data.devices.map(d => `
                            <div class="device">
                                <strong>📱 ${d.id}</strong><br>
                                <small style="color: #7f8c8d;">متصل منذ: ${new Date(d.connected_at).toLocaleString('ar-EG')}</small><br>
                                <code style="margin-top: 10px; display: inline-block;">${window.location.origin}${d.public_url}</code>
                            </div>
                        `).join('');
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
    """


# ═══════════════════════════════════════════════════════════
#  Startup & Shutdown
# ═══════════════════════════════════════════════════════════

@app.on_event("startup")
async def startup_event():
    """عند بدء التشغيل"""
    logger.info("🚀 SMS Gateway Relay Server Started")
    logger.info("📡 WebSocket endpoint: /device/{device_id}")
    logger.info("🌐 HTTP proxy: /api/{device_id}/{path}")


@app.on_event("shutdown")
async def shutdown_event():
    """عند الإيقاف"""
    logger.info("🛑 Server shutting down...")
    
    # إغلاق جميع الاتصالات
    for device_id, device in list(devices.items()):
        try:
            await device["websocket"].close()
        except:
            pass


# ═══════════════════════════════════════════════════════════
#  Keep Alive - 3 طبقات حماية ضد النوم
# ═══════════════════════════════════════════════════════════

@app.on_event("startup")
async def keep_alive():
    """
    Keep alive task - 3 طبقات حماية:
    1. Internal ping كل دقيقة
    2. Self HTTP request كل 5 دقائق
    3. Memory activity كل 30 ثانية
    """
    
    # الطبقة 1: Internal Ping
    async def internal_ping():
        """Ping داخلي كل دقيقة"""
        while True:
            await asyncio.sleep(60)
            logger.info(f"💓 Keep alive - Devices: {len(devices)} - Requests: {len(pending_requests)}")
    
    # الطبقة 2: Self HTTP Request
    async def self_request():
        """طلب HTTP للسيرفر نفسه كل 5 دقائق"""
        import aiohttp
        await asyncio.sleep(30)  # انتظر 30 ثانية للبدء
        
        while True:
            try:
                async with aiohttp.ClientSession() as session:
                    async with session.get('http://localhost:7860/health', timeout=10) as resp:
                        if resp.status == 200:
                            logger.info("🔄 Self-ping successful")
            except Exception as e:
                logger.warning(f"Self-ping failed: {e}")
            
            await asyncio.sleep(300)  # كل 5 دقائق
    
    # الطبقة 3: Memory Activity
    async def memory_activity():
        """نشاط في الذاكرة كل 30 ثانية"""
        counter = 0
        while True:
            counter += 1
            # نشاط بسيط في الذاكرة
            _ = [i for i in range(100)]
            
            if counter % 10 == 0:  # كل 5 دقائق
                logger.info(f"🧠 Memory activity - Counter: {counter}")
            
            await asyncio.sleep(30)
    
    # تشغيل جميع الطبقات
    asyncio.create_task(internal_ping())
    asyncio.create_task(self_request())
    asyncio.create_task(memory_activity())
    
    logger.info("✅ Keep Alive activated - 3 layers protection")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=7860)
