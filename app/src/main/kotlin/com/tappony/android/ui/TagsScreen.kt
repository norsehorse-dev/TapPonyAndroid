package com.tappony.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.tappony.android.nfc.WriteJob
import com.tappony.core.Profile
import com.tappony.core.TagEntry

private enum class WriteKind { URL, TEXT, LAUNCH, MIRROR }

/** Tags: write your own tags, and name the ones you use (PROFILE_SCHEMA.md section 16). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(vm: TagsViewModel) {
    val app = LocalContext.current.applicationContext as TapPonyApp
    val registry by app.tags.current.collectAsState()
    val profiles by app.profiles.profiles.collectAsState()
    val history by app.history.entries.collectAsState(initial = emptyList())
    val state by vm.state.collectAsState()

    var kind by remember { mutableStateOf(WriteKind.LAUNCH) }
    var content by remember { mutableStateOf("https://") }
    var launchLabel by remember { mutableStateOf("") }
    var launchProfile by remember { mutableStateOf<String?>(null) }
    var lock by remember { mutableStateOf(false) }
    var confirmLock by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TagEntry?>(null) }

    val lastUid = history.firstOrNull { it.uid.isNotEmpty() }?.uid
    // A write armed here must not fire on a tag tapped after leaving the screen.
    DisposableEffect(Unit) { onDispose { vm.cancel() } }

    fun job(): WriteJob = when (kind) {
        WriteKind.URL -> WriteJob.Url(content.trim(), lock)
        WriteKind.TEXT -> WriteJob.Text(content, lock)
        WriteKind.MIRROR -> WriteJob.MirrorUid(lock)
        WriteKind.LAUNCH -> WriteJob.Launch(vm.newToken(), launchLabel.trim(), launchProfile, lock)
    }

    val canWrite = when (kind) {
        WriteKind.URL -> content.trim().let { it.contains(':') && it.length > 3 }
        WriteKind.TEXT -> content.isNotEmpty()
        else -> true
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.tags_write), style = MaterialTheme.typography.titleSmall, color = TapColors.BlueLight)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WriteKind.values().forEach { k ->
                FilterChip(
                    selected = kind == k,
                    onClick = {
                        kind = k
                        if (k == WriteKind.URL && content.isEmpty()) content = "https://"
                        if (k == WriteKind.TEXT && content == "https://") content = ""
                    },
                    label = { Text(kindLabel(k)) },
                )
            }
        }
        Text(kindHelp(kind), color = TapColors.Muted, style = MaterialTheme.typography.bodySmall)
        when (kind) {
            WriteKind.URL, WriteKind.TEXT -> OutlinedTextField(
                content, { content = it },
                label = { Text(stringResource(if (kind == WriteKind.URL) R.string.tags_content_url else R.string.tags_content_text)) },
                textStyle = Mono, modifier = Modifier.fillMaxWidth(), singleLine = kind == WriteKind.URL,
            )
            WriteKind.LAUNCH -> {
                OutlinedTextField(
                    launchLabel, { launchLabel = it.take(64) },
                    label = { Text(stringResource(R.string.tags_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (Entitlements.tagDefaults) ProfilePicker(profiles, launchProfile) { launchProfile = it }
            }
            WriteKind.MIRROR -> Unit
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.tags_lock))
                Text(stringResource(R.string.tags_lock_help), color = TapColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(lock, { lock = it })
        }

        WriteCard(state, onCancel = { vm.cancel() })
        Button(
            enabled = canWrite && state !is WriteState.Waiting,
            onClick = { if (lock) confirmLock = true else vm.arm(job()) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (lock) R.string.tags_write_and_lock else R.string.tags_write_button)) }

        Text(stringResource(R.string.tags_registry), style = MaterialTheme.typography.titleSmall, color = TapColors.BlueLight,
            modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.tags_registry_help), color = TapColors.Muted, style = MaterialTheme.typography.bodySmall)
        if (registry.tags.isEmpty()) Text(stringResource(R.string.tags_registry_empty), color = TapColors.Muted)
        registry.tags.forEach { t ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().clickable { editing = t },
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(t.label.ifBlank { stringResource(R.string.editor_untitled) }, style = MaterialTheme.typography.titleSmall)
                    if (t.uid.isNotEmpty()) Text(t.uid, style = Mono, color = TapColors.Muted)
                    if (t.token.isNotEmpty()) Text(stringResource(R.string.tags_has_launch_link), color = TapColors.BlueLight, style = MaterialTheme.typography.bodySmall)
                    t.profile?.let { id ->
                        Text(
                            stringResource(R.string.tags_goes_to, profiles.firstOrNull { it.id == id }?.name ?: stringResource(R.string.tags_profile_missing)),
                            color = TapColors.Muted, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        val known = lastUid != null && registry.tags.any { it.uid == lastUid }
        if (lastUid != null && !known && Entitlements.canAddTag(registry.tags.size)) {
            TextButton(onClick = { editing = TagEntry(uid = lastUid, label = "") }) {
                Text(stringResource(R.string.tags_add_last, lastUid))
            }
        }
    }

    if (confirmLock) {
        var understood by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { confirmLock = false },
            title = { Text(stringResource(R.string.tags_lock_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.tags_lock_confirm_body))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(understood, { understood = it })
                        Text(stringResource(R.string.tags_lock_confirm_check))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = understood, onClick = {
                    confirmLock = false
                    vm.arm(job())
                }) { Text(stringResource(R.string.tags_write_and_lock), color = TapColors.Fail) }
            },
            dismissButton = { TextButton(onClick = { confirmLock = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    editing?.let { original ->
        TagDialog(
            original = original,
            profiles = profiles,
            isNew = registry.tags.none { it == original },
            onSave = { e ->
                app.tags.upsert(e, replacing = original)
                editing = null
            },
            onDelete = {
                app.tags.remove(original)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun WriteCard(state: WriteState, onCancel: () -> Unit) {
    when (state) {
        is WriteState.Idle -> Unit
        is WriteState.Waiting -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Text(stringResource(R.string.tags_write_waiting), modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        }
        is WriteState.Done -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Contactless, contentDescription = null, tint = TapColors.Ok)
                Column {
                    Text(stringResource(if (state.locked) R.string.tags_write_done_locked else R.string.tags_write_done), color = TapColors.Ok)
                    if (state.uid.isNotEmpty()) Text(state.uid, style = Mono, color = TapColors.Muted)
                }
            }
        }
        is WriteState.Failed -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
            Text(writeError(state.code), color = TapColors.Fail, modifier = Modifier.padding(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfilePicker(profiles: List<Profile>, selected: String?, onSelect: (String?) -> Unit) {
    Text(stringResource(R.string.tags_default_profile), style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected == null, { onSelect(null) }, { Text(stringResource(R.string.tags_no_default)) })
        profiles.forEach { p -> FilterChip(selected == p.id, { onSelect(p.id) }, { Text(p.name) }) }
    }
}

@Composable
private fun TagDialog(
    original: TagEntry,
    profiles: List<Profile>,
    isNew: Boolean,
    onSave: (TagEntry) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var t by remember(original) { mutableStateOf(original) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.tags_add else R.string.tags_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (t.uid.isNotEmpty()) Text(t.uid, style = Mono, color = TapColors.Muted)
                if (t.token.isNotEmpty()) Text(stringResource(R.string.tags_has_launch_link), color = TapColors.BlueLight)
                OutlinedTextField(t.label, { t = t.copy(label = it.take(64)) }, label = { Text(stringResource(R.string.tags_label)) }, singleLine = true)
                if (Entitlements.tagDefaults) {
                    OutlinedTextField(t.notes, { t = t.copy(notes = it.take(500)) }, label = { Text(stringResource(R.string.tags_notes)) })
                    ProfilePicker(profiles, t.profile) { t = t.copy(profile = it) }
                }
                if (!isNew) TextButton(onClick = onDelete) { Text(stringResource(R.string.tags_delete), color = TapColors.Fail) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(t) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun kindLabel(k: WriteKind): String = stringResource(
    when (k) {
        WriteKind.URL -> R.string.tags_kind_url
        WriteKind.TEXT -> R.string.tags_kind_text
        WriteKind.LAUNCH -> R.string.tags_kind_launch
        WriteKind.MIRROR -> R.string.tags_kind_mirror
    },
)

@Composable
private fun kindHelp(k: WriteKind): String = stringResource(
    when (k) {
        WriteKind.URL -> R.string.tags_kind_url_help
        WriteKind.TEXT -> R.string.tags_kind_text_help
        WriteKind.LAUNCH -> R.string.tags_kind_launch_help
        WriteKind.MIRROR -> R.string.tags_kind_mirror_help
    },
)

@Composable
private fun writeError(code: String): String = stringResource(
    when (code) {
        "readOnly" -> R.string.tags_err_read_only
        "tooSmall" -> R.string.tags_err_too_small
        "notNdef" -> R.string.tags_err_not_ndef
        "randomUid" -> R.string.tags_err_random_uid
        "lockUnsupported" -> R.string.tags_err_lock_unsupported
        "lockFailed" -> R.string.tags_err_lock_failed
        "moved" -> R.string.tags_err_moved
        else -> R.string.tags_err_failed
    },
)
