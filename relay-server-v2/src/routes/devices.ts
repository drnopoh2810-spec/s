import { Router } from "express";
  import { randomBytes } from "crypto";
  import { db } from "../../db";
  import { devicesTable } from "../../db/schema";
  import { eq } from "drizzle-orm";
  import { isDeviceConnected } from "../lib/relay";

  const router = Router();
  const generateApiKey = () => randomBytes(32).toString("hex");
  const withConnection = (d: any) => ({ ...d, isConnected: isDeviceConnected(d.apiKey) });

  router.get("/devices", async (_req, res) => {
    const devices = await db.select().from(devicesTable);
    res.json(devices.map(withConnection));
  });

  router.post("/devices", async (req, res) => {
    const { name } = req.body;
    if (!name?.trim()) { res.status(400).json({ error: "name is required" }); return; }
    const [device] = await db.insert(devicesTable).values({ name: name.trim(), apiKey: generateApiKey() }).returning();
    res.status(201).json(withConnection(device));
  });

  router.get("/devices/:id", async (req, res) => {
    const [device] = await db.select().from(devicesTable).where(eq(devicesTable.id, req.params.id));
    if (!device) { res.status(404).json({ error: "Not found" }); return; }
    res.json(withConnection(device));
  });

  router.delete("/devices/:id", async (req, res) => {
    const [deleted] = await db.delete(devicesTable).where(eq(devicesTable.id, req.params.id)).returning();
    if (!deleted) { res.status(404).json({ error: "Not found" }); return; }
    res.json({ success: true });
  });

  export default router;
  