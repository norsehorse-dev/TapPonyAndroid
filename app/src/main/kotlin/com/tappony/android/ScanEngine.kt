package com.tappony.android

import com.tappony.android.data.AppSettings
import com.tappony.android.data.HistoryEntry
import com.tappony.android.data.HistoryStore
import com.tappony.android.data.SecretStore
import com.tappony.android.net.SendResult
import com.tappony.android.net.Sender
import com.tappony.core.Encoding
import com.tappony.core.HistoryCsv
import com.tappony.core.PreparedRequest
import com.tappony.core.Profile
import com.tappony.core.RequestBuilder
import com.tappony.core.RequestException
import com.tappony.core.ResponseMessage
import com.tappony.core.ResultText
import com.tappony.core.SendContext
import com.tappony.core.TagReading
import com.tappony.core.Variables
import java.security.SecureRandom
import java.util.TimeZone

/** What the Scan screen shows after a tag. */
data class ScanOutcome(
    val scanTimeMs: Long,
    val profileId: String,
    val profileName: String,
    val uid: String,
    val chip: String,
    val tagType: String,
    val randomUid: Boolean,
    val request: PreparedRequest?,
    val result: SendResult?,
    val buildError: String?,
    /** The piece of the reply picked by the profile's messageField, if any. */
    val message: String? = null,
    /** Got no response and went into the offline queue instead of history. */
    val queued: Boolean = false,
    /** The profile's success or failure text, rendered (PROFILE_SCHEMA.md section 15). */
    val resultText: String? = null,
) {
    val outcome: String get() = HistoryCsv.outcome(buildError, result?.status)

    /** Built and sent, but no HTTP response came back (a transport failure, not a bad URL or header). */
    val isNoResponse: Boolean get() = buildError == null && result != null && result.status == null && result.transportFailure

    /** History entry; bodies only when the profile keeps them, and never secrets (the request is already masked). */
    fun toHistory(keepBodies: Boolean) = HistoryEntry(
        timeMs = scanTimeMs,
        profileId = profileId,
        profileName = profileName,
        uid = uid,
        chip = chip,
        tagType = tagType,
        outcome = outcome,
        status = result?.status,
        latencyMs = result?.latencyMs,
        error = buildError ?: result?.error ?: "",
        message = message,
        request = request?.let { "${it.method} ${it.url}" } ?: "",
        requestBody = if (keepBodies) request?.body?.take(HistoryStore.KEPT_BODY_CHARS) else null,
        responseBody = if (keepBodies) result?.responseBody?.take(HistoryStore.KEPT_BODY_CHARS) else null,
    )
}

/**
 * The scan pipeline, identical in shape to the iOS ScanEngine: reading ->
 * variables -> request (core) -> send (platform). The network call never runs
 * inside the NFC read; the read finishes first.
 */
class ScanEngine(
    private val settings: AppSettings,
    private val secrets: SecretStore,
    private val sender: Sender,
) {
    private val random = SecureRandom()

    fun nonce(): String {
        val b = ByteArray(16)
        random.nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    fun context(profile: Profile, scanTimeMs: Long, test: Boolean = false, tagLabel: String = "") = SendContext(
        scanTimeMs = scanTimeMs,
        sendTimeMs = System.currentTimeMillis(),
        timeZone = TimeZone.getDefault().id,
        profileName = profile.name,
        profileId = profile.id,
        deviceLabel = settings.deviceLabel.value,
        platform = "android",
        nonce = nonce(),
        seq = if (test) 0 else settings.nextSeq(profile.id),
        tagLabel = tagLabel,
    )

    suspend fun run(
        profile: Profile,
        reading: TagReading,
        scanTimeMs: Long,
        history: HistoryStore? = null,
        queue: OfflineQueue? = null,
        tagLabel: String = "",
    ): ScanOutcome {
        val ctx = context(profile, scanTimeMs, tagLabel = tagLabel)
        val vars = Variables.build(reading, ctx)
        val outcome = send(profile, vars, ctx.scanTimeMs, ctx.sendTimeMs)
        // The request already went out (or couldn't); a failed local write must not turn into a crash.
        try {
            if (queue != null && profile.after.queueOffline && outcome.isNoResponse) {
                queue.enqueue(profile, scanTimeMs, vars, outcome.result?.error ?: "")
                return outcome.copy(queued = true, resultText = null)
            }
            history?.record(outcome.toHistory(profile.after.keepBodies))
            if (outcome.result?.status != null) queue?.kick()
        } catch (e: android.database.SQLException) {
        }
        return outcome
    }

    /**
     * Sends a queued scan again (PROFILE_SCHEMA.md section 13): the stored
     * variables with {sent_at} set to now, rebuilt against the current profile
     * and secrets, signed with the real send time.
     */
    suspend fun resend(profile: Profile, storedVars: Map<String, String>, scanTimeMs: Long): ScanOutcome {
        val now = System.currentTimeMillis()
        val vars = storedVars + ("sent_at" to Variables.isoUtc(now))
        return send(profile, vars, scanTimeMs, now)
    }

    /** The editor's Test send: clearly fake sample values, never a real tag. */
    suspend fun test(profile: Profile): ScanOutcome {
        val ctx = context(profile, System.currentTimeMillis(), test = true)
        return send(profile, Variables.sample(ctx), ctx.scanTimeMs, ctx.sendTimeMs)
    }

    private suspend fun send(profile: Profile, vars: Map<String, String>, scanTimeMs: Long, sendTimeMs: Long): ScanOutcome {
        val secretValues = secrets.all()
        val base = ScanOutcome(
            scanTimeMs = scanTimeMs,
            profileId = profile.id,
            profileName = profile.name,
            uid = vars["uid"] ?: "",
            chip = vars["chip"] ?: "",
            tagType = vars["tag_type"] ?: "",
            randomUid = vars["random_uid"] == "true",
            request = null,
            result = null,
            buildError = null,
        )
        val req = try {
            RequestBuilder.build(profile, vars, secretValues, sendTimeMs / 1000)
        } catch (e: RequestException) {
            return base.copy(
                buildError = e.code,
                resultText = ResultText.render(profile.after.failureText, null, null, base.uid, profile.name),
            )
        }
        val raw = sender.send(req, profile.request.allowLocalHttp)
        // A server that echoes a secret back must not get it onto the screen or into kept history.
        val secretsByLength = secretValues.values.filter { it.isNotEmpty() }.sortedByDescending { it.length }
        // Literal, then percent- and form-encoded, so a URL or form echo is caught too.
        fun mask(text: String): String {
            var out = text
            for (v in secretsByLength) out = out.replace(v, RequestBuilder.MASK)
            for (v in secretsByLength) out = out.replace(Encoding.percent(v), RequestBuilder.MASK)
            for (v in secretsByLength) out = out.replace(Encoding.form(v), RequestBuilder.MASK)
            return out
        }
        val result = raw.copy(responseBody = raw.responseBody?.let { mask(it) }, error = raw.error?.let { mask(it) })
        val message = ResponseMessage.extract(profile.after.messageField, raw.responseHeaders.map { it.first to mask(it.second) }, result.responseBody)
        val template = if (result.ok) profile.after.successText else profile.after.failureText
        val text = ResultText.render(template, result.status, message, base.uid, profile.name)?.let { mask(it) }
        return base.copy(request = RequestBuilder.masked(req, secretValues), result = result, message = message, resultText = text)
    }
}
