export type WalletType = "VODAFONE_CASH" | "ORANGE_MONEY" | "ETISALAT_CASH" | "FAWRY" | "INSTAPAY";

  export interface ParsedSms {
    walletType: WalletType | null;
    amount: number | null;
    senderPhone: string | null;
    txId: string | null;
  }

  const WALLET_PATTERNS = [
    {
      type: "VODAFONE_CASH" as WalletType,
      senders: ["VCASH", "Vodafone Cash", "VF-Cash"],
      amountRegex: /(d+(?:.d+)?)s*(?:جنيه|EGP|ج.م)/i,
      txIdRegex: /(?:رقم العملية|Transaction ID|Ref)[:s]*([A-Z0-9]+)/i,
      phoneRegex: /(?:من|from)s*(+?20d{10}|d{11})/i,
    },
    {
      type: "ORANGE_MONEY" as WalletType,
      senders: ["Orange Money", "ORANGE"],
      amountRegex: /(d+(?:.d+)?)s*(?:جنيه|EGP|ج.م)/i,
      txIdRegex: /(?:Ref|رقم)[:s]*([A-Z0-9]+)/i,
      phoneRegex: /(+?20d{10}|d{11})/i,
    },
    {
      type: "ETISALAT_CASH" as WalletType,
      senders: ["E-Cash", "Etisalat"],
      amountRegex: /(d+(?:.d+)?)s*(?:جنيه|EGP|ج.م)/i,
      txIdRegex: /(?:Ref|رقم)[:s]*([A-Z0-9]+)/i,
      phoneRegex: /(+?20d{10}|d{11})/i,
    },
    {
      type: "FAWRY" as WalletType,
      senders: ["Fawry", "FAWRY"],
      amountRegex: /(d+(?:.d+)?)s*(?:جنيه|EGP|ج.م|LE)/i,
      txIdRegex: /(?:Fawry Ref|رقم)[:s]*([0-9]+)/i,
      phoneRegex: /(+?20d{10}|d{11})/i,
    },
    {
      type: "INSTAPAY" as WalletType,
      senders: ["InstaPay", "instapay"],
      amountRegex: /(d+(?:.d+)?)s*(?:EGP|جنيه|ج.م)/i,
      txIdRegex: /(?:ID|Ref)[:s]*([A-Z0-9-]+)/i,
      phoneRegex: /(+?20d{10}|d{11})/i,
    },
  ];

  export function parseSms(sender: string, body: string): ParsedSms {
    const result: ParsedSms = { walletType: null, amount: null, senderPhone: null, txId: null };
    let matched = WALLET_PATTERNS.find(p =>
      p.senders.some(s => sender.toLowerCase().includes(s.toLowerCase()) || body.toLowerCase().includes(s.toLowerCase()))
    );
    if (!matched) {
      const b = body.toLowerCase();
      if (b.includes("vodafone") || b.includes("vcash")) matched = WALLET_PATTERNS[0];
      else if (b.includes("orange")) matched = WALLET_PATTERNS[1];
      else if (b.includes("fawry")) matched = WALLET_PATTERNS[3];
      else if (b.includes("instapay")) matched = WALLET_PATTERNS[4];
    }
    if (matched) {
      result.walletType = matched.type;
      const am = body.match(matched.amountRegex);
      if (am) result.amount = parseFloat(am[1]);
      if (matched.txIdRegex) { const t = body.match(matched.txIdRegex); if (t) result.txId = t[1]; }
      if (matched.phoneRegex) { const p = body.match(matched.phoneRegex); if (p) result.senderPhone = p[1].replace(/+20/, "0"); }
    }
    return result;
  }

  export function calculateMatchConfidence(parsed: ParsedSms, expectedAmount: number, expectedPhone: string, expectedTxId?: string | null): number {
    let confidence = 0, checks = 0;
    checks++;
    if (parsed.amount !== null) {
      const diff = Math.abs(parsed.amount - expectedAmount);
      confidence += diff === 0 ? 1 : diff < 0.01 ? 0.95 : diff < 1 ? 0.5 : 0;
    }
    checks++;
    if (parsed.senderPhone) {
      const norm = (p: string) => p.replace(/D/g, "").slice(-10);
      if (norm(parsed.senderPhone) === norm(expectedPhone)) confidence += 1;
    }
    if (expectedTxId && parsed.txId) {
      checks++;
      if (parsed.txId.toLowerCase() === expectedTxId.toLowerCase()) confidence += 1;
    }
    return checks > 0 ? confidence / checks : 0;
  }
  