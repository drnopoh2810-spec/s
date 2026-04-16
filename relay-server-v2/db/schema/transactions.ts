import { pgTable, text, uuid, timestamp, numeric } from "drizzle-orm/pg-core";
  import { devicesTable } from "./devices";

  export const WALLET_TYPES = ["VODAFONE_CASH", "ORANGE_MONEY", "ETISALAT_CASH", "FAWRY", "INSTAPAY"] as const;
  export type WalletType = typeof WALLET_TYPES[number];
  export const TRANSACTION_STATUSES = ["PENDING", "CONFIRMED", "EXPIRED", "FAILED"] as const;
  export type TransactionStatus = typeof TRANSACTION_STATUSES[number];

  export const transactionsTable = pgTable("transactions", {
    id: text("id").primaryKey(),
    deviceId: uuid("device_id").references(() => devicesTable.id),
    amount: numeric("amount", { precision: 12, scale: 2 }).notNull(),
    phoneNumber: text("phone_number").notNull(),
    walletType: text("wallet_type").$type<WalletType>(),
    expectedTxId: text("expected_tx_id"),
    webhookUrl: text("webhook_url"),
    status: text("status").$type<TransactionStatus>().notNull().default("PENDING"),
    confirmedAt: timestamp("confirmed_at", { withTimezone: true }),
    expiresAt: timestamp("expires_at", { withTimezone: true }).notNull(),
    confirmedAmount: numeric("confirmed_amount", { precision: 12, scale: 2 }),
    confirmedPhone: text("confirmed_phone"),
    walletTxId: text("wallet_tx_id"),
    confidence: numeric("confidence", { precision: 4, scale: 2 }),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow().$onUpdate(() => new Date()),
  });

  export type Transaction = typeof transactionsTable.$inferSelect;
  