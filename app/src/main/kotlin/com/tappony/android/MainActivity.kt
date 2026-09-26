package com.tappony.android

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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tappony.android.ui.HistoryScreen
import com.tappony.android.ui.ProfileEditorScreen
import com.tappony.android.ui.ProfilesScreen
import com.tappony.android.ui.ScanScreen
import com.tappony.android.ui.ScanViewModel
import com.tappony.android.ui.SettingsScreen
import com.tappony.android.ui.TapPonyTheme

class MainActivity : ComponentActivity() {

    private val scanVm: ScanViewModel by viewModels()
    private var nfc: NfcAdapter? = null

    @Volatile
    private var onScanScreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfc = NfcAdapter.getDefaultAdapter(this)
        scanVm.activeProfile
        setContent {
            TapPonyTheme {
                val nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route ?: "scan"
                onScanScreen = route == "scan"
                val tabs = listOf(
                    Triple("scan", R.string.tab_scan, Icons.Filled.Contactless),
                    Triple("profiles", R.string.tab_profiles, Icons.Filled.Tune),
                    Triple("history", R.string.tab_history, Icons.Filled.History),
                    Triple("settings", R.string.tab_settings, Icons.Filled.Settings),
                )
                Scaffold(
                    bottomBar = {
                        if (!route.startsWith("editor")) {
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
                            ProfilesScreen(onOpen = { id -> nav.navigate("editor/$id") })
                        }
                        composable("editor/{id}") { e ->
                            ProfileEditorScreen(
                                profileId = e.arguments?.getString("id") ?: "",
                                engine = scanVm.engine,
                                onDone = { nav.popBackStack() },
                            )
                        }
                        composable("history") { HistoryScreen() }
                        composable("settings") { SettingsScreen() }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfc ?: return
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        val extras = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 500) }
        adapter.enableReaderMode(this, { tag -> if (onScanScreen) scanVm.onTag(tag) }, flags, extras)
    }

    override fun onPause() {
        super.onPause()
        nfc?.disableReaderMode(this)
    }
}
