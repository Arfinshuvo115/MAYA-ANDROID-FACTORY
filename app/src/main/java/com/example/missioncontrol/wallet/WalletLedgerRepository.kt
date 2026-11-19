package com.example.missioncontrol.wallet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID

data class WalletLedgerEntry(
    val id: String,
    val kind: String,
    val amount: String,
    val currency: String,
    val note: String,
    val createdAtMillis: Long
)

/**
 * A manual, device-local accounting ledger. It never initiates payments or withdrawals.
 */
class WalletLedgerRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "maya_wallet_ledger",
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun getAll(): List<WalletLedgerEntry> {
        val array = runCatching {
            JSONArray(preferences.getString(KEY_ENTRIES, "[]") ?: "[]")
        }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val kind = item.optString("kind")
            val currency = item.optString("currency").uppercase()
            val amount = item.optString("amount")
            val validAmount = runCatching { BigDecimal(amount).signum() > 0 }.getOrDefault(false)
            if (kind !in setOf(KIND_EARNING, KIND_WITHDRAWAL) ||
                !validAmount || !currency.matches(Regex("[A-Z]{3}"))) return@mapNotNull null
            WalletLedgerEntry(
                id = item.optString("id"),
                kind = kind,
                amount = amount,
                currency = currency,
                note = item.optString("note"),
                createdAtMillis = item.optLong("createdAtMillis")
            )
        }.sortedByDescending { it.createdAtMillis }
    }

    @Synchronized
    fun add(kind: String, amountText: String, currencyText: String, note: String): WalletLedgerEntry {
        require(kind == KIND_EARNING || kind == KIND_WITHDRAWAL) { "Choose earning or withdrawal." }
        val amount = BigDecimal(amountText.trim())
        require(amount.signum() > 0) { "Amount must be greater than zero." }
        val currency = currencyText.trim().uppercase()
        require(currency.matches(Regex("[A-Z]{3}"))) { "Enter a three-letter currency code, such as SAR or USD." }
        val entry = WalletLedgerEntry(
            id = UUID.randomUUID().toString(),
            kind = kind,
            amount = amount.stripTrailingZeros().toPlainString(),
            currency = currency,
            note = note.trim(),
            createdAtMillis = System.currentTimeMillis()
        )
        save(getAll() + entry)
        return entry
    }

    private fun save(entries: List<WalletLedgerEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("kind", entry.kind)
                    .put("amount", entry.amount)
                    .put("currency", entry.currency)
                    .put("note", entry.note)
                    .put("createdAtMillis", entry.createdAtMillis)
            )
        }
        check(preferences.edit().putString(KEY_ENTRIES, array.toString()).commit()) {
            "Could not save the wallet ledger."
        }
    }

    companion object {
        const val KIND_EARNING = "EARNING"
        const val KIND_WITHDRAWAL = "WITHDRAWAL"
        private const val KEY_ENTRIES = "entries_json"
    }
}
