package com.tappony.android.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tappony.android.BuildConfig
import com.tappony.android.R

private const val DOCS_URL = "https://tappony.app/docs"
private const val PRIVACY_URL = "https://tappony.app/privacy"
private const val SOURCE_URL = "https://github.com/norsehorse-dev/TapPonyAndroid"
private const val APACHE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
private const val FEEDBACK_EMAIL = "NorseHorse@norsehor.se"

/** The rest of the Pony family; TapPony itself is left out. Names are brands and stay untranslated. */
private val FAMILY = listOf(
    Triple("PGPony", R.string.more_pgpony, "https://pgpony.app"),
    Triple("AgePony", R.string.more_agepony, "https://agepony.com"),
    Triple("QuorumPony", R.string.more_quorumpony, "https://quorumpony.com"),
    Triple("CarrierPony", R.string.more_carrierpony, "https://carrierpony.com"),
    Triple("BurnPony", R.string.more_burnpony, "https://burnpony.app"),
    Triple("VaultPony", R.string.more_vaultpony, "https://vaultpony.app"),
    Triple("PassPony", R.string.more_passpony, "https://passpony.app"),
    Triple("RelayPony", R.string.more_relaypony, "https://relaypony.app"),
    Triple("ScrubPony", R.string.more_scrubpony, "https://scrubpony.app"),
)

/** Open-source libraries in the Android build, all Apache-2.0. */
private val LIBRARIES = listOf(
    "AndroidX (Core, Activity, Lifecycle, Navigation, Room, WorkManager)" to "The Android Open Source Project",
    "Jetpack Compose and Material 3" to "The Android Open Source Project",
    "Kotlin standard library and kotlinx.coroutines" to "JetBrains s.r.o.",
    "OkHttp" to "Square, Inc.",
)

@Composable
fun SettingsSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = TapColors.BlueLight)
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String?, external: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = TapColors.BlueLight, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TapColors.Text)
            subtitle?.let { Text(it, color = TapColors.Muted, style = MaterialTheme.typography.bodySmall) }
        }
        if (external) Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = TapColors.Muted, modifier = Modifier.size(18.dp))
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.settings_no_browser, Toast.LENGTH_SHORT).show()
    }
}

private fun sendFeedback(context: Context) {
    // App and OS version only; nothing that identifies the phone.
    val body = "\n\n\nTapPony ${BuildConfig.VERSION_NAME}, Android ${Build.VERSION.RELEASE}"
    val uri = Uri.parse("mailto:$FEEDBACK_EMAIL?subject=" + Uri.encode("TapPony feedback") + "&body=" + Uri.encode(body))
    try {
        context.startActivity(Intent(Intent.ACTION_SENDTO, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.settings_no_email_app, FEEDBACK_EMAIL), Toast.LENGTH_LONG).show()
    }
}

@Composable
fun SupportSection() {
    val context = LocalContext.current
    SettingsSectionTitle(stringResource(R.string.settings_support))
    LinkRow(Icons.AutoMirrored.Filled.HelpOutline, stringResource(R.string.settings_help), stringResource(R.string.settings_help_sub)) {
        openUrl(context, DOCS_URL)
    }
    LinkRow(Icons.Filled.Email, stringResource(R.string.settings_feedback), FEEDBACK_EMAIL) { sendFeedback(context) }
    LinkRow(Icons.Filled.PrivacyTip, stringResource(R.string.settings_privacy), stringResource(R.string.settings_privacy_sub)) {
        openUrl(context, PRIVACY_URL)
    }
}

@Composable
fun MoreFromNorseHorseSection() {
    val context = LocalContext.current
    SettingsSectionTitle(stringResource(R.string.settings_more_apps))
    FAMILY.forEach { (name, sub, url) ->
        LinkRow(Icons.Filled.Shield, name, stringResource(sub)) { openUrl(context, url) }
    }
    LinkRow(Icons.Filled.Apps, stringResource(R.string.more_family), stringResource(R.string.more_family_sub)) {
        openUrl(context, "https://pony.norsehor.se")
    }
}

@Composable
fun AboutSection() {
    val context = LocalContext.current
    var showLicenses by remember { mutableStateOf(false) }
    SettingsSectionTitle(stringResource(R.string.settings_about))
    Row(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_version), color = TapColors.Text, modifier = Modifier.weight(1f))
        Text(BuildConfig.VERSION_NAME, color = TapColors.Muted)
    }
    Text(stringResource(R.string.settings_about_text), color = TapColors.Muted)
    LinkRow(Icons.Filled.Code, stringResource(R.string.settings_source), stringResource(R.string.settings_source_sub)) {
        openUrl(context, SOURCE_URL)
    }
    LinkRow(Icons.Filled.Description, stringResource(R.string.settings_licenses), null, external = false) { showLicenses = true }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.settings_licenses)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.licenses_app))
                    Text(stringResource(R.string.licenses_libraries), color = TapColors.Muted)
                    LIBRARIES.forEach { (name, holder) ->
                        Column {
                            Text(name, color = TapColors.Text)
                            Text("© $holder · Apache-2.0", color = TapColors.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick = { openUrl(context, APACHE_URL) }) { Text(stringResource(R.string.licenses_read_apache)) }
                }
            },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}
