package com.tappony.core

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Everything a scan produced, in platform-neutral form. The reader layer on
 * each platform fills this in; the core never touches NFC APIs.
 */
data class TagReading(
    val family: TagFamily,
    val tagType: String,
    val identifier: ByteArray,
    val chip: String = "",
    val signature: ByteArray? = null,
    val counter: Int? = null,
    val atqa: ByteArray? = null,
    val sak: ByteArray? = null,
    val dsfid: ByteArray? = null,
    val afi: ByteArray? = null,
    val blockSize: Int? = null,
    val blockCount: Int? = null,
    val pmm: ByteArray? = null,
    val systemCode: ByteArray? = null,
    val historicalBytes: ByteArray? = null,
    val applicationData: ByteArray? = null,
    val ndef: List<NdefRecord> = emptyList(),
)

/** The non-tag inputs to one send. Times are epoch milliseconds. */
data class SendContext(
    val scanTimeMs: Long,
    val sendTimeMs: Long,
    val timeZone: String,
    val profileName: String,
    val profileId: String,
    val deviceLabel: String,
    val platform: String,
    val nonce: String,
    val seq: Long,
    val tagLabel: String = "",
)

/** Builds the full variable map for a scan. PROFILE_SCHEMA.md sections 2, 8 and 10. */
object Variables {

    /** Every variable name the engine knows, for the editor's unknown-variable check. */
    val ALL: Set<String> = setOf(
        "uid", "uid_colon", "uid_dec", "uid_rev", "uid_len", "tag_type", "chip", "manufacturer", "signature",
        "counter", "random_uid", "atqa", "sak", "dsfid", "afi", "block_size", "block_count", "idm", "pmm",
        "system_code", "pupi", "historical_bytes", "application_data", "tag_label",
        "payload", "ndef_text", "ndef_uri", "ndef_json", "ndef_raw", "ndef_count", "token",
        "timestamp", "timestamp_local", "unix", "tz", "sent_at", "profile", "profile_id", "device_label",
        "platform", "nonce", "seq",
    )

    private val TOKEN = Regex("^https://tappony\\.app/t/[^?#]*\\?(?:[^#]*&)?k=([A-Za-z0-9_-]+)")
    private val LOCAL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    fun isoUtc(ms: Long): String {
        val secs = Math.floorDiv(ms, 1000L)
        val millis = Math.floorMod(ms, 1000L)
        val dt = Instant.ofEpochSecond(secs).atOffset(ZoneOffset.UTC)
        return LOCAL_FORMAT.format(dt) + "." + millis.toString().padStart(3, '0') + "Z"
    }

    fun isoLocal(ms: Long, zone: String): String {
        val secs = Math.floorDiv(ms, 1000L)
        val millis = Math.floorMod(ms, 1000L)
        val zdt = Instant.ofEpochSecond(secs).atZone(ZoneId.of(zone))
        val total = zdt.offset.totalSeconds / 60
        val sign = if (total >= 0) "+" else "-"
        val abs = Math.abs(total)
        return LOCAL_FORMAT.format(zdt) + "." + millis.toString().padStart(3, '0') + sign +
            (abs / 60).toString().padStart(2, '0') + ":" + (abs % 60).toString().padStart(2, '0')
    }

    fun launchToken(uri: String): String = TOKEN.find(uri)?.groupValues?.get(1) ?: ""

    private fun hex(b: ByteArray?): String = if (b == null) "" else Encoding.hexUpper(b)

    fun build(reading: TagReading, ctx: SendContext): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val ids = Uid.variables(reading.family, reading.identifier)
        out.putAll(ids)
        out["tag_type"] = reading.tagType
        out["chip"] = reading.chip
        out["signature"] = hex(reading.signature)
        out["counter"] = reading.counter?.toString() ?: ""
        out["atqa"] = hex(reading.atqa)
        out["sak"] = hex(reading.sak)
        out["dsfid"] = hex(reading.dsfid)
        out["afi"] = hex(reading.afi)
        out["block_size"] = reading.blockSize?.toString() ?: ""
        out["block_count"] = reading.blockCount?.toString() ?: ""
        out["idm"] = if (reading.family == TagFamily.FELICA) ids.getValue("uid") else ""
        out["pmm"] = hex(reading.pmm)
        out["system_code"] = hex(reading.systemCode)
        out["pupi"] = if (reading.family == TagFamily.ISO7816_B) ids.getValue("uid") else ""
        out["historical_bytes"] = hex(reading.historicalBytes)
        out["application_data"] = hex(reading.applicationData)
        out["tag_label"] = ctx.tagLabel
        val nv = Ndef.variables(reading.ndef)
        out.putAll(nv)
        out["token"] = launchToken(nv.getValue("ndef_uri"))
        out["timestamp"] = isoUtc(ctx.scanTimeMs)
        out["timestamp_local"] = isoLocal(ctx.scanTimeMs, ctx.timeZone)
        out["unix"] = Math.floorDiv(ctx.scanTimeMs, 1000L).toString()
        out["tz"] = ctx.timeZone
        out["sent_at"] = isoUtc(ctx.sendTimeMs)
        out["profile"] = ctx.profileName
        out["profile_id"] = ctx.profileId
        out["device_label"] = ctx.deviceLabel
        out["platform"] = ctx.platform
        out["nonce"] = ctx.nonce
        out["seq"] = ctx.seq.toString()
        return out
    }

    /** Sample values for the editor's Test send, clearly fake. */
    fun sample(ctx: SendContext): Map<String, String> = build(
        TagReading(
            family = TagFamily.MIFARE,
            tagType = "mifare_ultralight",
            identifier = byteArrayOf(0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01),
            chip = "NTAG215",
            ndef = listOf(NdefRecord(1, byteArrayOf('T'.code.toByte()), ByteArray(0), byteArrayOf(0x02, 'e'.code.toByte(), 'n'.code.toByte()) + "TapPony test".toByteArray())),
        ),
        ctx,
    )
}
