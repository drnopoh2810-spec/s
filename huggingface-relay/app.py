"""
═══════════════════════════════════════════════════════════
 SMS Payment Gateway - Relay Server for Hugging Face
 يعمل 24/7 بدون انقطاع - نسخة محسّنة v2.0
═══════════════════════════════════════════════════════════
"""

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Request, HTTPException
from fastapi.responses import HTMLResponse, JSONResponse
from fastapi.middleware.cors import CORSMiddleware
import asyncio
import json
import time
import uuid
from datetime import datetime
from typing import Dict, Optional
import logging

# ─── Logging ──────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S"
)
logger = logging.getLogger(__name__)

SERVER_START_TIME = time.time()

# ─── FastAPI App ───────────────────────────────────────────
app = FastAPI(
    title="SMS Gateway Relay",
    description="Relay server for SMS Payment Gateway - Always Online v2.0",
    version="2.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ─── State ────────────────────────────────────────────────
# device_id -> { websocket, connected, connected_at, last_heartbeat, api_key }
devices: Dict[str, dict] = {}

# request_id -> { event, response }
pending_requests: Dict[str, dict] = {}

# api_key -> device_id  (اختياري للتوافق مع server.js)
api_key_to_device: Dict[str, str] = {}


# ═══════════════════════════════════════════════════════════
#  WebSocket — اتصال الأجهزة
# ═══════════════════════════════════════════════════════════

@app.websocket("/device/{device_id}")
async def websocket_device(websocket: WebSocket, device_id: str):
    """اتصال WebSocket للأجهزة - يدعم إعادة الاتصال التلقائي"""
    await websocket.accept()

    # إذا كان الجهاز متصلاً بالفعل، نغلق الاتصال القديم
    if device_id in devices:
        old_ws = devices[device_id].get("websocket")
        try:
            if old_ws:
                await old_ws.close(1001, "Replaced by new connection")
        except Exception:
            pass
        logger.info(f"♻️ Replaced existing connection for device: {device_id}")

    # قراءة API Key من headers إن وُجد
    api_key = websocket.headers.get("x-api-key") or \
              (websocket.headers.get("authorization", "").replace("Bearer ", "") or None)

    now = datetime.now().isoformat()
    devices[device_id] = {
        "websocket": websocket,
        "connected": True,
        "connected_at": now,
        "last_heartbeat": time.time(),
        "api_key": api_key
    }

    if api_key:
        api_key_to_device[api_key] = device_id

    logger.info(f"📱 Device connected: {device_id} (total: {len(devices)})")

    # رسالة ترحيب
    try:
        await websocket.send_json({
            "type": "connected",
            "deviceId": device_id,
            "device_id": device_id,
            "public_url": f"/api/{device_id}",
            "message": "Connected successfully to relay server v2.0",
            "timestamp": int(time.time() * 1000)
        })
    except Exception as e:
        logger.error(f"Error sending welcome message: {e}")

    # ─── Server-side Ping Task ────────────────────────────
    async def server_ping():
        while device_id in devices and devices[device_id]["connected"]:
            try:
                await websocket.send_json({
                    "type": "ping",
                    "timestamp": int(time.time() * 1000)
                })
                await asyncio.sleep(25)
            except Exception:
                break

    ping_task = asyncio.create_task(server_ping())

    # ─── Receive Loop ─────────────────────────────────────
    try:
        while True:
            try:
                data = await asyncio.wait_for(websocket.receive_text(), timeout=120)
            except asyncio.TimeoutError:
                # لا يوجد رسائل من الجهاز منذ 2 دقيقة — نرسل ping ونستمر
                logger.warning(f"⏰ No message from {device_id} for 120s, sending ping")
                try:
                    await websocket.send_json({"type": "ping", "timestamp": int(time.time() * 1000)})
                except Exception:
                    break
                continue
            except WebSocketDisconnect:
                break

            try:
                message = json.loads(data)
            except json.JSONDecodeError:
                logger.warning(f"Invalid JSON from {device_id}")
                continue

            # تحديث heartbeat
            if device_id in devices:
                devices[device_id]["last_heartbeat"] = time.time()

            msg_type = message.get("type", "")

            if msg_type in ("pong", "ping"):
                # رد على ping بـ pong
                if msg_type == "ping":
                    try:
                        await websocket.send_json({
                            "type": "pong",
                            "timestamp": int(time.time() * 1000)
                        })
                    except Exception:
                        pass

            elif msg_type in ("http_response", "response"):
                # الجهاز يرد على طلب HTTP
                request_id = message.get("request_id") or message.get("requestId")
                if request_id and request_id in pending_requests:
                    pending_requests[request_id]["response"] = message
                    pending_requests[request_id]["event"].set()

            elif msg_type == "register":
                # تسجيل الجهاز (رسالة اختيارية من التطبيق)
                new_api_key = message.get("apiKey") or message.get("api_key")
                if new_api_key:
                    devices[device_id]["api_key"] = new_api_key
                    api_key_to_device[new_api_key] = device_id
                logger.info(f"📝 Device registered: {device_id}")
                try:
                    await websocket.send_json({
                        "type": "registered",
                        "device_id": device_id,
                        "timestamp": int(time.time() * 1000)
                    })
                except Exception:
                    pass

            else:
                logger.debug(f"📨 Message from {device_id}: type={msg_type}")

    except WebSocketDisconnect:
        pass
    except Exception as e:
        logger.error(f"WebSocket error for {device_id}: {e}")
    finally:
        ping_task.cancel()
        _cleanup_device(device_id, api_key)
        logger.info(f"📴 Device disconnected: {device_id} (total: {len(devices)})")


def _cleanup_device(device_id: str, api_key: Optional[str]):
    """تنظيف الجهاز عند قطع الاتصال"""
    if device_id in devices:
        del devices[device_id]
    if api_key and api_key in api_key_to_device:
        del api_key_to_device[api_key]

    # رفض الطلبات المعلقة لهذا الجهاز
    to_remove = [
        req_id for req_id, req in pending_requests.items()
        if req.get("device_id") == device_id
    ]
    for req_id in to_remove:
        if req_id in pending_requests:
            pending_requests[req_id]["response"] = {"error": "Device disconnected", "status_code": 503}
            pending_requests[req_id]["event"].set()
            del pending_requests[req_id]


# ═══════════════════════════════════════════════════════════
#  الاتصال الجديد: /device (بدون device_id في المسار)
#  للتوافق مع server.js الذي يستخدم ws://relay/device
# ═══════════════════════════════════════════════════════════

@app.websocket("/device")
async def websocket_device_nopath(websocket: WebSocket):
    """اتصال WebSocket بدون device_id في المسار (متوافق مع server.js)"""
    await websocket.accept()

    api_key = websocket.headers.get("x-api-key") or \
              (websocket.headers.get("authorization", "").replace("Bearer ", "") or None)

    device_id = str(uuid.uuid4())

    now = datetime.now().isoformat()
    devices[device_id] = {
        "websocket": websocket,
        "connected": True,
        "connected_at": now,
        "last_heartbeat": time.time(),
        "api_key": api_key
    }

    if api_key:
        api_key_to_device[api_key] = device_id

    logger.info(f"📱 Device connected (no-path): {device_id}")

    try:
        await websocket.send_json({
            "type": "connected",
            "deviceId": device_id,
            "device_id": device_id,
            "message": "Connected to relay server v2.0",
            "relayVersion": "2.0.0",
            "timestamp": int(time.time() * 1000)
        })
    except Exception as e:
        logger.error(f"Error sending welcome: {e}")

    async def server_ping():
        while device_id in devices and devices[device_id]["connected"]:
            try:
                await websocket.send_json({"type": "ping", "timestamp": int(time.time() * 1000)})
                await asyncio.sleep(25)
            except Exception:
                break

    ping_task = asyncio.create_task(server_ping())

    try:
        while True:
            try:
                data = await asyncio.wait_for(websocket.receive_text(), timeout=120)
            except asyncio.TimeoutError:
                try:
                    await websocket.send_json({"type": "ping", "timestamp": int(time.time() * 1000)})
                except Exception:
                    break
                continue
            except WebSocketDisconnect:
                break

            try:
                message = json.loads(data)
            except json.JSONDecodeError:
                continue

            if device_id in devices:
                devices[device_id]["last_heartbeat"] = time.time()

            msg_type = message.get("type", "")

            if msg_type == "ping":
                try:
                    await websocket.send_json({"type": "pong", "timestamp": int(time.time() * 1000)})
                except Exception:
                    pass
            elif msg_type in ("pong",):
                pass
            elif msg_type in ("response", "http_response"):
                request_id = message.get("requestId") or message.get("request_id")
                if request_id and request_id in pending_requests:
                    pending_requests[request_id]["response"] = message
                    pending_requests[request_id]["event"].set()
            else:
                logger.debug(f"📨 {device_id}: {msg_type}")

    except WebSocketDisconnect:
        pass
    except Exception as e:
        logger.error(f"WS error for {device_id}: {e}")
    finally:
        ping_task.cancel()
        _cleanup_device(device_id, api_key)
        logger.info(f"📴 Device disconnected (no-path): {device_id}")


# ═══════════════════════════════════════════════════════════
#  HTTP Proxy - توجيه الطلبات للأجهزة
# ═══════════════════════════════════════════════════════════

async def _forward_to_device(device_id: str, request: Request, path: str) -> JSONResponse:
    """دالة مشتركة لتوجيه الطلبات للجهاز"""
    device = devices.get(device_id)
    if not device or not device["connected"]:
        raise HTTPException(status_code=503, detail=f"Device '{device_id}' is not connected")

    websocket = device["websocket"]

    # قراءة Body
    body = None
    try:
        body = await request.json()
    except Exception:
        try:
            raw = await request.body()
            body = raw.decode() if raw else None
        except Exception:
            pass

    request_id = f"{device_id}_{uuid.uuid4().hex[:8]}"

    http_request = {
        "type": "http_request",
        "requestId": request_id,
        "request_id": request_id,
        "method": request.method,
        "path": f"/{path}",
        "headers": {k: v for k, v in request.headers.items() if k.lower() not in ("host",)},
        "query": dict(request.query_params),
        "body": body
    }

    event = asyncio.Event()
    pending_requests[request_id] = {
        "event": event,
        "response": None,
        "device_id": device_id
    }

    try:
        await websocket.send_json(http_request)
    except Exception as e:
        del pending_requests[request_id]
        raise HTTPException(status_code=502, detail=f"Failed to forward request: {e}")

    try:
        await asyncio.wait_for(event.wait(), timeout=30.0)
    except asyncio.TimeoutError:
        pending_requests.pop(request_id, None)
        raise HTTPException(status_code=504, detail="Device response timeout (30s)")

    response_data = pending_requests.pop(request_id, {}).get("response", {})

    if not response_data:
        raise HTTPException(status_code=500, detail="No response from device")

    status_code = response_data.get("status_code") or response_data.get("status", 200)
    body_content = response_data.get("body") or response_data.get("data") or {}

    # تنظيف headers قبل الإرجاع
    resp_headers = {
        k: v for k, v in (response_data.get("headers") or {}).items()
        if k.lower() not in ("content-length", "transfer-encoding")
    }

    return JSONResponse(content=body_content, status_code=int(status_code), headers=resp_headers)


@app.api_route("/api/{device_id}/{path:path}", methods=["GET", "POST", "PUT", "DELETE", "PATCH"])
async def proxy_by_device_id(device_id: str, path: str, request: Request):
    """توجيه طلبات HTTP للأجهزة عبر device_id"""
    return await _forward_to_device(device_id, request, path)


@app.api_route("/api/v1/{path:path}", methods=["GET", "POST", "PUT", "DELETE", "PATCH"])
async def proxy_by_api_key(path: str, request: Request):
    """توجيه طلبات HTTP للأجهزة عبر API Key (توافق مع server.js)"""
    api_key = (request.headers.get("authorization", "").replace("Bearer ", "")
               or request.headers.get("x-api-key"))

    if not api_key:
        raise HTTPException(status_code=401, detail="Missing API key")

    device_id = api_key_to_device.get(api_key)
    if not device_id:
        raise HTTPException(
            status_code=503,
            detail={
                "error": "Device not connected",
                "hint": "Make sure the Android app is running",
                "connectedDevices": len(devices)
            }
        )

    return await _forward_to_device(device_id, request, f"v1/{path}")


# ═══════════════════════════════════════════════════════════
#  Health & Monitoring
# ═══════════════════════════════════════════════════════════

@app.get("/health")
async def health_check():
    uptime = time.time() - SERVER_START_TIME
    return {
        "status": "ok",
        "version": "2.0.0",
        "devices": len(devices),
        "pendingRequests": len(pending_requests),
        "uptime": round(uptime, 2),
        "uptimeHuman": f"{int(uptime // 3600)}h {int((uptime % 3600) // 60)}m",
        "timestamp": datetime.now().isoformat()
    }


@app.get("/relay/status")
async def relay_status():
    return {
        "status": "ok",
        "version": "2.0.0",
        "connectedDevices": len(devices),
        "uptime": round(time.time() - SERVER_START_TIME, 2)
    }


@app.get("/devices")
async def list_devices():
    device_list = []
    for device_id, device in devices.items():
        device_list.append({
            "id": device_id,
            "connected": device["connected"],
            "connected_at": device["connected_at"],
            "last_heartbeat": device["last_heartbeat"],
            "idle_seconds": round(time.time() - device["last_heartbeat"], 1),
            "public_url": f"/api/{device_id}"
        })
    return {"count": len(devices), "devices": device_list}


@app.get("/", response_class=HTMLResponse)
async def root():
    return """<!DOCTYPE html>
<html dir="rtl" lang="ar">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SMS Gateway Relay v2.0</title>
    <style>
        *{margin:0;padding:0;box-sizing:border-box}
        body{font-family:'Segoe UI',sans-serif;background:linear-gradient(135deg,#1a1f2e,#16213e);
             color:#e1e4e8;min-height:100vh;padding:20px}
        .container{max-width:860px;margin:0 auto}
        h1{font-size:2rem;margin-bottom:6px}
        .badge{background:#238636;color:#fff;font-size:.75rem;padding:3px 10px;border-radius:12px;margin-right:8px}
        .card{background:#161b22;border:1px solid #30363d;border-radius:14px;padding:24px;margin-bottom:20px}
        .card h2{margin-bottom:16px;padding-bottom:10px;border-bottom:1px solid #30363d}
        .stats{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;margin-bottom:20px}
        .stat{background:#0d1117;border:1px solid #30363d;border-radius:10px;padding:16px;text-align:center}
        .stat .num{font-size:2rem;font-weight:700;color:#58a6ff}
        .stat .num.green{color:#3fb950}
        .stat .lbl{font-size:.8rem;color:#8b949e;margin-top:4px}
        .online{display:inline-block;width:9px;height:9px;border-radius:50%;background:#3fb950;
                animation:pulse 2s infinite;margin-left:6px}
        @keyframes pulse{0%,100%{opacity:1}50%{opacity:.35}}
        .device{background:#21262d;border-radius:8px;padding:12px 16px;margin-bottom:8px}
        code{background:#0d1117;padding:3px 8px;border-radius:5px;font-family:monospace;font-size:.85rem}
        @media(max-width:600px){.stats{grid-template-columns:1fr}}
    </style>
</head>
<body>
<div class="container">
    <div class="card">
        <h1>🔗 SMS Gateway Relay <span class="badge">v2.0</span></h1>
        <p style="color:#8b949e">خادم الوسيط — يربط التطبيق بمواقعك عبر WebSocket</p>
    </div>

    <div class="stats">
        <div class="stat">
            <div class="num green" id="deviceCount">0</div>
            <div class="lbl">أجهزة متصلة</div>
        </div>
        <div class="stat">
            <div class="num" id="pendingCount">0</div>
            <div class="lbl">طلبات معلقة</div>
        </div>
        <div class="stat">
            <div class="num" id="uptimeVal">0</div>
            <div class="lbl">وقت التشغيل</div>
        </div>
    </div>

    <div class="card">
        <h2>📱 الأجهزة المتصلة <span class="online"></span></h2>
        <div id="devices"><p style="color:#8b949e">جاري التحميل...</p></div>
    </div>

    <div class="card">
        <h2>🌐 كيفية الاتصال</h2>
        <p style="margin-bottom:10px;color:#8b949e">رابط WebSocket للتطبيق:</p>
        <code id="wsUrl">wss://YOUR_SPACE.hf.space/device/YOUR_DEVICE_ID</code>
        <br><br>
        <p style="color:#8b949e">أو بدون device_id (مع X-Api-Key header):</p>
        <code id="wsUrl2">wss://YOUR_SPACE.hf.space/device</code>
    </div>
</div>

<script>
    const host = window.location.host;
    document.getElementById('wsUrl').textContent = `wss://${host}/device/YOUR_DEVICE_ID`;
    document.getElementById('wsUrl2').textContent = `wss://${host}/device`;

    async function refresh() {
        try {
            const [hRes, dRes] = await Promise.all([
                fetch('/health'),
                fetch('/devices')
            ]);
            const h = await hRes.json();
            const d = await dRes.json();

            document.getElementById('deviceCount').textContent = h.devices;
            document.getElementById('pendingCount').textContent = h.pendingRequests;
            document.getElementById('uptimeVal').textContent = h.uptimeHuman;

            const el = document.getElementById('devices');
            if (d.count === 0) {
                el.innerHTML = '<p style="color:#8b949e;padding:10px">لا توجد أجهزة متصلة حالياً</p>';
            } else {
                el.innerHTML = d.devices.map(dev => `
                    <div class="device">
                        <span class="online"></span>
                        <strong style="color:#79c0ff">${dev.id.substring(0,20)}...</strong>
                        <span style="color:#8b949e;font-size:.8rem;margin-right:12px">
                            متصل منذ: ${new Date(dev.connected_at).toLocaleTimeString('ar-EG')}
                        </span>
                        <br>
                        <code style="margin-top:6px;display:inline-block">${window.location.origin}${dev.public_url}</code>
                    </div>`).join('');
            }
        } catch(e) {
            console.error('Refresh error:', e);
        }
    }
    refresh();
    setInterval(refresh, 5000);
</script>
</body>
</html>"""


# ═══════════════════════════════════════════════════════════
#  Startup / Shutdown
# ═══════════════════════════════════════════════════════════

@app.on_event("startup")
async def on_startup():
    logger.info("🚀 SMS Gateway Relay Server v2.0 Started")
    logger.info("📡 WebSocket: /device/{device_id}  OR  /device  (with X-Api-Key)")
    logger.info("🌐 HTTP proxy: /api/{device_id}/{path}  OR  /api/v1/{path} (with API key)")
    asyncio.create_task(_keep_alive_loop())
    asyncio.create_task(_stale_device_cleanup())


@app.on_event("shutdown")
async def on_shutdown():
    logger.info("🛑 Shutting down...")
    for device in list(devices.values()):
        try:
            await device["websocket"].close()
        except Exception:
            pass


# ─── Keep-Alive & Cleanup Tasks ───────────────────────────

async def _keep_alive_loop():
    """طبقة triple-protection ضد Sleep Mode في Hugging Face"""
    counter = 0

    async def _self_ping():
        try:
            import aiohttp
            async with aiohttp.ClientSession() as session:
                async with session.get("http://localhost:7860/health", timeout=aiohttp.ClientTimeout(total=10)) as r:
                    if r.status == 200:
                        logger.debug("🔄 Self-ping OK")
        except Exception as e:
            logger.debug(f"Self-ping: {e}")

    while True:
        await asyncio.sleep(30)
        counter += 1
        _ = [i * 2 for i in range(50)]   # memory activity

        if counter % 2 == 0:             # كل دقيقة
            logger.info(f"💓 Alive — devices={len(devices)} pending={len(pending_requests)}")

        if counter % 10 == 0:            # كل 5 دقائق
            await _self_ping()


async def _stale_device_cleanup():
    """إزالة الأجهزة التي توقفت عن إرسال heartbeat منذ أكثر من 3 دقائق"""
    while True:
        await asyncio.sleep(60)
        now = time.time()
        stale = [
            did for did, dev in list(devices.items())
            if now - dev["last_heartbeat"] > 180
        ]
        for did in stale:
            logger.warning(f"🗑️ Removing stale device: {did}")
            dev = devices.get(did)
            ak = dev.get("api_key") if dev else None
            try:
                if dev:
                    await dev["websocket"].close()
            except Exception:
                pass
            _cleanup_device(did, ak)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=7860, log_level="info")
