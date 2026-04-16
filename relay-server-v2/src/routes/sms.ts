import { Router } from "express";
  import { db } from "../../db";
  import { devicesTable, smsLogsTable, transactionsTable } from "../../db/schema";
  import { eq, desc, count, and } from "drizzle-orm";
  import { parseSms, calculateMatchConfidence } from "../lib/sms-parser";

  const router = Router();
  const getApiKey = (req: any) => (req.headers["x-api-key"] || req.headers["authorization"]?.replace("Bearer ", "")) as string | undefined;

  router.post("/sms/ingest", async (req, res) => {
    const apiKey = getApiKey(req);
    if (!apiKey) { res.status(401).json({ error: "Missing API key" }); return; }
    const [device] = await db.select().from(devicesTable).where(eq(devicesTable.apiKey, apiKey));
    if (!device) { res.status(401).json({ error: "Invalid API key" }); return; }

    const { sender, body, receivedAt } = req.body;
    if (!sender || !body) { res.status(400).json({ error: "sender and body are required" }); return; }

    const smsData = parseSms(sender, body);
    await db.update(devicesTable).set({ lastSeenAt: new Date() }).where(eq(devicesTable.id, device.id));

    const [log] = await db.insert(smsLogsTable).values({
      deviceId: device.id, sender, body,
      walletType: smsData.walletType ?? undefined,
      parsedAmount: smsData.amount ? String(smsData.amount) : undefined,
      parsedPhone: smsData.senderPhone ?? undefined,
      parsedTxId: smsData.txId ?? undefined,
      receivedAt: receivedAt ? new Date(receivedAt) : new Date(),
    }).returning();

    let matchedTxId = null, matchConfidence = null;
    if (smsData.amount && smsData.walletType) {
      const pending = await db.select().from(transactionsTable).where(and(eq(transactionsTable.status, "PENDING"), eq(transactionsTable.deviceId, device.id)));
      for (const tx of pending) {
        if (tx.expiresAt && new Date(tx.expiresAt) < new Date()) continue;
        const confidence = calculateMatchConfidence(smsData, parseFloat(tx.amount), tx.phoneNumber, tx.expectedTxId);
        if (confidence >= 0.7) {
          matchedTxId = tx.id; matchConfidence = confidence;
          await db.update(transactionsTable).set({ status: "CONFIRMED", confirmedAt: new Date(), walletTxId: smsData.txId ?? undefined, confirmedAmount: smsData.amount ? String(smsData.amount) : undefined, confirmedPhone: smsData.senderPhone ?? undefined, confidence: String(confidence) }).where(eq(transactionsTable.id, tx.id));
          await db.update(smsLogsTable).set({ matchedTransactionId: tx.id }).where(eq(smsLogsTable.id, log.id));
          break;
        }
      }
    }
    res.json({ success: true, logId: log.id, parsed: smsData, matchedTransactionId: matchedTxId, confidence: matchConfidence });
  });

  router.get("/sms/logs", async (req, res) => {
    const limit = Math.min(parseInt(String(req.query.limit || 50)), 200);
    const offset = parseInt(String(req.query.offset || 0));
    const [data, [{ value: total }]] = await Promise.all([
      db.select().from(smsLogsTable).orderBy(desc(smsLogsTable.receivedAt)).limit(limit).offset(offset),
      db.select({ value: count() }).from(smsLogsTable),
    ]);
    res.json({ data, total, limit, offset });
  });

  export default router;
  