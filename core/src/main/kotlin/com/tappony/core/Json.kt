package com.tappony.core

/**
 * A small strict RFC 8259 JSON reader and writer.
 *
 * The core uses it to validate rendered JSON bodies, to read and write profile
 * documents, and (in tests) to load the conformance fixtures. Objects decode to
 * [LinkedHashMap] so key order is preserved, arrays to [List], integers that fit
 * to [Long], other numbers to [Double], and the literals to Boolean or null.
 */
object Json {

    class ParseException(message: String) : Exception(message)

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.value(0)
        p.skipWs()
        if (p.i != text.length) throw ParseException("trailing content at ${p.i}")
        return v
    }

    fun isValid(text: String): Boolean = try {
        parse(text); true
    } catch (e: ParseException) {
        false
    }

    /** JSON string escaping per PROFILE_SCHEMA.md section 5 (no surrounding quotes). */
    fun escape(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (ch in s) {
            when {
                ch == '"' -> sb.append("\\\"")
                ch == '\\' -> sb.append("\\\\")
                ch == '\b' -> sb.append("\\b")
                ch == '\u000C' -> sb.append("\\f")
                ch == '\n' -> sb.append("\\n")
                ch == '\r' -> sb.append("\\r")
                ch == '\t' -> sb.append("\\t")
                ch.code < 0x20 -> sb.append("\\u00").append(Encoding.hex2(ch.code))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** Compact serialization with insertion-ordered keys. */
    fun write(value: Any?): String {
        val sb = StringBuilder()
        writeTo(sb, value)
        return sb.toString()
    }

    private fun writeTo(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is Boolean -> sb.append(if (v) "true" else "false")
            is Int, is Long -> sb.append(v.toString())
            is Double -> {
                if (v.isNaN() || v.isInfinite()) throw IllegalArgumentException("non-finite number")
                if (v == Math.floor(v) && Math.abs(v) < 1e15) sb.append(v.toLong().toString()) else sb.append(v.toString())
            }
            is String -> sb.append('"').append(escape(v)).append('"')
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(',')
                    first = false
                    sb.append('"').append(escape(k as String)).append("\":")
                    writeTo(sb, value)
                }
                sb.append('}')
            }
            is List<*> -> {
                sb.append('[')
                v.forEachIndexed { idx, item ->
                    if (idx > 0) sb.append(',')
                    writeTo(sb, item)
                }
                sb.append(']')
            }
            else -> throw IllegalArgumentException("unsupported type ${v::class}")
        }
    }

    private class Parser(val s: String) {
        var i = 0

        fun skipWs() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n' || s[i] == '\r')) i++
        }

        fun fail(msg: String): Nothing = throw ParseException("$msg at $i")

        fun value(depth: Int): Any? {
            if (depth > 128) fail("too deep")
            if (i >= s.length) fail("unexpected end")
            return when (s[i]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> num()
            }
        }

        fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) fail("bad literal")
            i += word.length
            return v
        }

        fun obj(depth: Int): Map<String, Any?> {
            i++
            val m = LinkedHashMap<String, Any?>()
            skipWs()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                skipWs()
                if (i >= s.length || s[i] != '"') fail("expected key")
                val k = str()
                skipWs()
                if (i >= s.length || s[i] != ':') fail("expected colon")
                i++
                skipWs()
                m[k] = value(depth + 1)
                skipWs()
                if (i >= s.length) fail("unexpected end")
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return m }
                    else -> fail("expected , or }")
                }
            }
        }

        fun arr(depth: Int): List<Any?> {
            i++
            val l = ArrayList<Any?>()
            skipWs()
            if (i < s.length && s[i] == ']') { i++; return l }
            while (true) {
                skipWs()
                l.add(value(depth + 1))
                skipWs()
                if (i >= s.length) fail("unexpected end")
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; return l }
                    else -> fail("expected , or ]")
                }
            }
        }

        fun str(): String {
            i++
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) fail("unterminated string")
                val c = s[i]
                when {
                    c == '"' -> { i++; return sb.toString() }
                    c == '\\' -> {
                        if (i + 1 >= s.length) fail("bad escape")
                        val e = s[i + 1]
                        i += 2
                        when (e) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("bad unicode escape")
                                val hex = s.substring(i, i + 4)
                                if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) fail("bad unicode escape")
                                sb.append(hex.toInt(16).toChar())
                                i += 4
                            }
                            else -> fail("bad escape")
                        }
                    }
                    c.code < 0x20 -> fail("control character in string")
                    else -> { sb.append(c); i++ }
                }
            }
        }

        fun num(): Any {
            val start = i
            if (i < s.length && s[i] == '-') i++
            if (i >= s.length) fail("bad number")
            if (s[i] == '0') {
                i++
            } else if (s[i] in '1'..'9') {
                while (i < s.length && s[i] in '0'..'9') i++
            } else fail("bad number")
            var isInt = true
            if (i < s.length && s[i] == '.') {
                isInt = false
                i++
                if (i >= s.length || s[i] !in '0'..'9') fail("bad fraction")
                while (i < s.length && s[i] in '0'..'9') i++
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                isInt = false
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                if (i >= s.length || s[i] !in '0'..'9') fail("bad exponent")
                while (i < s.length && s[i] in '0'..'9') i++
            }
            val t = s.substring(start, i)
            if (isInt) t.toLongOrNull()?.let { return it }
            return t.toDouble()
        }
    }
}
