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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.tappony.android.R
import com.tappony.android.ScanOutcome

@Composable
fun ScanScreen(vm: ScanViewModel, nfcAvailable: Boolean, nfcEnabled: () -> Boolean) {
    val profiles by vm.profiles.collectAsState()
    val active by vm.activeProfile.collectAsState()
    val state by vm.state.collectAsState()
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

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state is ScanState.Sending) {
                    CircularProgressIndicator(modifier = Modifier.size(72.dp))
                } else {
                    Icon(Icons.Filled.Contactless, contentDescription = null, tint = TapColors.Blue, modifier = Modifier.size(88.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(if (state is ScanState.Sending) R.string.scan_sending else R.string.scan_hold_tag),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        when (val s = state) {
            is ScanState.Done -> ResultCard(s.outcome)
            is ScanState.ReadFailed -> Notice(
                if (s.reason == "noNdef") stringResource(R.string.scan_requires_ndef) else stringResource(R.string.scan_hold_still),
            )
            else -> Unit
        }
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
        o.buildError != null -> TapColors.Fail
        r == null -> TapColors.Muted
        r.ok -> TapColors.Ok
        else -> TapColors.Fail
    }
    val headline = when {
        o.buildError != null -> stringResource(R.string.result_not_sent)
        r?.status != null -> "HTTP ${r.status}"
        else -> stringResource(R.string.result_network_error)
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(headline, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                r?.let { Text("${it.latencyMs} ms", color = TapColors.Muted) }
            }
            o.message?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = TapColors.Text) }
            Text(o.profileName, color = TapColors.Muted)
            if (o.uid.isNotEmpty()) Text(o.uid, style = Mono)
            val detail = listOf(o.chip, o.tagType).filter { it.isNotEmpty() }.joinToString(" · ")
            if (detail.isNotEmpty()) Text(detail, color = TapColors.Muted)
            if (o.randomUid) Text(stringResource(R.string.result_random_uid), color = TapColors.Warn)
            o.buildError?.let { Text(explainError(it), color = TapColors.Fail) }
            r?.error?.let { Text(it, color = TapColors.Fail, style = Mono) }
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
    else -> code
}
