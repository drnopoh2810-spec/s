import { pgTable, text, uuid, timestamp, boolean } from "drizzle-orm/pg-core";

  export const devicesTable = pgTable("devices", {
    id: uuid("id").primaryKey().defaultRandom(),
    name: text("name").notNull(),
    apiKey: text("api_key").notNull().unique(),
    isActive: boolean("is_active").notNull().default(true),
    lastSeenAt: timestamp("last_seen_at", { withTimezone: true }),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow().$onUpdate(() => new Date()),
  });

  export type Device = typeof devicesTable.$inferSelect;
  export type InsertDevice = typeof devicesTable.$inferInsert;
  