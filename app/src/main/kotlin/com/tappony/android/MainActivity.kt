package com.tappony.android

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tappony.android.ui.HistoryScreen
import com.tappony.android.ui.ProfileEditorScreen
import com.tappony.android.ui.ProfilesScreen
import com.tappony.android.ui.RulesScreen
import com.tappony.android.ui.ScanScreen
import com.tappony.android.ui.ScanViewModel
import com.tappony.android.ui.SettingsScreen
import com.tappony.android.ui.TagsScreen
import com.tappony.android.ui.TagsViewModel
import com.tappony.core.NdefRecord
import com.tappony.android.ui.TapPonyTheme

class MainActivity : ComponentActivity() {

    private val scanVm: ScanViewModel by viewModels()
    private val tagsVm: TagsViewModel by viewModels()
    private var nfc: NfcAdapter? = null

    @Volatile
    private var onScanScreen = true

    /** Bumped by a tappony://scan link (shortcut, tile, other apps) to jump to the Scan tab. */
    private val scanRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfc = NfcAdapter.getDefaultAdapter(this)
        scanVm.activeProfile
        // A cold start already lands on the Scan tab; a recreated activity must not re-apply the old link.
        if (savedInstanceState == null) handleLink(intent, jump = false)
        setContent {
            TapPonyTheme {
                val nav = rememberNavController()
                val jump = scanRequests.intValue
                LaunchedEffect(jump) {
                    if (jump > 0) {
                        nav.navigate("scan") {
                            popUpTo(nav.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    }
                }
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route ?: "scan"
                onScanScreen = route == "scan"
                val tabs = listOf(
                    Triple("scan", R.string.tab_scan, Icons.Filled.Contactless),
                    Triple("profiles", R.string.tab_profiles, Icons.Filled.Tune),
                    Triple("tags", R.string.tab_tags, Icons.Filled.Nfc),
                    Triple("history", R.string.tab_history, Icons.Filled.History),
                    Triple("settings", R.string.tab_settings, Icons.Filled.Settings),
                )
                Scaffold(
                    bottomBar = {
                        if (!route.startsWith("editor") && route != "rules") {
                            NavigationBar {
                                tabs.forEach { (r, label, icon) ->
                                    NavigationBarItem(
                                        selected = route == r,
                                        onClick = {
                                            nav.navigate(r) {
                                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(icon, contentDescription = null) },
                                        label = { Text(stringResource(label)) },
                                    )
                                }
                            }
                        }
                    },
                ) { padding ->
                    NavHost(nav, startDestination = "scan", modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
                        composable("scan") {
                            ScanScreen(scanVm, nfcAvailable = nfc != null, nfcEnabled = { nfc?.isEnabled == true })
                        }
                        composable("profiles") {
                            ProfilesScreen(onOpen = { id -> nav.navigate("editor/$id") }, onOpenRules = { nav.navigate("rules") })
                        }
                        composable("editor/{id}") { e ->
                            ProfileEditorScreen(
                                profileId = e.arguments?.getString("id") ?: "",
                                engine = scanVm.engine,
                                onDone = { nav.popBackStack() },
                            )
                        }
                        composable("rules") { RulesScreen(onDone = { nav.popBackStack() }) }
                        composable("tags") { TagsScreen(tagsVm) }
                        composable("history") { HistoryScreen() }
                        composable("settings") { SettingsScreen() }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent, jump = true)
    }

    /**
     * tappony://scan?profile=<id>: select that profile (if it exists) and show the Scan tab.
     * https://tappony.app/t/?k=<token>, from a tag (NDEF discovery) or an App Link:
     * send for a registered token (PROFILE_SCHEMA.md section 16).
     */
    private fun handleLink(intent: Intent?, jump: Boolean) {
        val uri = intent?.data ?: return
        if (uri.scheme == "https" && uri.host == "tappony.app" && (uri.path ?: "").startsWith("/t/") &&
            (intent.action == Intent.ACTION_VIEW || intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED)
        ) {
            // Reopening from Recents replays the original intent; that must not send again.
            if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
                val tagId = if (intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED) intent.getByteArrayExtra(NfcAdapter.EXTRA_ID) else null
                scanVm.onLaunch(uri.toString(), ndefRecords(intent), tagId)
            }
            scanRequests.intValue += 1
            return
        }
        if (intent.action != Intent.ACTION_VIEW || uri.scheme != "tappony" || uri.host != "scan") return
        val app = application as TapPonyApp
        uri.getQueryParameter("profile")?.let { id -> if (app.profiles.get(id) != null) app.settings.setActiveProfile(id) }
        if (jump) scanRequests.intValue += 1
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfc ?: return
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        val extras = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 500) }
        adapter.enableReaderMode(this, { tag ->
            when {
                tagsVm.armed -> tagsVm.onTag(tag)
                onScanScreen -> scanVm.onTag(tag)
            }
        }, flags, extras)
    }

    override fun onPause() {
        super.onPause()
        nfc?.disableReaderMode(this)
    }

    /** The first NDEF message a discovery intent carries, as core records. */
    private fun ndefRecords(intent: Intent): List<NdefRecord> {
        if (intent.action != NfcAdapter.ACTION_NDEF_DISCOVERED) return emptyList()
        val messages = IntentCompat.getParcelableArrayExtra(intent, NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        val first = messages?.firstOrNull() as? NdefMessage ?: return emptyList()
        return first.records.map { NdefRecord(it.tnf.toInt(), it.type ?: ByteArray(0), it.id ?: ByteArray(0), it.payload ?: ByteArray(0)) }
    }
}
