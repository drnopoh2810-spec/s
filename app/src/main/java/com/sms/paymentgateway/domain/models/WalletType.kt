package com.sms.paymentgateway.domain.models

enum class WalletType(val displayName: String, val senderNumbers: List<String>) {
    VODAFONE_CASH("Vodafone Cash", listOf("Vodafone", "VodafoneCash", "VF-Cash")),
    ORANGE_MONEY("Orange Money", listOf("Orange", "OrangeMoney")),
    ETISALAT_CASH("Etisalat Cash", listOf("Etisalat", "EtisalatCash")),
    FAWRY("Fawry", listOf("Fawry", "FAWRY")),
    INSTAPAY("InstaPay", listOf("InstaPay", "INSTAPAY")),
    UNKNOWN("Unknown", emptyList());

    companion object {
        fun fromSender(sender: String): WalletType {
            return values().firstOrNull { wallet ->
                wallet.senderNumbers.any { sender.contains(it, ignoreCase = true) }
            } ?: UNKNOWN
        }
    }
}
