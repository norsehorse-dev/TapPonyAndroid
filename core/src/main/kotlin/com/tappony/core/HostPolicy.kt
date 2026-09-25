package com.tappony.core

/**
 * Decides whether a rendered URL may be sent. PROFILE_SCHEMA.md section 7,
 * pinned by fixtures/hostpolicy_vectors.json.
 *
 * On iOS, ATS already blocks plain HTTP to public host names, but
 * NSAllowsLocalNetworking re-opens IP literals of any kind; on Android there is
 * no ATS at all. This validator is therefore the real guard on both platforms.
 */
object HostPolicy {

    enum class Verdict { ALLOWED, REJECTED }

    /** detail is the host class when allowed (publicTls, localTls, localPlain) or the reason when rejected. */
    data class Result(val verdict: Verdict, val detail: String) {
        val allowed get() = verdict == Verdict.ALLOWED
    }

    private val URL = Regex("([A-Za-z][A-Za-z0-9+.-]*)://([^/?#]*)(.*)", RegexOption.DOT_MATCHES_ALL)
    private val PORT = Regex("[0-9]{1,5}")
    private val V4_OCTET = Regex("0|[1-9][0-9]{0,2}")
    private val HOST = Regex("[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*")
    private val NUMERICISH = Regex("[0-9a-fx.]+")
    private val DIGITS_DOTS = Regex("[0-9.]+")

    private fun rejected(reason: String) = Result(Verdict.REJECTED, reason)

    fun check(url: String, allowLocalHttp: Boolean): Result {
        val m = URL.matchEntire(url) ?: return rejected("malformed")
        val scheme = Encoding.asciiLower(m.groupValues[1])
        val authority = m.groupValues[2]
        if (scheme != "http" && scheme != "https") return rejected("scheme")
        if ('@' in authority) return rejected("userinfo")
        val local: Boolean
        if (authority.startsWith("[")) {
            val k = authority.indexOf(']')
            if (k < 0) return rejected("malformed")
            val host = authority.substring(1, k)
            val rest = authority.substring(k + 1)
            if (rest.isNotEmpty()) {
                if (!rest.startsWith(":") || !PORT.matches(rest.substring(1)) || rest.substring(1).toInt() > 65535) return rejected("malformed")
            }
            if ('%' in host) return rejected("malformed")
            val b = parseIPv6(host) ?: return rejected("malformed")
            local = if (isV4Mapped(b)) isLocalV4(b.copyOfRange(12, 16)) else isLocalV6(b)
        } else {
            val colon = authority.indexOf(':')
            var host = if (colon < 0) authority else authority.substring(0, colon)
            if (colon >= 0) {
                val port = authority.substring(colon + 1)
                if (!PORT.matches(port)) return rejected("malformed")
                if (port.toInt() > 65535) return rejected("malformed")
            }
            host = host.lowercase()
            if (host.endsWith(".")) host = host.dropLast(1)
            if (host.isEmpty()) return rejected("malformed")
            val v4 = parseStrictV4(host)
            if (v4 != null) {
                local = isLocalV4(v4)
            } else {
                if (NUMERICISH.matches(host) && (DIGITS_DOTS.matches(host) || host.split(".").any { it.startsWith("0x") })) {
                    return rejected("numericHost")
                }
                if (!HOST.matches(host)) return rejected("malformed")
                local = host.endsWith(".local") || host.endsWith(".home.arpa") || '.' !in host
            }
        }
        if (scheme == "https") return Result(Verdict.ALLOWED, if (local) "localTls" else "publicTls")
        if (!local) return rejected("plainHttpPublic")
        if (!allowLocalHttp) return rejected("localHttpNotEnabled")
        return Result(Verdict.ALLOWED, "localPlain")
    }

    /** Save-time rule: the scheme and authority of a URL template must be literal text. */
    fun checkTemplate(template: String): String {
        val idx = template.indexOf("://")
        if (idx < 0) return "malformed"
        val scheme = template.substring(0, idx)
        val after = template.substring(idx + 3)
        val end = after.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val authority = if (end < 0) after else after.substring(0, end)
        if ('{' in scheme || '{' in authority) return "templatedAuthority"
        return "ok"
    }

    fun parseStrictV4(h: String): ByteArray? {
        val parts = h.split(".")
        if (parts.size != 4) return null
        val out = ByteArray(4)
        for ((i, p) in parts.withIndex()) {
            if (!V4_OCTET.matches(p)) return null
            val v = p.toInt()
            if (v > 255) return null
            out[i] = v.toByte()
        }
        return out
    }

    /** RFC 4291 text forms, including '::' compression and a trailing dotted IPv4. No zone ids. */
    fun parseIPv6(s: String): ByteArray? {
        if (s.isEmpty()) return null
        val dbl = s.indexOf("::")
        if (dbl >= 0 && s.indexOf("::", dbl + 1) >= 0) return null
        fun groups(part: String): List<String>? = if (part.isEmpty()) emptyList() else part.split(":").takeIf { g -> g.none { it.isEmpty() } }
        val head: List<String>
        val tail: List<String>
        if (dbl >= 0) {
            head = groups(s.substring(0, dbl)) ?: return null
            tail = groups(s.substring(dbl + 2)) ?: return null
        } else {
            head = groups(s) ?: return null
            tail = emptyList()
        }
        val all = head + tail
        val words = ArrayList<Int>()
        val headWords = ArrayList<Int>()
        val tailWords = ArrayList<Int>()
        for ((idx, g) in all.withIndex()) {
            val target = if (idx < head.size) headWords else tailWords
            val isLast = idx == all.size - 1
            if (isLast && '.' in g) {
                val v4 = parseStrictV4(g) ?: return null
                target.add(((v4[0].toInt() and 0xFF) shl 8) or (v4[1].toInt() and 0xFF))
                target.add(((v4[2].toInt() and 0xFF) shl 8) or (v4[3].toInt() and 0xFF))
            } else {
                if (g.length > 4 || !g.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
                target.add(g.toInt(16))
            }
        }
        val count = headWords.size + tailWords.size
        if (dbl >= 0) {
            if (count > 7) return null
            words.addAll(headWords)
            repeat(8 - count) { words.add(0) }
            words.addAll(tailWords)
        } else {
            if (count != 8) return null
            words.addAll(headWords)
        }
        val out = ByteArray(16)
        for (i in 0 until 8) {
            out[2 * i] = (words[i] shr 8).toByte()
            out[2 * i + 1] = words[i].toByte()
        }
        return out
    }

    private fun isV4Mapped(b: ByteArray): Boolean =
        (0 until 10).all { b[it].toInt() == 0 } && (b[10].toInt() and 0xFF) == 0xFF && (b[11].toInt() and 0xFF) == 0xFF

    fun isLocalV4(b: ByteArray): Boolean {
        val a = b[0].toInt() and 0xFF
        val c = b[1].toInt() and 0xFF
        return a == 10 || (a == 172 && c in 16..31) || (a == 192 && c == 168) || (a == 169 && c == 254) || a == 127
    }

    fun isLocalV6(b: ByteArray): Boolean {
        val loopback = (0 until 15).all { b[it].toInt() == 0 } && b[15].toInt() == 1
        val b0 = b[0].toInt() and 0xFF
        val b1 = b[1].toInt() and 0xFF
        val linkLocal = b0 == 0xFE && (b1 and 0xC0) == 0x80
        val ula = (b0 and 0xFE) == 0xFC
        return loopback || linkLocal || ula
    }
}
