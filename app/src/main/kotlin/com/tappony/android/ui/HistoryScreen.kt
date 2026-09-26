package com.tappony.android.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.tappony.android.R
import com.tappony.android.TapPonyApp
import com.tappony.android.data.HistoryEntry
import com.tappony.core.HistoryCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

private enum class HistoryFilter { ALL, OK, FAILED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as TapPonyApp
    val entries by app.history.entries.collectAsState(initial = emptyList())
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    var selected by remember { mutableStateOf<HistoryEntry?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val shown = when (filter) {
        HistoryFilter.ALL -> entries
        HistoryFilter.OK -> entries.filter { it.outcome == HistoryCsv.OK }
        HistoryFilter.FAILED -> entries.filter { it.outcome != HistoryCsv.OK }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(filter == HistoryFilter.ALL, { filter = HistoryFilter.ALL }, { Text(stringResource(R.string.history_all)) })
            FilterChip(filter == HistoryFilter.OK, { filter = HistoryFilter.OK }, { Text(stringResource(R.string.history_ok)) })
            FilterChip(filter == HistoryFilter.FAILED, { filter = HistoryFilter.FAILED }, { Text(stringResource(R.string.history_failed)) })
            Spacer(Modifier.width(8.dp))
            TextButton(enabled = entries.isNotEmpty(), onClick = { scope.launch { exportCsv(context, app) } }) {
                Text(stringResource(R.string.history_export))
            }
            TextButton(enabled = entries.isNotEmpty(), onClick = { confirmClear = true }) {
                Text(stringResource(R.string.history_clear), color = TapColors.Fail)
            }
        }

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.history_empty), color = TapColors.Muted, modifier = Modifier.padding(32.dp))
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { e -> HistoryRowCard(e) { selected = e } }
            }
        }
    }

    selected?.let { e ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(e.profileName) },
            text = {
                SelectionContainer {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(formatTime(e.timeMs), color = TapColors.Muted)
                        Text(outcomeLabel(e), color = outcomeColor(e.outcome))
                        e.message?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                        if (e.uid.isNotEmpty()) Text(e.uid, style = Mono)
                        listOf(e.chip, e.tagType).filter { it.isNotEmpty() }.joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                            Text(it, color = TapColors.Muted)
                        }
                        if (e.error.isNotEmpty()) Text(explainError(e.error), color = TapColors.Fail)
                        if (e.request.isNotEmpty()) Text(e.request, style = Mono, color = TapColors.Muted)
                        e.requestBody?.let {
                            Text(stringResource(R.string.history_request_body), style = MaterialTheme.typography.labelMedium)
                            Text(it, style = Mono, color = TapColors.Muted)
                        }
                        e.responseBody?.let {
                            Text(stringResource(R.string.history_response_body), style = MaterialTheme.typography.labelMedium)
                            Text(it, style = Mono, color = TapColors.Muted)
                        }
                        if (e.requestBody == null && e.responseBody == null) {
                            Text(stringResource(R.string.history_bodies_off), color = TapColors.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.close)) } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { app.history.clear() }
                    confirmClear = false
                }) { Text(stringResource(R.string.history_clear), color = TapColors.Fail) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun HistoryRowCard(e: HistoryEntry, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(outcomeLabel(e), color = outcomeColor(e.outcome), modifier = Modifier.weight(1f))
                Text(formatTime(e.timeMs), color = TapColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
            Text(e.profileName, style = MaterialTheme.typography.bodyMedium)
            if (e.uid.isNotEmpty()) Text(e.uid, style = Mono, color = TapColors.Muted)
            e.message?.let { Text(it, color = TapColors.Text, maxLines = 1) }
        }
    }
}

@Composable
private fun outcomeLabel(e: HistoryEntry): String = when (e.outcome) {
    HistoryCsv.OK, HistoryCsv.HTTP_ERROR -> "HTTP ${e.status ?: ""}".trim() + (e.latencyMs?.let { " · $it ms" } ?: "")
    HistoryCsv.NETWORK_ERROR -> stringResource(R.string.result_network_error)
    else -> stringResource(R.string.result_not_sent)
}

private fun outcomeColor(outcome: String) = when (outcome) {
    HistoryCsv.OK -> TapColors.Ok
    else -> TapColors.Fail
}

private fun formatTime(ms: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(ms))

/** Writes the CSV to app cache and hands it to the share sheet through the FileProvider. */
private suspend fun exportCsv(context: Context, app: TapPonyApp) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        File(dir, "tappony-history-${System.currentTimeMillis()}.csv").apply {
            writeText(HistoryCsv.document(app.history.exportRows()))
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/csv")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}
