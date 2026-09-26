package com.tappony.android

import android.app.Application
import com.tappony.android.data.AppSettings
import com.tappony.android.data.HistoryStore
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
    val sender = Sender()

    override fun onCreate() {
        super.onCreate()
        profiles = ProfileStore(this)
        secrets = SecretStore(this)
        settings = AppSettings(this)
        history = HistoryStore(this, settings)
    }
}
