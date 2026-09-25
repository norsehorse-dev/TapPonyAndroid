package com.tappony.android

import com.tappony.android.data.AppSettings
import com.tappony.android.data.SecretStore
import com.tappony.android.net.SendResult
import com.tappony.android.net.Sender
import com.tappony.core.PreparedRequest
import com.tappony.core.Profile
import com.tappony.core.RequestBuilder
import com.tappony.core.RequestException
import com.tappony.core.SendContext
import com.tappony.core.TagReading
import com.tappony.core.Variables
import java.security.SecureRandom
import java.util.TimeZone

/** What the Scan screen shows after a tag. */
data class ScanOutcome(
    val profileName: String,
    val uid: String,
    val chip: String,
    val tagType: String,
    val randomUid: Boolean,
    val request: PreparedRequest?,
    val result: SendResult?,
    val buildError: String?,
)

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

    fun context(profile: Profile, scanTimeMs: Long, test: Boolean = false) = SendContext(
        scanTimeMs = scanTimeMs,
        sendTimeMs = System.currentTimeMillis(),
        timeZone = TimeZone.getDefault().id,
        profileName = profile.name,
        profileId = profile.id,
        deviceLabel = settings.deviceLabel.value,
        platform = "android",
        nonce = nonce(),
        seq = if (test) 0 else settings.nextSeq(profile.id),
    )

    suspend fun run(profile: Profile, reading: TagReading, scanTimeMs: Long): ScanOutcome {
        val ctx = context(profile, scanTimeMs)
        val vars = Variables.build(reading, ctx)
        return send(profile, vars, ctx)
    }

    /** The editor's Test send: clearly fake sample values, never a real tag. */
    suspend fun test(profile: Profile): ScanOutcome {
        val ctx = context(profile, System.currentTimeMillis(), test = true)
        return send(profile, Variables.sample(ctx), ctx)
    }

    private suspend fun send(profile: Profile, vars: Map<String, String>, ctx: SendContext): ScanOutcome {
        val secretValues = secrets.all()
        val base = ScanOutcome(
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
            RequestBuilder.build(profile, vars, secretValues, ctx.sendTimeMs / 1000)
        } catch (e: RequestException) {
            return base.copy(buildError = e.code)
        }
        val result = sender.send(req, profile.request.allowLocalHttp)
        return base.copy(request = RequestBuilder.masked(req, secretValues), result = result)
    }
}
