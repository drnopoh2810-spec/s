import http from "http";
  import express from "express";
  import { setupWebSocketServer } from "./lib/relay";
  import devicesRouter from "./routes/devices";
  import transactionsRouter from "./routes/transactions";
  import smsRouter from "./routes/sms";
  import relayRouter from "./routes/relay";

  const PORT = parseInt(process.env.PORT || "5000");

  const app = express();
  app.use(express.json());

  // Routes
  app.use("/api/v1", devicesRouter);
  app.use("/api/v1", transactionsRouter);
  app.use("/api/v1", smsRouter);
  app.use("/api/v1", relayRouter);
  app.get("/healthz", (_req, res) => res.json({ status: "ok" }));

  const server = http.createServer(app);
  setupWebSocketServer(server);

  server.listen(PORT, () => {
    console.log(`SMS Payment Gateway Relay v2.0.0 running on port ${PORT}`);
    console.log(`WebSocket: ws://0.0.0.0:${PORT}/device`);
    console.log(`API: http://0.0.0.0:${PORT}/api/v1`);
  });
  