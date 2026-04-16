import { Router } from "express";
  import { db } from "../../db";
  import { devicesTable, transactionsTable } from "../../db/schema";
  import { eq, desc, count, and, gte } from "drizzle-orm";
  import { isDeviceConnected, getConnectedDevicesCount } from "../lib/relay";
  import { smsLogsTable } from "../../db/schema";

  const router = Router();
  const getApiKey = (req: any) => (req.headers["x-api-key"] || req.headers["authorization"]?.replace("Bearer ", "")) as string | undefined;

  router.get("/transactions", async (req, res) => {
    const limit = Math.min(parseInt(String(req.query.limit || 50)), 200);
    const offset = parseInt(String(req.query.offset || 0));
    const status = req.query.status as string | undefined;
    const where = status ? [eq(transactionsTable.status, status as any)] : [];
    const [data, [{ value: total }]] = await Promise.all([
      db.select().from(transactionsTable).where(where.length ? and(...where) : undefined).orderBy(desc(transactionsTable.createdAt)).limit(limit).offset(offset),
      db.select({ value: count() }).from(transactionsTable).where(where.length ? and(...where) : undefined),
    ]);
    res.json({ data, total, limit, offset });
  });

  router.post("/transactions", async (req, res) => {
    const apiKey = getApiKey(req);
    if (!apiKey) { res.status(401).json({ error: "Missing API key" }); return; }
    const [device] = await db.select().from(devicesTable).where(eq(devicesTable.apiKey, apiKey));
    if (!device) { res.status(401).json({ error: "Invalid API key" }); return; }
    if (!isDeviceConnected(apiKey)) { res.status(503).json({ error: "Device not connected" }); return; }

    const { id, amount, phoneNumber, walletType, expectedTxId, webhookUrl, expiresInMinutes = 30 } = req.body;
    if (!id || !amount || !phoneNumber) { res.status(400).json({ error: "id, amount, phoneNumber required" }); return; }

    const expiresAt = new Date(Date.now() + expiresInMinutes * 60 * 1000);
    try {
      const [tx] = await db.insert(transactionsTable).values({ id, deviceId: device.id, amount: String(amount), phoneNumber, walletType, expectedTxId, webhookUrl, expiresAt, status: "PENDING" }).returning();
      res.status(201).json(tx);
    } catch (err: any) {
      if (err.code === "23505") { res.status(409).json({ error: "Transaction ID already exists" }); return; }
      res.status(500).json({ error: "Internal server error" });
    }
  });

  router.get("/transactions/:id", async (req, res) => {
    const [tx] = await db.select().from(transactionsTable).where(eq(transactionsTable.id, req.params.id));
    if (!tx) { res.status(404).json({ error: "Not found" }); return; }
    res.json(tx);
  });

  router.get("/stats", async (_req, res) => {
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const [total, pending, confirmed, expired, failed, smsTotal, todayRows, todayAmt] = await Promise.all([
      db.select({ v: count() }).from(transactionsTable),
      db.select({ v: count() }).from(transactionsTable).where(eq(transactionsTable.status, "PENDING")),
      db.select({ v: count() }).from(transactionsTable).where(eq(transactionsTable.status, "CONFIRMED")),
      db.select({ v: count() }).from(transactionsTable).where(eq(transactionsTable.status, "EXPIRED")),
      db.select({ v: count() }).from(transactionsTable).where(eq(transactionsTable.status, "FAILED")),
      db.select({ v: count() }).from(smsLogsTable),
      db.select({ v: count() }).from(transactionsTable).where(and(eq(transactionsTable.status, "CONFIRMED"), gte(transactionsTable.confirmedAt, today))),
      db.select({ amount: transactionsTable.confirmedAmount }).from(transactionsTable).where(and(eq(transactionsTable.status, "CONFIRMED"), gte(transactionsTable.confirmedAt, today))),
    ]);
    const todayAmount = todayAmt.reduce((s, r) => s + parseFloat(r.amount || "0"), 0);
    const devTotal = await db.select({ v: count() }).from(devicesTable);
    res.json({ totalDevices: devTotal[0].v, connectedDevices: getConnectedDevicesCount(), totalTransactions: total[0].v, pendingTransactions: pending[0].v, confirmedTransactions: confirmed[0].v, expiredTransactions: expired[0].v, failedTransactions: failed[0].v, totalSmsLogs: smsTotal[0].v, todayConfirmed: todayRows[0].v, todayAmount: todayAmount.toFixed(2) });
  });

  export default router;
  