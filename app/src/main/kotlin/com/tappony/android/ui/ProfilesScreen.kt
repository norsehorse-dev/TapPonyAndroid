package com.tappony.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.tappony.android.Entitlements
import com.tappony.android.R
import com.tappony.android.TapPonyApp
import com.tappony.android.data.ProfileStore
import com.tappony.core.BodySpec
import com.tappony.core.BodyType
import com.tappony.core.Presets
import com.tappony.core.Profile
import com.tappony.core.RequestSpec

@Composable
fun ProfilesScreen(onOpen: (String) -> Unit, onOpenRules: () -> Unit) {
    val app = LocalContext.current.applicationContext as TapPonyApp
    val profiles by app.profiles.profiles.collectAsState()
    val rules by app.rules.current.collectAsState()
    var picking by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (profiles.isEmpty()) {
            Text(
                stringResource(R.string.profiles_empty),
                color = TapColors.Muted,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (Entitlements.rules) item(key = "rules") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().clickable { onOpenRules() },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.rules_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                !rules.enabled -> stringResource(R.string.rules_status_off)
                                rules.rules.size == 1 -> stringResource(R.string.rules_status_on_one)
                                else -> stringResource(R.string.rules_status_on, rules.rules.size)
                            },
                            color = TapColors.Muted,
                        )
                    }
                }
            }
            items(profiles, key = { it.id }) { p ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(p.id) },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text("${p.request.method} ${p.request.url}", style = Mono, color = TapColors.Muted, maxLines = 2)
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { if (Entitlements.canAddProfile(profiles.size)) picking = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.profiles_new)) }
    }

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.profiles_new)) },
            text = {
                LazyColumn {
                    item {
                        PresetRow(stringResource(R.string.preset_blank_title)) {
                            val p = Profile(
                                id = ProfileStore.newId(),
                                name = app.getString(R.string.preset_blank_title),
                                request = RequestSpec(url = "https://", body = BodySpec(BodyType.JSON, "{\"uid\":\"{uid}\"}")),
                            )
                            app.profiles.save(p)
                            picking = false
                            onOpen(p.id)
                        }
                    }
                    items(Presets.ALL) { preset ->
                        PresetRow(presetTitle(preset.key), presetExplainer(preset.key)) {
                            val p = preset.create(ProfileStore.newId())
                            app.profiles.save(p)
                            picking = false
                            onOpen(p.id)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PresetRow(title: String, explainer: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) {
        Text(title)
        explainer?.let { Text(it, color = TapColors.Muted, style = MaterialTheme.typography.bodySmall) }
    }
    HorizontalDivider()
}

@Composable
fun presetTitle(key: String): String {
    val ctx = LocalContext.current
    val id = ctx.resources.getIdentifier("preset_${key}_title", "string", ctx.packageName)
    return if (id != 0) stringResource(id) else key
}

@Composable
fun presetExplainer(key: String): String? {
    val ctx = LocalContext.current
    val id = ctx.resources.getIdentifier("preset_${key}_explainer", "string", ctx.packageName)
    return if (id != 0) stringResource(id) else null
}
