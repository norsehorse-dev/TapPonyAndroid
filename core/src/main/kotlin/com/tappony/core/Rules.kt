package com.tappony.core

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

data class RuleMatch(val field: String, val op: String, val value: String)

data class Rule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val match: RuleMatch,
    val profiles: List<String>,
)

data class RuleSet(
    val schema: Int = 1,
    val enabled: Boolean = false,
    val unmatched: String = UNMATCHED_ACTIVE,
    val rules: List<Rule> = emptyList(),
) {
    companion object {
        const val UNMATCHED_ACTIVE = "active"
        const val UNMATCHED_IGNORE = "ignore"
    }
}

/** Where a scan goes. [ruleId] null means no rule decided (rules off, or nothing matched). */
data class Route(val profileIds: List<String>, val ruleId: String?)

/** The rules engine, PROFILE_SCHEMA.md section 14, pinned by fixtures/rules_vectors.json. */
object Rules {

    val FIELDS = listOf("uid", "tag_type", "chip", "manufacturer", "payload", "ndef_text", "ndef_uri")
    val OPS = listOf("equals", "prefix", "contains", "regex")

    /** Null when valid, else unknownField, unknownOp, badRegex or noProfiles. */
    fun error(rule: Rule): String? {
        if (rule.match.field !in FIELDS) return "unknownField"
        if (rule.match.op !in OPS) return "unknownOp"
        if (rule.match.op == "regex" && compile(rule.match.value) == null) return "badRegex"
        if (rule.profiles.isEmpty()) return "noProfiles"
        return null
    }

    private fun compile(p: String): Pattern? = try {
        Pattern.compile(p, Pattern.UNIX_LINES)
    } catch (e: PatternSyntaxException) {
        null
    }

    private fun normUid(s: String) = Encoding.asciiUpper(s.filter { it != ':' && it != '-' && it != ' ' })

    fun matches(rule: Rule, variables: Map<String, String>): Boolean {
        if (!rule.enabled || error(rule) != null) return false
        val m = rule.match
        val have = variables[m.field] ?: ""
        if (m.op == "regex") return compile(m.value)?.matcher(have)?.find() == true
        val (h, w) = if (m.field == "uid") normUid(have) to normUid(m.value) else Encoding.asciiLower(have) to Encoding.asciiLower(m.value)
        return when (m.op) {
            "equals" -> h == w
            "prefix" -> h.startsWith(w)
            else -> h.contains(w)
        }
    }

    fun route(set: RuleSet, variables: Map<String, String>, activeProfileId: String?): Route {
        val fallback = listOfNotNull(activeProfileId)
        if (!set.enabled) return Route(fallback, null)
        for (r in set.rules) {
            if (matches(r, variables)) return Route(r.profiles.distinct(), r.id)
        }
        return if (set.unmatched == RuleSet.UNMATCHED_IGNORE) Route(emptyList(), null) else Route(fallback, null)
    }

    // ---- codec -------------------------------------------------------------

    fun decode(text: String): RuleSet {
        val root = try { Json.parse(text) } catch (e: Json.ParseException) { throw ProfileException("invalidJson") }
        return fromMap(root as? Map<*, *> ?: throw ProfileException("notAnObject"))
    }

    fun fromMap(m: Map<*, *>): RuleSet {
        val schema = (m["schema"] as? Number)?.toInt() ?: 1
        if (schema > 1) throw ProfileException("newerSchema")
        return RuleSet(
            schema = schema,
            enabled = m["enabled"] as? Boolean ?: false,
            unmatched = m["unmatched"] as? String ?: RuleSet.UNMATCHED_ACTIVE,
            rules = (m["rules"] as? List<*> ?: emptyList<Any?>()).mapNotNull { r ->
                val o = r as? Map<*, *> ?: return@mapNotNull null
                val mm = o["match"] as? Map<*, *> ?: emptyMap<String, Any?>()
                Rule(
                    id = o["id"] as? String ?: return@mapNotNull null,
                    name = o["name"] as? String ?: "",
                    enabled = o["enabled"] as? Boolean ?: true,
                    match = RuleMatch(mm["field"] as? String ?: "", mm["op"] as? String ?: "", mm["value"] as? String ?: ""),
                    profiles = (o["profiles"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                )
            },
        )
    }

    fun toMap(s: RuleSet): Map<String, Any?> = linkedMapOf(
        "schema" to s.schema,
        "enabled" to s.enabled,
        "unmatched" to s.unmatched,
        "rules" to s.rules.map { r ->
            linkedMapOf(
                "id" to r.id,
                "name" to r.name,
                "enabled" to r.enabled,
                "match" to linkedMapOf("field" to r.match.field, "op" to r.match.op, "value" to r.match.value),
                "profiles" to r.profiles,
            )
        },
    )

    fun encode(s: RuleSet): String = Json.write(toMap(s))
}
