package com.tappony.android.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small per-device settings and the per-profile {seq} counters. */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _deviceLabel = MutableStateFlow(prefs.getString(KEY_LABEL, "") ?: "")
    val deviceLabel: StateFlow<String> = _deviceLabel.asStateFlow()

    private val _activeProfileId = MutableStateFlow(prefs.getString(KEY_ACTIVE, null))
    val activeProfileId: StateFlow<String?> = _activeProfileId.asStateFlow()

    private val _historyDays = MutableStateFlow(prefs.getInt(KEY_HISTORY_DAYS, 30))
    /** Days of history to keep; 0 keeps entries until the 1,000-entry cap. */
    val historyDays: StateFlow<Int> = _historyDays.asStateFlow()

    fun setHistoryDays(v: Int) {
        prefs.edit().putInt(KEY_HISTORY_DAYS, v).apply()
        _historyDays.value = v
    }

    fun setDeviceLabel(v: String) {
        prefs.edit().putString(KEY_LABEL, v).apply()
        _deviceLabel.value = v
    }

    fun setActiveProfile(id: String?) {
        prefs.edit().putString(KEY_ACTIVE, id).apply()
        _activeProfileId.value = id
    }

    /** Monotonic per-profile counter; returns the value to use for this send. */
    @Synchronized
    fun nextSeq(profileId: String): Long {
        val k = "seq_$profileId"
        val next = prefs.getLong(k, 0L) + 1
        prefs.edit().putLong(k, next).apply()
        return next
    }

    private companion object {
        const val KEY_LABEL = "device_label"
        const val KEY_ACTIVE = "active_profile"
        const val KEY_HISTORY_DAYS = "history_days"
    }
}
