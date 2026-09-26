package com.tappony.android.ui

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import android.nfc.Tag
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tappony.android.OfflineQueue
import com.tappony.android.ScanEngine
import com.tappony.android.ScanOutcome
import com.tappony.android.TapPonyApp
import com.tappony.android.nfc.AndroidTagReader
import com.tappony.core.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class ScanState {
    object Idle : ScanState()
    object Sending : ScanState()
    data class Done(val outcome: ScanOutcome) : ScanState()
    data class ReadFailed(val reason: String) : ScanState()
}

/** Batch mode: tag after tag without leaving the Scan screen, with a running list. */
data class BatchState(
    val on: Boolean = false,
    val results: List<ScanOutcome> = emptyList(),
    val inFlight: Int = 0,
    val skippedRepeats: Int = 0,
    /** Scans finished in this batch; [results] only keeps the latest rows. */
    val done: Int = 0,
    /** Changes with every new batch, so a send from an earlier batch never lands in this one. */
    val gen: Int = 0,
) {
    val count get() = done + inFlight
}

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val tp = app as TapPonyApp
    val engine: ScanEngine = tp.engine
    val queued: Flow<Int> = tp.queue.count

    val profiles = tp.profiles.profiles

    val activeProfile: StateFlow<Profile?> = combine(tp.profiles.profiles, tp.settings.activeProfileId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val _batch = MutableStateFlow(BatchState())
    val batch: StateFlow<BatchState> = _batch.asStateFlow()

    /** Increments on every successful tag read, so the screen can give haptic feedback. */
    private val _reads = MutableStateFlow(0)
    val reads: StateFlow<Int> = _reads.asStateFlow()

    private val _flushing = MutableStateFlow(false)
    val flushing: StateFlow<Boolean> = _flushing.asStateFlow()

    private val tones: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
    } catch (e: RuntimeException) {
        null
    }

    private var lastUid: String? = null
    private var lastAt = 0L
    private val seenInBatch = HashSet<String>()

    fun select(profile: Profile) = tp.settings.setActiveProfile(profile.id)

    fun setBatch(on: Boolean) {
        synchronized(seenInBatch) { seenInBatch.clear() }
        _batch.update { BatchState(on = on, gen = it.gen + 1) }
        _state.value = ScanState.Idle
    }

    fun newBatch() = setBatch(true)

    fun sendQueuedNow() {
        if (_flushing.value) return
        _flushing.value = true
        viewModelScope.launch {
            try {
                if (tp.queue.flush() == OfflineQueue.Flush.STOPPED) tp.queue.kick()
            } catch (e: android.database.SQLException) {
            } finally {
                _flushing.value = false
            }
        }
    }

    /** Called on the reader-mode binder thread. Reads first, then sends from a coroutine. */
    fun onTag(tag: Tag) {
        val profile = activeProfile.value ?: return
        val scanTime = System.currentTimeMillis()
        val uidKey = tag.id?.joinToString("") { "%02x".format(it) } ?: ""
        val batchAtRead = _batch.value
        val batchOn = batchAtRead.on
        val gen = batchAtRead.gen

        if (uidKey == lastUid && scanTime - lastAt < DEDUPE_MS) return
        lastUid = uidKey
        lastAt = scanTime

        val markedSeen = batchOn && tp.settings.oncePerBatch.value
        if (markedSeen) {
            val fresh = synchronized(seenInBatch) { seenInBatch.add(uidKey) }
            if (!fresh) {
                _batch.update { if (it.gen == gen) it.copy(skippedRepeats = it.skippedRepeats + 1) else it }
                return
            }
        }
        // A failed read was never sent, so it must not count as already sent in this batch.
        fun unmark() {
            if (markedSeen) synchronized(seenInBatch) { seenInBatch.remove(uidKey) }
        }

        val reading = try {
            AndroidTagReader.read(tag, profile.tag.extendedReads)
        } catch (e: Exception) {
            unmark()
            _state.value = ScanState.ReadFailed(e.javaClass.simpleName)
            return
        }
        if (profile.tag.requireNdef && reading.ndef.isEmpty()) {
            unmark()
            _state.value = ScanState.ReadFailed("noNdef")
            return
        }
        _reads.update { it + 1 }

        if (batchOn) {
            _batch.update { if (it.gen == gen) it.copy(inFlight = it.inFlight + 1) else it }
            _state.value = ScanState.Idle
        } else {
            _state.value = ScanState.Sending
        }
        viewModelScope.launch {
            val outcome = engine.run(profile, reading, scanTime, tp.history, tp.queue)
            if (profile.after.sound) tone(outcome)
            if (batchOn) {
                _batch.update { b ->
                    if (b.gen != gen) b
                    else b.copy(results = (listOf(outcome) + b.results).take(MAX_BATCH_ROWS), inFlight = maxOf(0, b.inFlight - 1), done = b.done + 1)
                }
            } else {
                _state.value = ScanState.Done(outcome)
            }
        }
    }

    private fun tone(o: ScanOutcome) {
        val ok = o.queued || o.result?.ok == true
        tones?.startTone(if (ok) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK, 150)
    }

    override fun onCleared() {
        tones?.release()
    }

    private companion object {
        const val DEDUPE_MS = 2000L
        const val MAX_BATCH_ROWS = 200
    }
}
