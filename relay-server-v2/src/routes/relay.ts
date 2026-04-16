import { Router } from "express";
  import { getRelayVersion, getConnectedDevicesCount, getPendingRequestsCount } from "../lib/relay";

  const router = Router();
  router.get("/relay/status", (_req, res) => {
    res.json({ status: "ok", version: getRelayVersion(), connectedDevices: getConnectedDevicesCount(), pendingRequests: getPendingRequestsCount(), uptime: process.uptime() });
  });
  export default router;
  