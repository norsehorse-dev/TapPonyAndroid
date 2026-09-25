package com.tappony.android.ui

import android.app.Application
import android.nfc.Tag
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tappony.android.ScanEngine
import com.tappony.android.ScanOutcome
import com.tappony.android.TapPonyApp
import com.tappony.android.nfc.AndroidTagReader
import com.tappony.core.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ScanState {
    object Idle : ScanState()
    object Sending : ScanState()
    data class Done(val outcome: ScanOutcome) : ScanState()
    data class ReadFailed(val reason: String) : ScanState()
}

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val tp = app as TapPonyApp
    val engine = ScanEngine(tp.settings, tp.secrets, tp.sender)

    val profiles = tp.profiles.profiles

    val activeProfile: StateFlow<Profile?> = combine(tp.profiles.profiles, tp.settings.activeProfileId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private var lastUid: String? = null
    private var lastAt = 0L

    fun select(profile: Profile) = tp.settings.setActiveProfile(profile.id)

    /** Called on the reader-mode binder thread. Reads first, then sends from a coroutine. */
    fun onTag(tag: Tag) {
        val profile = activeProfile.value ?: return
        val scanTime = System.currentTimeMillis()
        val uidKey = tag.id?.joinToString("") { "%02x".format(it) }
        if (uidKey == lastUid && scanTime - lastAt < DEDUPE_MS) return
        lastUid = uidKey
        lastAt = scanTime
        val reading = try {
            AndroidTagReader.read(tag, profile.tag.extendedReads)
        } catch (e: Exception) {
            _state.value = ScanState.ReadFailed(e.javaClass.simpleName)
            return
        }
        if (profile.tag.requireNdef && reading.ndef.isEmpty()) {
            _state.value = ScanState.ReadFailed("noNdef")
            return
        }
        _state.value = ScanState.Sending
        viewModelScope.launch {
            _state.value = ScanState.Done(engine.run(profile, reading, scanTime))
        }
    }

    private companion object {
        const val DEDUPE_MS = 2000L
    }
}
