package com.sms.paymentgateway.domain.models

enum class WalletType(val displayName: String, val senderNumbers: List<String>) {
    VODAFONE_CASH(
        "Vodafone Cash",
        listOf("Vodafone", "VodafoneCash", "VF-Cash", "VF Cash", "01010", "VODAFONE")
    ),
    ORANGE_MONEY(
        "Orange Money",
        listOf("Orange", "OrangeMoney", "Orange Money", "OrangeMny", "01020", "ORANGE")
    ),
    ETISALAT_CASH(
        "e& Cash",
        listOf("Etisalat", "EtisalatCash", "e& Cash", "eCash", "eand", "01022", "ETISALAT", "E&")
    ),
    WE_PAY(
        "WE Pay",
        listOf("WE", "WEPay", "WE Pay", "Telecom Egypt", "01015", "WEPAY")
    ),
    FAWRY(
        "Fawry",
        listOf("Fawry", "FAWRY", "FawryPay", "Fawry Pay")
    ),
    INSTAPAY(
        "InstaPay",
        listOf("InstaPay", "INSTAPAY", "Insta Pay", "InstaPay Egypt")
    ),
    BANK_MISR(
        "Bank Misr / Meeza",
        listOf("BankMisr", "Bank Misr", "Meeza", "MEEZA", "BANQUE MISR", "Banque Misr")
    ),
    CIB(
        "CIB",
        listOf("CIB", "Commercial International Bank", "CIB Egypt")
    ),
    UNKNOWN("Unknown", emptyList());

    companion object {
        fun fromSender(sender: String): WalletType {
            val trimmed = sender.trim()
            return values().firstOrNull { wallet ->
                wallet.senderNumbers.any { trimmed.contains(it, ignoreCase = true) }
            } ?: UNKNOWN
        }
    }
}
