package com.tappony.core

import java.math.BigInteger
import java.util.Base64

/** Byte and text encodings shared by the template engine, UID formats and NDEF. */
object Encoding {

    private const val LOWER = "0123456789abcdef"
    private const val UPPER = "0123456789ABCDEF"

    fun hex2(v: Int): String = "" + LOWER[(v shr 4) and 0xF] + LOWER[v and 0xF]

    fun hexUpper(b: ByteArray): String {
        val sb = StringBuilder(b.size * 2)
        for (x in b) {
            val v = x.toInt() and 0xFF
            sb.append(UPPER[v shr 4]).append(UPPER[v and 0xF])
        }
        return sb.toString()
    }

    fun hexLower(b: ByteArray): String = hexUpper(b).lowercase()

    /** Strict hex (no separators) to bytes, or null. */
    fun parseHex(s: String): ByteArray? {
        if (s.length % 2 != 0) return null
        val out = ByteArray(s.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(s[2 * i], 16)
            val lo = Character.digit(s[2 * i + 1], 16)
            if (hi < 0 || lo < 0 || !isAsciiHex(s[2 * i]) || !isAsciiHex(s[2 * i + 1])) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    /** Hex with optional ':', '-' or ' ' separators, non-empty, even digit count. Null otherwise. */
    fun parseLooseHex(s: String): ByteArray? {
        val t = s.filter { it != ':' && it != '-' && it != ' ' }
        if (t.isEmpty()) return null
        return parseHex(t)
    }

    private fun isAsciiHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    fun colon(b: ByteArray): String = b.joinToString(":") { hexUpper(byteArrayOf(it)) }

    fun decimal(b: ByteArray): String = if (b.isEmpty()) "" else BigInteger(1, b).toString()

    fun base64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)

    private fun isUnreserved(v: Int): Boolean =
        v in 'A'.code..'Z'.code || v in 'a'.code..'z'.code || v in '0'.code..'9'.code ||
            v == '-'.code || v == '.'.code || v == '_'.code || v == '~'.code

    /** Percent-encode everything but RFC 3986 unreserved, over UTF-8 bytes, uppercase hex. */
    fun percent(s: String): String {
        val sb = StringBuilder()
        for (x in s.toByteArray(Charsets.UTF_8)) {
            val v = x.toInt() and 0xFF
            if (isUnreserved(v)) sb.append(v.toChar()) else sb.append('%').append(UPPER[v shr 4]).append(UPPER[v and 0xF])
        }
        return sb.toString()
    }

    /** application/x-www-form-urlencoded. */
    fun form(s: String): String {
        val sb = StringBuilder()
        for (x in s.toByteArray(Charsets.UTF_8)) {
            val v = x.toInt() and 0xFF
            when {
                isUnreserved(v) -> sb.append(v.toChar())
                v == 0x20 -> sb.append('+')
                else -> sb.append('%').append(UPPER[v shr 4]).append(UPPER[v and 0xF])
            }
        }
        return sb.toString()
    }

    fun asciiLower(s: String): String = buildString(s.length) { for (c in s) append(if (c in 'A'..'Z') c + 32 else c) }

    fun asciiUpper(s: String): String = buildString(s.length) { for (c in s) append(if (c in 'a'..'z') c - 32 else c) }

    fun trimTemplateWs(s: String): String = s.trim { it == ' ' || it == '\t' || it == '\r' || it == '\n' }

    /** Substring by Unicode code point indices, clamped. */
    fun sliceCodePoints(s: String, from: Int, to: Int?): String {
        val n = s.codePointCount(0, s.length)
        val a = from.coerceIn(0, n)
        val b = (to ?: n).coerceIn(a, n)
        val start = s.offsetByCodePoints(0, a)
        val end = s.offsetByCodePoints(0, b)
        return s.substring(start, end)
    }

    /** First [max] code points. */
    fun capCodePoints(s: String, max: Int): String {
        if (s.length <= max) return s
        if (s.codePointCount(0, s.length) <= max) return s
        return s.substring(0, s.offsetByCodePoints(0, max))
    }
}
