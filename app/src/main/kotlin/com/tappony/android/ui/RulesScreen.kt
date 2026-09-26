package com.tappony.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.tappony.android.R
import com.tappony.android.TapPonyApp
import com.tappony.android.data.ProfileStore
import com.tappony.core.Profile
import com.tappony.core.Rule
import com.tappony.core.RuleMatch
import com.tappony.core.RuleSet
import com.tappony.core.Rules

/** Rules: route each tag to the right profiles (PROFILE_SCHEMA.md section 14). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as TapPonyApp
    val set by app.rules.current.collectAsState()
    val profiles by app.profiles.profiles.collectAsState()
    val history by app.history.entries.collectAsState(initial = emptyList())
    val lastUid = history.firstOrNull { it.uid.isNotEmpty() }?.uid
    var editing by remember { mutableStateOf<Rule?>(null) }

    fun save(s: RuleSet) = app.rules.save(s)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rules_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.rules_use))
                    Text(stringResource(R.string.rules_use_help), color = TapColors.Muted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(set.enabled, { save(set.copy(enabled = it)) })
            }

            Text(stringResource(R.string.rules_unmatched), style = MaterialTheme.typography.titleSmall, color = TapColors.BlueLight)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = set.unmatched != RuleSet.UNMATCHED_IGNORE,
                    onClick = { save(set.copy(unmatched = RuleSet.UNMATCHED_ACTIVE)) },
                    label = { Text(stringResource(R.string.rules_unmatched_active)) },
                )
                FilterChip(
                    selected = set.unmatched == RuleSet.UNMATCHED_IGNORE,
                    onClick = { save(set.copy(unmatched = RuleSet.UNMATCHED_IGNORE)) },
                    label = { Text(stringResource(R.string.rules_unmatched_ignore)) },
                )
            }

            Text(stringResource(R.string.rules_list), style = MaterialTheme.typography.titleSmall, color = TapColors.BlueLight)
            Text(stringResource(R.string.rules_order_help), color = TapColors.Muted, style = MaterialTheme.typography.bodySmall)
            set.rules.forEachIndexed { i, r ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().clickable { editing = r },
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name.ifBlank { stringResource(R.string.editor_untitled) }, style = MaterialTheme.typography.titleSmall)
                            Text(describe(r, profiles), color = TapColors.Muted, style = MaterialTheme.typography.bodySmall)
                            liveError(r, profiles)?.let { Text(ruleError(it), color = TapColors.Warn, style = MaterialTheme.typography.bodySmall) }
                        }
                        Switch(r.enabled, { on -> save(set.copy(rules = set.rules.map { if (it.id == r.id) it.copy(enabled = on) else it })) })
                        Column {
                            IconButton(enabled = i > 0, onClick = { save(set.copy(rules = set.rules.swap(i, i - 1))) }) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.rules_move_up))
                            }
                            IconButton(enabled = i < set.rules.size - 1, onClick = { save(set.copy(rules = set.rules.swap(i, i + 1))) }) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.rules_move_down))
                            }
                        }
                    }
                }
            }
            if (set.rules.isEmpty()) Text(stringResource(R.string.rules_empty), color = TapColors.Muted)
            TextButton(onClick = {
                editing = Rule(
                    id = "R-" + ProfileStore.newId(),
                    name = "",
                    match = RuleMatch("uid", "equals", lastUid ?: ""),
                    profiles = emptyList(),
                )
            }) { Text(stringResource(R.string.rules_add)) }
        }
    }

    editing?.let { original ->
        RuleDialog(
            original = original,
            profiles = profiles,
            lastUid = lastUid,
            isNew = set.rules.none { it.id == original.id },
            onSave = { r ->
                val exists = set.rules.any { it.id == r.id }
                save(set.copy(rules = if (exists) set.rules.map { if (it.id == r.id) r else it } else set.rules + r))
                editing = null
            },
            onDelete = { id ->
                save(set.copy(rules = set.rules.filter { it.id != id }))
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** Rules.error, counting only profiles that still exist. */
private fun liveError(r: Rule, profiles: List<Profile>): String? =
    Rules.error(r.copy(profiles = r.profiles.filter { id -> profiles.any { it.id == id } }))

private fun <T> List<T>.swap(a: Int, b: Int): List<T> = toMutableList().also { val t = it[a]; it[a] = it[b]; it[b] = t }

@Composable
private fun describe(r: Rule, profiles: List<Profile>): String {
    val names = r.profiles.mapNotNull { id -> profiles.firstOrNull { it.id == id }?.name }
    val target = if (names.isEmpty()) stringResource(R.string.rules_no_profiles) else names.joinToString(", ")
    return "${fieldLabel(r.match.field)} ${opLabel(r.match.op)} \"${r.match.value}\" → $target"
}

@Composable
private fun fieldLabel(f: String): String = when (f) {
    "uid" -> stringResource(R.string.rule_field_uid)
    "tag_type" -> stringResource(R.string.rule_field_tag_type)
    "chip" -> stringResource(R.string.rule_field_chip)
    "manufacturer" -> stringResource(R.string.rule_field_manufacturer)
    "payload" -> stringResource(R.string.rule_field_payload)
    "ndef_text" -> stringResource(R.string.rule_field_ndef_text)
    "ndef_uri" -> stringResource(R.string.rule_field_ndef_uri)
    else -> f
}

@Composable
private fun opLabel(o: String): String = when (o) {
    "equals" -> stringResource(R.string.rule_op_equals)
    "prefix" -> stringResource(R.string.rule_op_prefix)
    "contains" -> stringResource(R.string.rule_op_contains)
    "regex" -> stringResource(R.string.rule_op_regex)
    else -> o
}

@Composable
private fun ruleError(code: String): String = when (code) {
    "badRegex" -> stringResource(R.string.rule_err_regex)
    "noProfiles" -> stringResource(R.string.rule_err_profiles)
    else -> stringResource(R.string.rule_err_other)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleDialog(
    original: Rule,
    profiles: List<Profile>,
    lastUid: String?,
    isNew: Boolean,
    onSave: (Rule) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var r by remember(original.id) { mutableStateOf(original) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.rules_add else R.string.rules_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(r.name, { r = r.copy(name = it.take(64)) }, label = { Text(stringResource(R.string.editor_name)) }, singleLine = true)
                Text(stringResource(R.string.rules_when), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Rules.FIELDS.forEach { f ->
                        FilterChip(r.match.field == f, { r = r.copy(match = r.match.copy(field = f)) }, { Text(fieldLabel(f)) })
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Rules.OPS.forEach { o ->
                        FilterChip(r.match.op == o, { r = r.copy(match = r.match.copy(op = o)) }, { Text(opLabel(o)) })
                    }
                }
                OutlinedTextField(
                    r.match.value, { r = r.copy(match = r.match.copy(value = it)) },
                    label = { Text(stringResource(R.string.rules_value)) }, singleLine = true, textStyle = Mono,
                )
                if (r.match.field == "uid" && lastUid != null) {
                    TextButton(onClick = { r = r.copy(match = r.match.copy(value = lastUid)) }) {
                        Text(stringResource(R.string.rules_use_last_uid, lastUid))
                    }
                }
                Text(stringResource(R.string.rules_send_to), style = MaterialTheme.typography.labelLarge)
                profiles.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(p.id in r.profiles, { on ->
                            r = r.copy(profiles = if (on) r.profiles + p.id else r.profiles - p.id)
                        })
                        Text(p.name)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.rules_enabled), modifier = Modifier.weight(1f))
                    Switch(r.enabled, { r = r.copy(enabled = it) })
                }
                liveError(r, profiles)?.let { Text(ruleError(it), color = TapColors.Warn) }
                if (!isNew) {
                    TextButton(onClick = { onDelete(r.id) }) { Text(stringResource(R.string.rules_delete), color = TapColors.Fail) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(r) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
