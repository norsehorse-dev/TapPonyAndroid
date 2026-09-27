package com.tappony.android.ui

import android.app.Application
import android.nfc.Tag
import androidx.lifecycle.AndroidViewModel
import com.tappony.android.TapPonyApp
import com.tappony.android.nfc.TagWriter
import com.tappony.android.nfc.WriteJob
import com.tappony.android.nfc.WriteResult
import com.tappony.core.TagEntry
import com.tappony.core.Tags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom

sealed class WriteState {
    object Idle : WriteState()
    data class Waiting(val job: WriteJob) : WriteState()
    data class Done(val job: WriteJob, val uid: String, val locked: Boolean) : WriteState()
    data class Failed(val job: WriteJob, val code: String) : WriteState()
}

/** Tag writing: arm a job, and the next tag held to the phone gets it. */
class TagsViewModel(app: Application) : AndroidViewModel(app) {

    private val tp = app as TapPonyApp
    private val random = SecureRandom()

    private val _state = MutableStateFlow<WriteState>(WriteState.Idle)
    val state: StateFlow<WriteState> = _state.asStateFlow()

    /** True while a job waits for a tag; the activity routes tags here instead of scanning. */
    val armed: Boolean get() = _state.value is WriteState.Waiting

    fun newToken(): String {
        val b = ByteArray(16)
        random.nextBytes(b)
        return Tags.token(b)
    }

    fun arm(job: WriteJob) {
        _state.value = WriteState.Waiting(job)
    }

    fun cancel() {
        _state.value = WriteState.Idle
    }

    /** Reader-mode binder thread. */
    fun onTag(tag: Tag) {
        val job = (_state.value as? WriteState.Waiting)?.job ?: return
        val r = TagWriter.write(tag, job)
        // A written launch link must be registered even when the lock after it failed, or its token is unknown.
        val writtenUid = when (r) {
            is WriteResult.Ok -> r.uid
            is WriteResult.Failed -> r.uid
        }
        if (job is WriteJob.Launch && writtenUid != null) register(job, writtenUid)
        _state.value = when (r) {
            is WriteResult.Ok -> WriteState.Done(job, r.uid, r.locked)
            is WriteResult.Failed -> WriteState.Failed(job, r.code)
        }
    }

    private fun register(job: WriteJob.Launch, uid: String) {
        val existing = if (uid.isEmpty()) null else tp.tags.current.value.tags.firstOrNull { it.uid == uid }
        val entry = TagEntry(
            uid = uid,
            label = job.label.ifBlank { existing?.label ?: "" },
            notes = existing?.notes ?: "",
            profile = job.profileId,
            token = job.token,
        )
        tp.tags.upsert(entry, replacing = existing)
    }
}
