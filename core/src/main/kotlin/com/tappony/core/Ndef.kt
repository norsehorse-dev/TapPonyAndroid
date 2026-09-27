package com.tappony.core

/** One NDEF record as both platforms hand it to the core. */
class NdefRecord(val tnf: Int, val type: ByteArray, val id: ByteArray, val payload: ByteArray)

/** NDEF decoding and re-encoding for the content variables. PROFILE_SCHEMA.md section 10. */
object Ndef {

    const val CONTENT_CAP = 8192

    private val URI_PREFIXES = listOf(
        "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:",
        "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://", "nfs://", "ftp://",
        "dav://", "news:", "telnet://", "imap:", "rtsp://", "urn:", "pop:", "sip:", "sips:", "tftp:",
        "btspp://", "btl2cap://", "btgoep://", "tcpobex://", "irdaobex://", "file://", "urn:epc:id:",
        "urn:epc:tag:", "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:",
    )

    /** A well-known URI record, with the longest matching NFC Forum prefix code. */
    fun uriRecord(uri: String): NdefRecord {
        var code = 0
        for (i in 1 until URI_PREFIXES.size) {
            if (uri.startsWith(URI_PREFIXES[i]) && URI_PREFIXES[i].length > URI_PREFIXES[code].length) code = i
        }
        val rest = uri.substring(URI_PREFIXES[code].length).toByteArray(Charsets.UTF_8)
        return NdefRecord(1, byteArrayOf(0x55), ByteArray(0), byteArrayOf(code.toByte()) + rest)
    }

    /** NFC Forum NDEF 1.0 message encoding. */
    fun encode(records: List<NdefRecord>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        records.forEachIndexed { i, r ->
            val sr = r.payload.size < 256
            val il = r.id.isNotEmpty()
            var h = r.tnf and 0x07
            if (i == 0) h = h or 0x80
            if (i == records.size - 1) h = h or 0x40
            if (sr) h = h or 0x10
            if (il) h = h or 0x08
            out.write(h)
            out.write(r.type.size)
            if (sr) out.write(r.payload.size) else {
                val n = r.payload.size
                out.write(n ushr 24); out.write(n ushr 16); out.write(n ushr 8); out.write(n)
            }
            if (il) out.write(r.id.size)
            out.write(r.type); out.write(r.id); out.write(r.payload)
        }
        return out.toByteArray()
    }

    fun textOf(r: NdefRecord): String? {
        if (r.tnf != 1 || !r.type.contentEquals(byteArrayOf('T'.code.toByte())) || r.payload.isEmpty()) return null
        val status = r.payload[0].toInt() and 0xFF
        val langLen = status and 0x3F
        val start = minOf(1 + langLen, r.payload.size)
        val body = r.payload.copyOfRange(start, r.payload.size)
        if (status and 0x80 != 0) {
            if (body.size >= 2 && (body[0].toInt() and 0xFF) == 0xFF && (body[1].toInt() and 0xFF) == 0xFE)
                return String(body, 2, body.size - 2, Charsets.UTF_16LE)
            if (body.size >= 2 && (body[0].toInt() and 0xFF) == 0xFE && (body[1].toInt() and 0xFF) == 0xFF)
                return String(body, 2, body.size - 2, Charsets.UTF_16BE)
            return String(body, Charsets.UTF_16BE)
        }
        return String(body, Charsets.UTF_8)
    }

    fun uriOf(r: NdefRecord): String? {
        if (r.tnf == 1 && r.type.contentEquals(byteArrayOf('U'.code.toByte())) && r.payload.isNotEmpty()) {
            val code = r.payload[0].toInt() and 0xFF
            val prefix = URI_PREFIXES.getOrElse(code) { "" }
            return prefix + String(r.payload, 1, r.payload.size - 1, Charsets.UTF_8)
        }
        if (r.tnf == 3) return String(r.type, Charsets.UTF_8)
        return null
    }

    /** payload, ndef_text, ndef_uri, ndef_json, ndef_raw, ndef_count. */
    fun variables(records: List<NdefRecord>): Map<String, String> {
        if (records.isEmpty()) return linkedMapOf(
            "payload" to "", "ndef_text" to "", "ndef_uri" to "", "ndef_json" to "[]", "ndef_raw" to "", "ndef_count" to "0",
        )
        val first = records[0]
        val payload = textOf(first) ?: uriOf(first) ?: Encoding.base64(first.payload)
        val text = records.firstNotNullOfOrNull { textOf(it) } ?: ""
        val uri = records.firstNotNullOfOrNull { uriOf(it) } ?: ""
        val json = StringBuilder("[")
        records.forEachIndexed { i, r ->
            if (i > 0) json.append(',')
            json.append("{\"tnf\":").append(r.tnf)
            json.append(",\"type\":\"").append(Json.escape(String(r.type, Charsets.ISO_8859_1))).append('"')
            json.append(",\"id\":\"").append(Encoding.base64(r.id)).append('"')
            json.append(",\"payload\":\"").append(Encoding.base64(r.payload)).append('"')
            textOf(r)?.let { json.append(",\"text\":\"").append(Json.escape(it)).append('"') }
            uriOf(r)?.let { json.append(",\"uri\":\"").append(Json.escape(it)).append('"') }
            json.append('}')
        }
        json.append(']')
        fun cap(s: String) = Encoding.capCodePoints(s, CONTENT_CAP)
        return linkedMapOf(
            "payload" to cap(payload),
            "ndef_text" to cap(text),
            "ndef_uri" to cap(uri),
            "ndef_json" to cap(json.toString()),
            "ndef_raw" to cap(Encoding.base64(encode(records))),
            "ndef_count" to records.size.toString(),
        )
    }
}
