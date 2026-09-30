package com.tappony.android.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.tappony.android.Entitlements
import com.tappony.android.R
import com.tappony.android.ScanOutcome

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(vm: ScanViewModel, nfcAvailable: Boolean, nfcEnabled: () -> Boolean) {
    val profiles by vm.profiles.collectAsState()
    val active by vm.activeProfile.collectAsState()
    val state by vm.state.collectAsState()
    val batch by vm.batch.collectAsState()
    val queued by vm.queued.collectAsState(initial = 0)
    val flushing by vm.flushing.collectAsState()
    val reads by vm.reads.collectAsState()
    val haptic = LocalHapticFeedback.current
    // Only reads that happen while this screen is shown; coming back to the tab must not buzz.
    val readsAtEntry = remember { reads }
    LaunchedEffect(reads) {
        if (reads > readsAtEntry && active?.after?.haptic != false) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    // Re-check the NFC switch whenever the lifecycle changes, so returning from NFC settings updates the notice.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val nfcOn = remember(lifecycleState) { nfcEnabled() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box {
            OutlinedButton(onClick = { menu = true }, enabled = profiles.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(active?.name ?: stringResource(R.string.scan_no_profile), modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                profiles.forEach { p ->
                    DropdownMenuItem(text = { Text(p.name) }, onClick = { vm.select(p); menu = false })
                }
            }
        }

        when {
            !nfcAvailable -> Notice(stringResource(R.string.scan_nfc_unavailable))
            !nfcOn -> {
                Notice(stringResource(R.string.scan_nfc_off))
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) {
                    Text(stringResource(R.string.scan_open_nfc_settings))
                }
            }
            profiles.isEmpty() -> Notice(stringResource(R.string.scan_create_profile_first))
        }

        if (Entitlements.batch) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = batch.on,
                onClick = { vm.setBatch(!batch.on) },
                label = { Text(stringResource(R.string.scan_batch)) },
            )
            if (batch.on) {
                Text(stringResource(R.string.scan_batch_count, batch.count), color = TapColors.Text, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.newBatch() }) { Text(stringResource(R.string.scan_batch_new)) }
            }
        }
        if (queued > 0) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.scan_queued, queued), color = TapColors.Warn, modifier = Modifier.weight(1f))
                    TextButton(enabled = !flushing, onClick = { vm.sendQueuedNow() }) {
                        Text(stringResource(if (flushing) R.string.scan_sending else R.string.scan_send_now))
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state is ScanState.Sending || (batch.on && batch.inFlight > 0)) {
                    CircularProgressIndicator(modifier = Modifier.size(72.dp))
                } else {
                    Icon(Icons.Filled.Contactless, contentDescription = null, tint = TapColors.Blue, modifier = Modifier.size(88.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(
                        when {
                            batch.on -> R.string.scan_batch_hold
                            state is ScanState.Sending -> R.string.scan_sending
                            else -> R.string.scan_hold_tag
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (batch.on && batch.skippedRepeats > 0) {
                    Text(stringResource(R.string.scan_batch_skipped, batch.skippedRepeats), color = TapColors.Muted)
                }
            }
        }

        if (batch.on) {
            batch.results.forEach { BatchRow(it) }
        }
        when (val s = state) {
            is ScanState.Done -> if (!batch.on) s.outcomes.forEach { ResultCard(it) }
            is ScanState.ReadFailed -> Notice(
                when (s.reason) {
                    "noNdef" -> stringResource(R.string.scan_requires_ndef)
                    "noRule" -> stringResource(R.string.scan_no_rule)
                    "unknownLaunch" -> stringResource(R.string.scan_launch_unknown)
                    "noProfile" -> stringResource(R.string.scan_create_profile_first)
                    else -> stringResource(R.string.scan_hold_still)
                },
            )
            else -> Unit
        }
    }
}

@Composable
private fun BatchRow(o: ScanOutcome) {
    val r = o.result
    val (label, color) = when {
        o.queued -> stringResource(R.string.result_queued_short) to TapColors.Warn
        o.buildError != null -> stringResource(R.string.result_not_sent) to TapColors.Fail
        r?.status != null -> "HTTP ${r.status}" to (if (r.ok) TapColors.Ok else TapColors.Fail)
        else -> stringResource(R.string.result_network_error) to TapColors.Fail
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(o.uid.ifEmpty { "?" }, style = Mono, modifier = Modifier.weight(1f))
        (o.resultText ?: o.message)?.let { Text(it, color = TapColors.Muted, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) }
        Text(label, color = color)
    }
}

@Composable
private fun Notice(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(16.dp), color = TapColors.Warn)
    }
}

@Composable
fun ResultCard(o: ScanOutcome) {
    val r = o.result
    val color = when {
        o.queued -> TapColors.Warn
        o.buildError != null -> TapColors.Fail
        r == null -> TapColors.Muted
        r.ok -> TapColors.Ok
        else -> TapColors.Fail
    }
    val headline = when {
        o.queued -> stringResource(R.string.result_queued)
        o.buildError != null -> stringResource(R.string.result_not_sent)
        r?.status != null -> "HTTP ${r.status}"
        else -> stringResource(R.string.result_network_error)
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(headline, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                r?.let { Text(stringResource(R.string.result_latency, it.latencyMs), color = TapColors.Muted) }
            }
            o.resultText?.let { Text(it, style = MaterialTheme.typography.headlineSmall, color = color) }
            o.message?.takeIf { it != o.resultText }?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = TapColors.Text) }
            Text(o.profileName, color = TapColors.Muted)
            if (o.uid.isNotEmpty()) Text(o.uid, style = Mono)
            val detail = listOf(o.chip, o.tagType).filter { it.isNotEmpty() }.joinToString(" · ")
            if (detail.isNotEmpty()) Text(detail, color = TapColors.Muted)
            if (o.randomUid) Text(stringResource(R.string.result_random_uid), color = TapColors.Warn)
            o.buildError?.let { Text(explainError(it), color = TapColors.Fail) }
            r?.error?.let { Text(explainError(it), color = TapColors.Fail, style = Mono) }
            r?.responseBody?.takeIf { it.isNotBlank() }?.let { Text(it.take(600), style = Mono, color = TapColors.Muted) }
        }
    }
}

@Composable
fun explainError(code: String): String = when {
    code == "hostPolicy:localHttpNotEnabled" -> stringResource(R.string.err_local_http_not_enabled)
    code == "hostPolicy:plainHttpPublic" -> stringResource(R.string.err_plain_http_public)
    code == "hostPolicy:numericHost" -> stringResource(R.string.err_numeric_host)
    code == "hostPolicy:userinfo" -> stringResource(R.string.err_userinfo)
    code.startsWith("hostPolicy:") || code.startsWith("urlTemplate:") -> stringResource(R.string.err_bad_url)
    code == "template:unknownSecret" -> stringResource(R.string.err_unknown_secret)
    code == "template:invalidJsonBody" -> stringResource(R.string.err_invalid_json)
    code.startsWith("template:") -> stringResource(R.string.err_template, code.removePrefix("template:"))
    code == "badHeaderName" -> stringResource(R.string.err_header_name)
    code == "malformedUrl" -> stringResource(R.string.err_malformed_url)
    code == "redirect" -> stringResource(R.string.err_redirect_refused)
    code == "tooManyRedirects" -> stringResource(R.string.err_too_many_redirects)
    code == "profileDeleted" -> stringResource(R.string.err_profile_deleted)
    code.startsWith("UnknownHostException") -> stringResource(R.string.err_unknown_host, code)
    code.startsWith("SocketTimeoutException") || code.startsWith("InterruptedIOException") -> stringResource(R.string.err_timeout, code)
    code.startsWith("ConnectException") || code.startsWith("NoRouteToHostException") -> stringResource(R.string.err_connect, code)
    code.startsWith("SSL") || code.startsWith("CertPathValidatorException") || code.startsWith("CertificateException") -> stringResource(R.string.err_tls, code)
    else -> code
}
