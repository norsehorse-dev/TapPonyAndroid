package com.tappony.android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tappony.android.BuildConfig
import com.tappony.android.R
import com.tappony.android.TapPonyApp
import com.tappony.android.data.SecretStore

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as TapPonyApp
    val label by app.settings.deviceLabel.collectAsState()
    var names by remember { mutableStateOf(app.secrets.names()) }
    var adding by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.settings_device_label), style = MaterialTheme.typography.titleSmall, color = TapColors.BlueLight)
        OutlinedTextField(
            label, { app.settings.setDeviceLabel(it.take(64)) },
            supportingText = { Text(stringResource(R.string.settings_device_label_help)) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )

        Text(stringResource(R.string.settings_secrets), style = MaterialTheme.typography.titleSmall, color = TapColors.BlueLight)
        Text(stringResource(R.string.settings_secrets_help), color = TapColors.Muted, style = MaterialTheme.typography.bodySmall)
        names.forEach { n ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n, style = Mono, modifier = Modifier.weight(1f))
                Text(com.tappony.core.RequestBuilder.MASK, color = TapColors.Muted)
                IconButton(onClick = {
                    app.secrets.remove(n)
                    names = app.secrets.names()
                }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.remove)) }
            }
        }
        TextButton(onClick = { adding = true }) { Text(stringResource(R.string.settings_add_secret)) }

        Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleSmall, color = TapColors.BlueLight)
        Text(stringResource(R.string.settings_about_body, BuildConfig.VERSION_NAME), color = TapColors.Muted)
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL))) }) {
            Text(stringResource(R.string.settings_source))
        }
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(HUB_URL))) }) {
            Text(stringResource(R.string.settings_more_apps))
        }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.settings_add_secret)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        name, { name = it.filter { c -> c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '_' } },
                        label = { Text(stringResource(R.string.editor_secret_name)) }, singleLine = true, textStyle = Mono,
                    )
                    OutlinedTextField(
                        value, { value = it },
                        label = { Text(stringResource(R.string.settings_secret_value)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = SecretStore.NAME_PATTERN.matches(name),
                    onClick = {
                        app.secrets.put(name, value)
                        names = app.secrets.names()
                        adding = false
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private const val SOURCE_URL = "https://github.com/norsehorse-dev/TapPonyAndroid"
private const val HUB_URL = "https://norsehor.se"
