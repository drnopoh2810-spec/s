import { pgTable, text, uuid, timestamp, numeric } from "drizzle-orm/pg-core";
  import { devicesTable } from "./devices";
  import type { WalletType } from "./transactions";

  export const smsLogsTable = pgTable("sms_logs", {
    id: uuid("id").primaryKey().defaultRandom(),
    deviceId: uuid("device_id").references(() => devicesTable.id),
    sender: text("sender").notNull(),
    body: text("body").notNull(),
    walletType: text("wallet_type").$type<WalletType>(),
    parsedAmount: numeric("parsed_amount", { precision: 12, scale: 2 }),
    parsedPhone: text("parsed_phone"),
    parsedTxId: text("parsed_tx_id"),
    matchedTransactionId: text("matched_transaction_id"),
    receivedAt: timestamp("received_at", { withTimezone: true }).notNull().defaultNow(),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  });

  export type SmsLog = typeof smsLogsTable.$inferSelect;
  