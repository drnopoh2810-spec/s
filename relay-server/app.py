"""
SMS Gateway Relay Server - Secure Edition
==========================================

نظام الأمان:
- كل جهاز يسجل بـ deviceId + deviceToken (مشتق من API Key الخاص به)
- الـ deviceId في الرابط العام لا يكفي وحده للوصول
- كل طلب HTTP يجب أن يحمل Authorization header يتحقق منه التطبيق نفسه
- الـ /status لا يكشف device IDs للعموم
- الـ deviceId مبني على Android ID + API Key hash → فريد لكل مستخدم
"""

import asyncio
import json
import uuid
import time
import hashlib
import hmac
import logging
import os
from typing import Dict, Optional
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Request, Response, Header
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
import uvicorn

# ─── Setup ───────────────────────────────────────────────────────────────────

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger("relay")

app = FastAPI(title="SMS Gateway Relay", version="3.0", docs_url=None, redoc_url=None)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ─── State ────────────────────────────────────────────────────────────────────

class DeviceSession:
    """معلومات جلسة الجهاز المتصل"""
    def __init__(self, device_id: str, ws: WebSocket, device_token: str):
        self.device_id = device_id
        self.ws = ws
        self.device_token = device_token  # hash من API Key - للتحقق
        self.connected_at = time.time()
        self.requests_handled = 0
        self.last_seen = time.time()

# deviceId → DeviceSession
devices: Dict[str, DeviceSession] = {}

# requestId → asyncio.Future
pending_requests: Dict[str, asyncio.Future] = {}

stats = {
    "total_requests": 0,
    "rejected_requests": 0,
    "start_time": time.time()
}

REQUEST_TIMEOUT = 30

# ─── Device WebSocket Endpoint ────────────────────────────────────────────────

@app.websocket("/device")
async def device_endpoint(ws: WebSocket):
    """
    التطبيق يتصل هنا.
    يجب أن يرسل:
    {
      "type": "register",
      "deviceId": "gw-abc123...",   ← فريد لكل مستخدم (Android ID + API Key hash)
      "deviceToken": "sha256(...)"  ← hash من API Key للتحقق لاحقاً
    }
    """
    await ws.accept()
    session: Optional[DeviceSession] = None

    try:
        async for raw in ws.iter_text():
            try:
                msg = json.loads(raw)
                msg_type = msg.get("type", "")

                # ─── تسجيل الجهاز ───────────────────────────────────────────
                if msg_type == "register":
                    device_id = msg.get("deviceId", "").strip()
                    device_token = msg.get("deviceToken", "").strip()

                    if not device_id or len(device_id) < 8:
                        await ws.send_text(json.dumps({
                            "type": "error",
                            "message": "Invalid deviceId"
                        }))
                        continue

                    # إذا كان الجهاز متصلاً بالفعل، أغلق الاتصال القديم
                    if device_id in devices:
                        old_session = devices[device_id]
                        try:
                            await old_session.ws.close(1000, "Replaced by new connection")
                        except Exception:
                            pass
                        log.info(f"🔄 Device reconnected: {device_id}")

                    session = DeviceSession(device_id, ws, device_token)
                    devices[device_id] = session

                    # بناء الرابط العام - يحتوي على deviceId فقط
                    # الأمان الحقيقي يأتي من API Key في كل طلب
                    public_url = f"/gateway/{device_id}"

                    await ws.send_text(json.dumps({
                        "type": "registered",
                        "deviceId": device_id,
                        "publicUrl": public_url,
                        "message": "Tunnel active!",
                        "timestamp": int(time.time() * 1000)
                    }))
                    log.info(f"✅ Device registered: {device_id[:16]}...")

                # ─── رد على طلب HTTP ────────────────────────────────────────
                elif msg_type in ("http_response", "response"):
                    request_id = msg.get("requestId") or msg.get("request_id", "")
                    if request_id and request_id in pending_requests:
                        future = pending_requests[request_id]
                        if not future.done():
                            future.set_result(msg)
                    if session:
                        session.requests_handled += 1
                        session.last_seen = time.time()

                # ─── Heartbeat ───────────────────────────────────────────────
                elif msg_type == "ping":
                    if session:
                        session.last_seen = time.time()
                    await ws.send_text(json.dumps({
                        "type": "pong",
                        "timestamp": int(time.time() * 1000)
                    }))

                elif msg_type == "pong":
                    if session:
                        session.last_seen = time.time()

            except json.JSONDecodeError:
                pass

    except WebSocketDisconnect:
        pass
    except Exception as e:
        log.error(f"Device error: {e}")
    finally:
        if session and session.device_id in devices:
            # فقط احذف إذا كانت نفس الجلسة (لم يُستبدل بجلسة جديدة)
            if devices.get(session.device_id) is session:
                del devices[session.device_id]
                log.info(f"🗑️ Device disconnected: {session.device_id[:16]}...")


# ─── HTTP Tunnel Endpoint ─────────────────────────────────────────────────────

@app.api_route(
    "/gateway/{device_id}/{path:path}",
    methods=["GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"]
)
async def tunnel_request(device_id: str, path: str, request: Request):
    """
    أي موقع يرسل طلب هنا.
    
    الأمان:
    - الـ deviceId في الرابط يحدد الجهاز
    - الـ Authorization header يتحقق منه التطبيق نفسه
    - الـ Relay Server لا يعرف API Key المستخدم - فقط يمرر الطلب
    - التطبيق هو من يقرر قبول أو رفض الطلب
    """
    if request.method == "OPTIONS":
        return Response(headers={
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, PATCH, OPTIONS",
            "Access-Control-Allow-Headers": "Authorization, Content-Type, X-Api-Key",
        })

    # التحقق من وجود الجهاز
    if device_id not in devices:
        return JSONResponse(status_code=503, content={
            "error": "device_offline",
            "message": "Device is not connected. Make sure the app is running.",
        })

    session = devices[device_id]
    stats["total_requests"] += 1

    # قراءة الـ body
    try:
        body_bytes = await request.body()
        body = body_bytes.decode("utf-8") if body_bytes else None
    except Exception:
        body = None

    # بناء الـ request message
    request_id = str(uuid.uuid4())
    full_path = f"/{path}"
    if request.query_params:
        full_path += f"?{request.query_params}"

    # إرسال كل الـ headers للتطبيق - التطبيق هو من يتحقق من Authorization
    message = {
        "type": "http_request",
        "requestId": request_id,
        "method": request.method,
        "path": full_path,
        "headers": dict(request.headers),
        "body": body,
        "timestamp": int(time.time() * 1000)
    }

    loop = asyncio.get_event_loop()
    future: asyncio.Future = loop.create_future()
    pending_requests[request_id] = future

    try:
        await session.ws.send_text(json.dumps(message))

        response_msg = await asyncio.wait_for(future, timeout=REQUEST_TIMEOUT)

        status_code = int(response_msg.get("status") or response_msg.get("status_code", 200))
        body_content = response_msg.get("body", "{}")

        if isinstance(body_content, dict):
            body_content = json.dumps(body_content)

        return Response(
            content=body_content,
            status_code=status_code,
            media_type="application/json",
            headers={
                "Access-Control-Allow-Origin": "*",
                "X-Request-Id": request_id[:8],
            }
        )

    except asyncio.TimeoutError:
        stats["rejected_requests"] += 1
        return JSONResponse(status_code=504, content={
            "error": "timeout",
            "message": "Device did not respond in time."
        })
    except Exception as e:
        return JSONResponse(status_code=502, content={"error": "bad_gateway", "message": str(e)})
    finally:
        pending_requests.pop(request_id, None)


# ─── Public Endpoints (لا تكشف معلومات حساسة) ────────────────────────────────

@app.get("/")
async def root():
    return {
        "name": "SMS Gateway Relay",
        "version": "3.0",
        "status": "running",
        "uptime_seconds": int(time.time() - stats["start_time"]),
        "active_devices": len(devices),  # عدد فقط، لا IDs
        "total_requests": stats["total_requests"],
    }

@app.get("/health")
async def health():
    return {"status": "ok", "timestamp": int(time.time() * 1000)}

# ─── لا يوجد /status يكشف device IDs ─────────────────────────────────────────
# تم حذفه عمداً لأسباب أمنية

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=7860, log_level="warning")
