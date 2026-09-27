package com.tappony.android.nfc

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import com.tappony.core.Tags
import com.tappony.core.Uid
import java.io.IOException

/** What to write. Mirror UID is filled in from the tag itself at write time. */
sealed class WriteJob {
    abstract val lock: Boolean

    data class Url(val url: String, override val lock: Boolean) : WriteJob()
    data class Text(val text: String, override val lock: Boolean) : WriteJob()
    data class MirrorUid(override val lock: Boolean) : WriteJob()
    data class Launch(val token: String, val label: String, val profileId: String?, override val lock: Boolean) : WriteJob()
}

sealed class WriteResult {
    /** [uid] is the tag's canonical UID, empty when it is random. */
    data class Ok(val uid: String, val locked: Boolean) : WriteResult()
    /** [uid] is set when the message was written but locking failed afterwards. */
    data class Failed(val code: String, val uid: String? = null) : WriteResult()
}

/**
 * Writes NDEF to a tag on the reader-mode binder thread. A blank tag that only
 * offers NdefFormatable is formatted with the message in one step. Locking is
 * permanent and only runs when the job asked for it.
 */
object TagWriter {

    fun write(tag: Tag, job: WriteJob): WriteResult {
        val reading = try {
            AndroidTagReader.read(tag, extendedReads = false)
        } catch (e: Exception) {
            return WriteResult.Failed("moved")
        }
        val ids = Uid.variables(reading.family, reading.identifier)
        val uid = if (ids["random_uid"] == "true") "" else ids["uid"] ?: ""
        val record = when (job) {
            is WriteJob.Url -> NdefRecord.createUri(job.url)
            is WriteJob.Text -> NdefRecord.createTextRecord("en", job.text)
            is WriteJob.MirrorUid -> {
                if (uid.isEmpty()) return WriteResult.Failed("randomUid")
                NdefRecord.createTextRecord("en", uid)
            }
            is WriteJob.Launch -> NdefRecord.createUri(Tags.link(job.token))
        }
        val message = NdefMessage(arrayOf(record))
        return try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                try {
                    if (!ndef.isWritable) return WriteResult.Failed("readOnly")
                    if (ndef.maxSize < message.byteArrayLength) return WriteResult.Failed("tooSmall")
                    // Checked before writing: a lock the user asked for must not silently become an unlocked write.
                    if (job.lock && !ndef.canMakeReadOnly()) return WriteResult.Failed("lockUnsupported")
                    ndef.writeNdefMessage(message)
                    if (job.lock && !runCatching { ndef.makeReadOnly() }.getOrDefault(false)) {
                        return WriteResult.Failed("lockFailed", uid)
                    }
                } finally {
                    runCatching { ndef.close() }
                }
                WriteResult.Ok(uid, job.lock)
            } else {
                val fmt = NdefFormatable.get(tag) ?: return WriteResult.Failed("notNdef")
                fmt.connect()
                try {
                    if (job.lock) fmt.formatReadOnly(message) else fmt.format(message)
                } finally {
                    runCatching { fmt.close() }
                }
                WriteResult.Ok(uid, job.lock)
            }
        } catch (e: TagLostException) {
            WriteResult.Failed("moved")
        } catch (e: FormatException) {
            WriteResult.Failed("failed")
        } catch (e: IOException) {
            WriteResult.Failed("moved")
        } catch (e: SecurityException) {
            WriteResult.Failed("moved")
        } catch (e: RuntimeException) {
            // e.g. IllegalStateException from a tech still connected; never crash the reader callback.
            WriteResult.Failed("failed")
        }
    }
}
