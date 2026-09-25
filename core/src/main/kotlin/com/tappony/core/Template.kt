package com.tappony.core

import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Where a template sits, which decides how inserted values are escaped. PROFILE_SCHEMA.md section 5. */
enum class TemplateContext(val wire: String) {
    URL("url"), HEADER("header"), FORM("form"), JSON("json"), RAW("raw");

    companion object {
        fun fromWire(s: String): TemplateContext = values().first { it.wire == s }
    }
}

class TemplateException(val code: String) : Exception(code)

/**
 * The TapPony template engine: variables, modifiers, context-aware escaping.
 * No conditionals, no loops. Semantics are defined in PROFILE_SCHEMA.md and
 * pinned by fixtures/template_vectors.json.
 */
object Template {

    private val NAME = Regex("[a-z_]+")
    private val SECRET = Regex("secret:([A-Za-z0-9_]+)")
    private val DIGITS = Regex("[0-9]+")
    private val JSON_NUMBER = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
    private val TIMESTAMP = Regex("(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(\\.\\d{1,9})?Z")
    private val NO_ARG_MODS = setOf("lower", "upper", "trim", "b64", "url", "colon", "rev", "dec", "unix", "raw")

    sealed class Token {
        data class Literal(val text: String) : Token()
        data class Placeholder(val ref: Ref, val modifiers: List<Modifier>) : Token()
    }

    sealed class Ref {
        data class Variable(val name: String) : Ref()
        data class Secret(val name: String) : Ref()
    }

    data class Modifier(val name: String, val args: List<String>)

    /** Tokenize and parse. Throws [TemplateException] on a malformed template. */
    fun parse(template: String): List<Token> {
        val out = ArrayList<Token>()
        val buf = StringBuilder()
        var i = 0
        val n = template.length
        while (i < n) {
            val c = template[i]
            if (c == '{') {
                if (i + 1 < n && template[i + 1] == '{') {
                    buf.append('{'); i += 2; continue
                }
                if (i + 1 < n && (template[i + 1] in 'a'..'z' || template[i + 1] == '_')) {
                    var j = i + 1
                    while (j < n && template[j] != '{' && template[j] != '}') j++
                    if (j >= n || template[j] != '}') throw TemplateException("unterminated")
                    if (buf.isNotEmpty()) { out.add(Token.Literal(buf.toString())); buf.setLength(0) }
                    out.add(parsePlaceholder(template.substring(i + 1, j)))
                    i = j + 1
                    continue
                }
            }
            buf.append(c)
            i++
        }
        if (buf.isNotEmpty()) out.add(Token.Literal(buf.toString()))
        return out
    }

    private fun parsePlaceholder(body: String): Token.Placeholder {
        val parts = body.split("|")
        val head = parts[0]
        val ref = SECRET.matchEntire(head)?.let { Ref.Secret(it.groupValues[1]) }
            ?: if (NAME.matches(head)) Ref.Variable(head) else throw TemplateException("badName")
        val mods = ArrayList<Modifier>()
        for (m in parts.drop(1)) {
            val colon = m.indexOf(':')
            val name = if (colon < 0) m else m.substring(0, colon)
            val rest = if (colon < 0) null else m.substring(colon + 1)
            if (name == "default") {
                if (rest == null) throw TemplateException("badModifierArgs")
                mods.add(Modifier(name, listOf(rest)))
                continue
            }
            val args = rest?.split(":") ?: emptyList()
            when {
                name in NO_ARG_MODS -> if (args.isNotEmpty()) throw TemplateException("badModifierArgs")
                name == "slice" -> if (args.size !in 1..2 || !args.all { DIGITS.matches(it) }) throw TemplateException("badModifierArgs")
                else -> throw TemplateException("unknownModifier")
            }
            mods.add(Modifier(name, args))
        }
        return Token.Placeholder(ref, mods)
    }

    /** Variable names a template refers to, for the editor's unknown-variable check. */
    fun referencedVariables(template: String): Set<String> =
        parse(template).filterIsInstance<Token.Placeholder>().mapNotNull { (it.ref as? Ref.Variable)?.name }.toSet()

    fun referencedSecrets(template: String): Set<String> =
        parse(template).filterIsInstance<Token.Placeholder>().mapNotNull { (it.ref as? Ref.Secret)?.name }.toSet()

    fun render(
        template: String,
        context: TemplateContext,
        variables: Map<String, String>,
        secrets: Map<String, String> = emptyMap(),
    ): String {
        val tokens = parse(template)
        val inString = if (context == TemplateContext.JSON) jsonStringFlags(tokens) else null
        val sb = StringBuilder()
        var pi = 0
        for (t in tokens) {
            when (t) {
                is Token.Literal -> sb.append(if (context == TemplateContext.FORM) Encoding.form(t.text) else t.text)
                is Token.Placeholder -> {
                    var v = when (val r = t.ref) {
                        is Ref.Secret -> secrets[r.name] ?: throw TemplateException("unknownSecret")
                        is Ref.Variable -> variables[r.name] ?: throw TemplateException("unknownVariable")
                    }
                    for (m in t.modifiers) v = apply(v, m)
                    val skip = t.modifiers.any { it.name == "url" || it.name == "raw" }
                    v = when {
                        context == TemplateContext.HEADER -> stripHeader(v)
                        context == TemplateContext.RAW || skip -> v
                        context == TemplateContext.URL -> Encoding.percent(v)
                        context == TemplateContext.FORM -> Encoding.form(v)
                        else -> jsonValue(v, inString!![pi])
                    }
                    pi++
                    sb.append(v)
                }
            }
        }
        val out = sb.toString()
        if (context == TemplateContext.JSON && !Json.isValid(out)) throw TemplateException("invalidJsonBody")
        return out
    }

    fun stripHeader(v: String): String = v.filter { it != '\r' && it != '\n' && it != '\u0000' }

    private fun jsonValue(v: String, inString: Boolean): String = when {
        inString -> Json.escape(v)
        v.isEmpty() -> "null"
        JSON_NUMBER.matches(v) || v == "true" || v == "false" || v == "null" -> v
        else -> "\"" + Json.escape(v) + "\""
    }

    /** For each placeholder, whether it sits inside a JSON string literal of the template text. */
    private fun jsonStringFlags(tokens: List<Token>): BooleanArray {
        val flags = ArrayList<Boolean>()
        var inStr = false
        var esc = false
        for (t in tokens) {
            when (t) {
                is Token.Placeholder -> flags.add(inStr)
                is Token.Literal -> for (ch in t.text) {
                    if (inStr) {
                        when {
                            esc -> esc = false
                            ch == '\\' -> esc = true
                            ch == '"' -> inStr = false
                        }
                    } else if (ch == '"') inStr = true
                }
            }
        }
        return flags.toBooleanArray()
    }

    private fun apply(v: String, m: Modifier): String = when (m.name) {
        "lower" -> Encoding.asciiLower(v)
        "upper" -> Encoding.asciiUpper(v)
        "trim" -> Encoding.trimTemplateWs(v)
        "b64" -> Encoding.base64(v.toByteArray(Charsets.UTF_8))
        "url" -> Encoding.percent(v)
        "colon" -> Encoding.parseLooseHex(v)?.let { Encoding.colon(it) } ?: v
        "rev" -> Encoding.parseLooseHex(v)?.let { Encoding.hexUpper(it.reversedArray()) } ?: v
        "dec" -> Encoding.parseLooseHex(v)?.let { Encoding.decimal(it) } ?: v
        "unix" -> unix(v) ?: v
        "slice" -> Encoding.sliceCodePoints(v, clampInt(m.args[0]), m.args.getOrNull(1)?.let { clampInt(it) })
        "default" -> if (v.isEmpty()) m.args[0] else v
        "raw" -> v
        else -> throw TemplateException("unknownModifier")
    }

    private fun clampInt(s: String): Int = if (s.length > 9) Int.MAX_VALUE else s.toInt()

    private fun unix(v: String): String? {
        val m = TIMESTAMP.matchEntire(v) ?: return null
        val g = m.groupValues
        if (g[1].toInt() < 1) return null
        return try {
            LocalDateTime.of(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt())
                .toEpochSecond(ZoneOffset.UTC).toString()
        } catch (e: DateTimeException) {
            null
        }
    }
}
