package com.tappony.android.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.tappony.android.R
import com.tappony.android.ScanEngine
import com.tappony.android.ScanOutcome
import com.tappony.android.TapPonyApp
import com.tappony.core.Auth
import com.tappony.core.BodyType
import com.tappony.core.FormField
import com.tappony.core.Header
import com.tappony.core.HostPolicy
import com.tappony.core.Profile
import com.tappony.core.ProfileCodec
import com.tappony.core.RequestBuilder
import com.tappony.core.Template
import com.tappony.core.TemplateException
import com.tappony.core.Variables
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(profileId: String, engine: ScanEngine, onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as TapPonyApp
    val original = remember(profileId) { app.profiles.get(profileId) }
    if (original == null) {
        LaunchedEffect(profileId) { onDone() }
        return
    }
    var p by remember(profileId) { mutableStateOf(original) }
    var testing by remember { mutableStateOf(false) }
    var testOutcome by remember { mutableStateOf<ScanOutcome?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val secretNames = remember(profileId) { app.secrets.names().toSet() }
    val problems = validate(p, secretNames)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(p.name.ifBlank { stringResource(R.string.editor_untitled) }, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                actions = {
                    TextButton(onClick = {
                        app.profiles.save(p)
                        onDone()
                    }) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(p.name, { p = p.copy(name = it.take(64)) }, label = { Text(stringResource(R.string.editor_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            Section(stringResource(R.string.editor_request))
            ChipRow(Profile.METHODS, p.request.method) { p = p.copy(request = p.request.copy(method = it)) }
            OutlinedTextField(
                p.request.url, { p = p.copy(request = p.request.copy(url = it.trim())) },
                label = { Text(stringResource(R.string.editor_url)) }, textStyle = Mono, modifier = Modifier.fillMaxWidth(),
            )
            if (p.request.url.startsWith("http://", ignoreCase = true)) {
                SwitchRow(stringResource(R.string.editor_allow_local_http), p.request.allowLocalHttp) {
                    p = p.copy(request = p.request.copy(allowLocalHttp = it))
                }
                Text(stringResource(R.string.editor_local_http_warning), color = TapColors.Warn, style = MaterialTheme.typography.bodySmall)
            }
            SwitchRow(stringResource(R.string.editor_follow_redirects), p.request.followRedirects) {
                p = p.copy(request = p.request.copy(followRedirects = it))
            }

            Section(stringResource(R.string.editor_headers))
            p.request.headers.forEachIndexed { i, h ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(h.name, { v -> p = p.withHeader(i, h.copy(name = v.trim())) }, label = { Text(stringResource(R.string.editor_header_name)) }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(h.value, { v -> p = p.withHeader(i, h.copy(value = v)) }, label = { Text(stringResource(R.string.editor_header_value)) }, modifier = Modifier.weight(1.4f).padding(start = 8.dp), singleLine = true, textStyle = Mono)
                    Checkbox(h.secret, { v -> p = p.withHeader(i, h.copy(secret = v)) })
                    IconButton(onClick = { p = p.copy(request = p.request.copy(headers = p.request.headers.filterIndexed { j, _ -> j != i })) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove))
                    }
                }
            }
            TextButton(onClick = { p = p.copy(request = p.request.copy(headers = p.request.headers + Header("", ""))) }) {
                Text(stringResource(R.string.editor_add_header))
            }

            Section(stringResource(R.string.editor_auth))
            val authKind = when (p.auth) { Auth.None -> "none"; is Auth.Bearer -> "bearer"; is Auth.Basic -> "basic"; is Auth.ApiKey -> "apiKey" }
            ChipRow(listOf("none", "bearer", "basic", "apiKey"), authKind) {
                p = p.copy(
                    auth = when (it) {
                        "bearer" -> Auth.Bearer("TOKEN")
                        "basic" -> Auth.Basic("", "PASSWORD")
                        "apiKey" -> Auth.ApiKey("X-API-Key", "API_KEY")
                        else -> Auth.None
                    },
                )
            }
            when (val a = p.auth) {
                is Auth.Bearer -> SecretNameField(a.secret) { p = p.copy(auth = Auth.Bearer(it)) }
                is Auth.Basic -> {
                    OutlinedTextField(a.username, { p = p.copy(auth = a.copy(username = it)) }, label = { Text(stringResource(R.string.editor_username)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    SecretNameField(a.passwordSecret) { p = p.copy(auth = a.copy(passwordSecret = it)) }
                }
                is Auth.ApiKey -> {
                    OutlinedTextField(a.header, { p = p.copy(auth = a.copy(header = it.trim())) }, label = { Text(stringResource(R.string.editor_header_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    SecretNameField(a.secret) { p = p.copy(auth = a.copy(secret = it)) }
                }
                Auth.None -> Unit
            }

            Section(stringResource(R.string.editor_body))
            ChipRow(BodyType.values().map { it.wire }, p.request.body.type.wire) {
                p = p.copy(request = p.request.copy(body = p.request.body.copy(type = BodyType.fromWire(it))))
            }
            when (p.request.body.type) {
                BodyType.JSON, BodyType.RAW -> OutlinedTextField(
                    p.request.body.template, { p = p.copy(request = p.request.copy(body = p.request.body.copy(template = it))) },
                    label = { Text(stringResource(R.string.editor_template)) }, textStyle = Mono, minLines = 4, modifier = Modifier.fillMaxWidth(),
                )
                BodyType.FORM -> {
                    p.request.body.fields.forEachIndexed { i, f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(f.name, { v -> p = p.withField(i, f.copy(name = v)) }, label = { Text(stringResource(R.string.editor_field_name)) }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(f.value, { v -> p = p.withField(i, f.copy(value = v)) }, label = { Text(stringResource(R.string.editor_field_value)) }, modifier = Modifier.weight(1.4f).padding(start = 8.dp), singleLine = true, textStyle = Mono)
                            IconButton(onClick = { p = p.copy(request = p.request.copy(body = p.request.body.copy(fields = p.request.body.fields.filterIndexed { j, _ -> j != i }))) }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove))
                            }
                        }
                    }
                    TextButton(onClick = { p = p.copy(request = p.request.copy(body = p.request.body.copy(fields = p.request.body.fields + FormField("", "")))) }) {
                        Text(stringResource(R.string.editor_add_field))
                    }
                }
                BodyType.NONE -> Unit
            }
            if (p.request.body.type != BodyType.NONE) {
                OutlinedTextField(
                    p.request.body.contentType ?: "", { p = p.copy(request = p.request.copy(body = p.request.body.copy(contentType = it.trim().ifEmpty { null }))) },
                    label = { Text(stringResource(R.string.editor_content_type)) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
            }

            Section(stringResource(R.string.editor_signing))
            SwitchRow(stringResource(R.string.editor_hmac), p.signing.enabled) {
                p = p.copy(signing = p.signing.copy(enabled = it, secret = p.signing.secret ?: "HMAC_KEY"))
            }
            if (p.signing.enabled) SecretNameField(p.signing.secret ?: "") { p = p.copy(signing = p.signing.copy(secret = it)) }

            Section(stringResource(R.string.editor_tag))
            SwitchRow(stringResource(R.string.editor_extended_reads), p.tag.extendedReads) { p = p.copy(tag = p.tag.copy(extendedReads = it)) }
            SwitchRow(stringResource(R.string.editor_require_ndef), p.tag.requireNdef) { p = p.copy(tag = p.tag.copy(requireNdef = it)) }

            Section(stringResource(R.string.editor_after))
            OutlinedTextField(
                p.after.messageField ?: "", { p = p.copy(after = p.after.copy(messageField = it.ifEmpty { null })) },
                label = { Text(stringResource(R.string.editor_message_field)) },
                supportingText = { Text(stringResource(R.string.editor_message_field_help)) },
                textStyle = Mono, modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            OutlinedTextField(
                p.after.successText ?: "", { p = p.copy(after = p.after.copy(successText = it.ifEmpty { null })) },
                label = { Text(stringResource(R.string.editor_success_text)) },
                supportingText = { Text(stringResource(R.string.editor_result_text_help)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            OutlinedTextField(
                p.after.failureText ?: "", { p = p.copy(after = p.after.copy(failureText = it.ifEmpty { null })) },
                label = { Text(stringResource(R.string.editor_failure_text)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            SwitchRow(stringResource(R.string.editor_speak), p.after.speak) { p = p.copy(after = p.after.copy(speak = it)) }
            SwitchRow(stringResource(R.string.editor_keep_bodies), p.after.keepBodies) { p = p.copy(after = p.after.copy(keepBodies = it)) }
            SwitchRow(stringResource(R.string.editor_queue_offline), p.after.queueOffline) { p = p.copy(after = p.after.copy(queueOffline = it)) }
            if (p.after.queueOffline) {
                Text(stringResource(R.string.editor_queue_offline_help), color = TapColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
            SwitchRow(stringResource(R.string.editor_sound), p.after.sound) { p = p.copy(after = p.after.copy(sound = it)) }
            SwitchRow(stringResource(R.string.editor_haptic), p.after.haptic) { p = p.copy(after = p.after.copy(haptic = it)) }

            if (problems.isNotEmpty()) {
                Section(stringResource(R.string.editor_problems))
                problems.forEach { Text(it, color = TapColors.Warn) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Button(enabled = !testing, onClick = {
                    testing = true
                    scope.launch {
                        testOutcome = engine.test(p)
                        testing = false
                    }
                }) { Text(stringResource(if (testing) R.string.editor_testing else R.string.editor_test)) }
                OutlinedButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_TEXT, ProfileCodec.encode(p))
                    context.startActivity(Intent.createChooser(send, null))
                }) { Text(stringResource(R.string.editor_export)) }
                OutlinedButton(onClick = {
                    app.profiles.save(p)
                    if (!com.tappony.android.Shortcuts.pin(context, p)) {
                        android.widget.Toast.makeText(context, R.string.editor_pin_unsupported, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.editor_pin)) }
            }
            Text(stringResource(R.string.editor_test_note), color = TapColors.Muted, style = MaterialTheme.typography.bodySmall)
            testOutcome?.let { o ->
                ResultCard(o)
                o.request?.let { r ->
                    Text(
                        (listOf("${r.method} ${r.url}") + r.headers.map { "${it.first}: ${it.second}" } + listOfNotNull(r.body)).joinToString("\n"),
                        style = Mono, color = TapColors.Muted,
                    )
                }
            }

            TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.editor_delete), color = TapColors.Fail) }
            if (confirmDelete) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text(stringResource(R.string.editor_delete_confirm, p.name)) },
                    confirmButton = {
                        TextButton(onClick = {
                            app.profiles.delete(p.id)
                            confirmDelete = false
                            onDone()
                        }) { Text(stringResource(R.string.editor_delete), color = TapColors.Fail) }
                    },
                    dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
                )
            }
        }
    }
}

private fun Profile.withHeader(i: Int, h: Header) = copy(request = request.copy(headers = request.headers.mapIndexed { j, x -> if (j == i) h else x }))

private fun Profile.withField(i: Int, f: FormField) =
    copy(request = request.copy(body = request.body.copy(fields = request.body.fields.mapIndexed { j, x -> if (j == i) f else x })))

@Composable
private fun validate(p: Profile, secretNames: Set<String>): List<String> {
    val out = ArrayList<String>()
    val tplCheck = HostPolicy.checkTemplate(p.request.url)
    if (tplCheck == "templatedAuthority") out.add(stringResource(R.string.val_templated_host))
    if (tplCheck == "malformed") out.add(stringResource(R.string.err_bad_url))
    val templates = listOf(p.request.url) + p.request.headers.map { it.value } + p.request.body.template +
        p.request.body.fields.flatMap { listOf(it.name, it.value) }
    val unknown = LinkedHashSet<String>()
    var parseError: String? = null
    for (t in templates) {
        try {
            unknown.addAll(Template.referencedVariables(t) - Variables.ALL)
        } catch (e: TemplateException) {
            parseError = e.code
        }
    }
    parseError?.let { out.add(stringResource(R.string.err_template, it)) }
    if (unknown.isNotEmpty()) out.add(stringResource(R.string.val_unknown_variables, unknown.joinToString(", ")))
    val missing = RequestBuilder.requiredSecrets(p) - secretNames
    if (missing.isNotEmpty()) out.add(stringResource(R.string.val_missing_secrets, missing.joinToString(", ")))
    if (p.request.headers.any { it.name.isBlank() }) out.add(stringResource(R.string.err_header_name))
    p.after.messageField?.let { f ->
        if (!f.startsWith("json:") && !f.startsWith("header:")) out.add(stringResource(R.string.val_message_field))
    }
    return out
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = TapColors.BlueLight, modifier = Modifier.padding(top = 8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(o) }) }
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(value, onChange)
    }
}

@Composable
private fun SecretNameField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { onChange(it.filter { c -> c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '_' }) },
        label = { Text(stringResource(R.string.editor_secret_name)) },
        supportingText = { Text(stringResource(R.string.editor_secret_name_help)) },
        modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = Mono,
    )
}
