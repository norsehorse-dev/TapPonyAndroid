package com.tappony.android

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import com.tappony.android.data.AppSettings
import com.tappony.android.data.HistoryStore
import com.tappony.android.data.RulesStore
import com.tappony.android.data.ProfileStore
import com.tappony.android.data.SecretStore
import com.tappony.android.net.Sender

/** Holds the app-wide stores. No analytics, no crash reporting, nothing that phones home. */
class TapPonyApp : Application() {

    lateinit var profiles: ProfileStore
        private set
    lateinit var secrets: SecretStore
        private set
    lateinit var settings: AppSettings
        private set
    lateinit var history: HistoryStore
        private set
    lateinit var rules: RulesStore
        private set
    val sender = Sender()
    val engine: ScanEngine by lazy { ScanEngine(settings, secrets, sender) }
    val queue: OfflineQueue by lazy { OfflineQueue(this) }

    override fun onCreate() {
        super.onCreate()
        profiles = ProfileStore(this)
        secrets = SecretStore(this)
        settings = AppSettings(this)
        history = HistoryStore(this, settings)
        rules = RulesStore(this)
        queue.kick()
        combine(profiles.profiles, settings.activeProfileId) { list, active -> Shortcuts.sync(this, list, active) }
            .launchIn(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
}
