import { randomUUID } from "crypto";
  import { WebSocketServer, WebSocket } from "ws";
  import type { IncomingMessage, Server } from "http";

  const RELAY_VERSION = "2.0.0";
  const REQUEST_TIMEOUT_MS = 30_000;

  export interface DeviceConnection {
    ws: WebSocket;
    deviceId: string;
    apiKey: string;
    connectedAt: string;
    isAlive: boolean;
  }

  export interface PendingRequest {
    resolve: (value: { status: number; body: unknown }) => void;
    reject: (reason: { status: number; body: unknown }) => void;
    timer: NodeJS.Timeout;
    deviceId: string;
  }

  const connectedDevices = new Map<string, DeviceConnection>();
  const apiKeyToDevice = new Map<string, string>();
  const pendingRequests = new Map<string, PendingRequest>();

  export const getRelayVersion = () => RELAY_VERSION;
  export const getConnectedDevicesCount = () => connectedDevices.size;
  export const getPendingRequestsCount = () => pendingRequests.size;
  export const getConnectedDeviceIds = () => Array.from(connectedDevices.values()).map(c => c.deviceId);

  export function isDeviceConnected(apiKey: string): boolean {
    const deviceId = apiKeyToDevice.get(apiKey);
    if (!deviceId) return false;
    const conn = connectedDevices.get(deviceId);
    return !!conn && conn.ws.readyState === WebSocket.OPEN;
  }

  export async function forwardToDevice(apiKey: string, method: string, path: string, query: string, body: string | null) {
    const deviceId = apiKeyToDevice.get(apiKey);
    if (!deviceId) throw { status: 503, body: { error: "Device not connected" } };
    const conn = connectedDevices.get(deviceId);
    if (!conn || conn.ws.readyState !== WebSocket.OPEN) {
      apiKeyToDevice.delete(apiKey);
      connectedDevices.delete(deviceId);
      throw { status: 503, body: { error: "Device connection lost" } };
    }
    const requestId = randomUUID();
    return new Promise<{ status: number; body: unknown }>((resolve, reject) => {
      const timer = setTimeout(() => {
        pendingRequests.delete(requestId);
        reject({ status: 504, body: { error: "Device response timeout (30s)" } });
      }, REQUEST_TIMEOUT_MS);
      pendingRequests.set(requestId, { resolve, reject, timer, deviceId });
      conn.ws.send(JSON.stringify({ type: "request", requestId, method, path, query, body }));
    });
  }

  function cleanupDevice(conn: DeviceConnection) {
    connectedDevices.delete(conn.deviceId);
    apiKeyToDevice.delete(conn.apiKey);
    console.log(`[Relay] Device disconnected: ${conn.deviceId} (total: ${connectedDevices.size})`);
    for (const [reqId, pending] of pendingRequests) {
      if (pending.deviceId === conn.deviceId) {
        clearTimeout(pending.timer);
        pending.reject({ status: 503, body: { error: "Device disconnected" } });
        pendingRequests.delete(reqId);
      }
    }
  }

  export function setupWebSocketServer(server: Server) {
    const wss = new WebSocketServer({ server, path: "/device" });

    wss.on("connection", (ws: WebSocket, req: IncomingMessage) => {
      const authHeader = req.headers["authorization"] as string | undefined;
      const apiKey = (req.headers["x-api-key"] as string) || authHeader?.replace("Bearer ", "");
      if (!apiKey) { ws.close(4001, "Unauthorized"); return; }

      const deviceId = randomUUID();
      const conn: DeviceConnection = { ws, deviceId, apiKey, connectedAt: new Date().toISOString(), isAlive: true };
      connectedDevices.set(deviceId, conn);
      apiKeyToDevice.set(apiKey, deviceId);
      console.log(`[Relay] Device connected: ${deviceId} (total: ${connectedDevices.size})`);

      ws.send(JSON.stringify({ type: "connected", deviceId, relayVersion: RELAY_VERSION }));
      ws.on("pong", () => { conn.isAlive = true; });
      ws.on("message", (data: Buffer) => {
        try {
          const msg = JSON.parse(data.toString()) as Record<string, unknown>;
          if (msg.type === "response" && typeof msg.requestId === "string") {
            const pending = pendingRequests.get(msg.requestId);
            if (pending) {
              clearTimeout(pending.timer);
              pendingRequests.delete(msg.requestId);
              pending.resolve({ status: (msg.status as number) || 200, body: msg.body });
            }
          }
        } catch { /* ignore */ }
      });
      ws.on("close", () => cleanupDevice(conn));
      ws.on("error", (err) => console.error("[Relay] WebSocket error:", err));
    });

    // Heartbeat
    const heartbeat = setInterval(() => {
      wss.clients.forEach((ws: WebSocket) => {
        const conn = Array.from(connectedDevices.values()).find(c => c.ws === ws);
        if (!conn) return;
        if (!conn.isAlive) { cleanupDevice(conn); return ws.terminate(); }
        conn.isAlive = false;
        ws.ping();
      });
    }, 30_000);

    wss.on("close", () => clearInterval(heartbeat));
    return wss;
  }
  