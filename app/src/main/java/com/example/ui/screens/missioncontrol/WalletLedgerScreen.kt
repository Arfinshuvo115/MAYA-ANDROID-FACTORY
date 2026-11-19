package com.example.ui.screens.missioncontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.missioncontrol.wallet.WalletLedgerEntry
import com.example.missioncontrol.wallet.WalletLedgerRepository
import java.math.BigDecimal

@Composable
fun WalletLedgerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { WalletLedgerRepository(context) }
    val entries = remember { mutableStateListOf<WalletLedgerEntry>() }
    var kind by remember { mutableStateOf(WalletLedgerRepository.KIND_EARNING) }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("SAR") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        entries.clear()
        entries.addAll(repository.getAll())
    }

    LaunchedEffect(repository) { refresh() }

    val balances = entries.groupBy { it.currency }.mapValues { (_, rows) ->
        rows.fold(BigDecimal.ZERO) { total, entry ->
            val value = BigDecimal(entry.amount)
            if (entry.kind == WalletLedgerRepository.KIND_EARNING) total + value else total - value
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Column {
                Text("Wallet & Withdrawals", style = MaterialTheme.typography.titleLarge)
                Text("Manual local ledger · no money movement", style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (kind == WalletLedgerRepository.KIND_EARNING) {
                Button(onClick = { kind = WalletLedgerRepository.KIND_EARNING }) { Text("Earning") }
            } else {
                OutlinedButton(onClick = { kind = WalletLedgerRepository.KIND_EARNING }) { Text("Earning") }
            }
            if (kind == WalletLedgerRepository.KIND_WITHDRAWAL) {
                Button(onClick = { kind = WalletLedgerRepository.KIND_WITHDRAWAL }) { Text("Withdrawal") }
            } else {
                OutlinedButton(onClick = { kind = WalletLedgerRepository.KIND_WITHDRAWAL }) { Text("Withdrawal") }
            }
        }
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Amount") },
            placeholder = { Text("0.00") },
            singleLine = true
        )
        OutlinedTextField(
            value = currency,
            onValueChange = { currency = it.uppercase(); error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Currency code") },
            placeholder = { Text("SAR") },
            singleLine = true
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Reference / note (optional)") },
            singleLine = true
        )
        Button(
            onClick = {
                runCatching { repository.add(kind, amount, currency, note) }
                    .onSuccess {
                        amount = ""
                        note = ""
                        error = null
                        refresh()
                    }
                    .onFailure { error = it.message ?: "Could not save ledger entry." }
            },
            enabled = amount.isNotBlank() && currency.length == 3,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save Ledger Entry") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Text("Balance by currency", style = MaterialTheme.typography.titleMedium)
        if (balances.isEmpty()) {
            Text("No ledger entries yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            balances.toSortedMap().forEach { (code, balance) ->
                Text("$code  ${balance.stripTrailingZeros().toPlainString()}", style = MaterialTheme.typography.bodyLarge)
            }
        }
        Text("Ledger history · ${entries.size}", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${if (entry.kind == WalletLedgerRepository.KIND_EARNING) "Earning" else "Withdrawal"} · ${entry.currency} ${entry.amount}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (entry.note.isNotBlank()) Text(entry.note, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Text(
            "Important: entries are user-entered records stored on this device. This screen does not connect to a bank, payment provider, or withdrawal service and cannot send or receive funds.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
