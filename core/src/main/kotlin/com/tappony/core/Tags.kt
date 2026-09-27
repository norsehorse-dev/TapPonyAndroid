package com.tappony.core

import java.util.Base64

/** One tag the user has named. PROFILE_SCHEMA.md section 16. */
data class TagEntry(
    val uid: String,
    val label: String,
    val notes: String = "",
    val profile: String? = null,
    val token: String = "",
)

data class TagRegistry(val schema: Int = 1, val tags: List<TagEntry> = emptyList())

/** The tags registry and launch links, pinned by fixtures/tags_vectors.json. */
object Tags {

    const val LAUNCH_PREFIX = "https://tappony.app/t/?k="
    private val TOKEN = Regex("^[A-Za-z0-9_-]{16,64}$")

    fun normUid(s: String): String = Encoding.asciiUpper(s.filter { it != ':' && it != '-' && it != ' ' })

    /** Base64url of 16 random bytes, no padding: 22 characters. */
    fun token(raw16: ByteArray): String {
        require(raw16.size == 16)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw16)
    }

    fun link(token: String): String = LAUNCH_PREFIX + token

    fun isToken(s: String): Boolean = TOKEN.matches(s)

    /** Token first (it names the tag even when the UID can't), then the UID unless it's random or empty. */
    fun find(reg: TagRegistry, uid: String, token: String, randomUid: Boolean): TagEntry? {
        if (token.isNotEmpty()) reg.tags.firstOrNull { it.token.isNotEmpty() && it.token == token }?.let { return it }
        val u = normUid(uid)
        if (u.isNotEmpty() && !randomUid) reg.tags.firstOrNull { it.uid.isNotEmpty() && it.uid == u }?.let { return it }
        return null
    }

    /** The registry entry for a scan's variables. */
    fun find(reg: TagRegistry, variables: Map<String, String>): TagEntry? =
        find(reg, variables["uid"] ?: "", variables["token"] ?: "", variables["random_uid"] == "true")

    fun decode(text: String): TagRegistry {
        val root = try { Json.parse(text) } catch (e: Json.ParseException) { throw ProfileException("invalidJson") }
        return fromMap(root as? Map<*, *> ?: throw ProfileException("notAnObject"))
    }

    fun fromMap(m: Map<*, *>): TagRegistry {
        val schema = (m["schema"] as? Number)?.toInt() ?: 1
        if (schema > 1) throw ProfileException("newerSchema")
        return TagRegistry(
            tags = (m["tags"] as? List<*> ?: emptyList<Any?>()).mapNotNull { t ->
                val o = t as? Map<*, *> ?: return@mapNotNull null
                TagEntry(
                    uid = normUid(o["uid"] as? String ?: ""),
                    label = o["label"] as? String ?: "",
                    notes = o["notes"] as? String ?: "",
                    profile = (o["profile"] as? String)?.takeIf { it.isNotEmpty() },
                    token = o["token"] as? String ?: "",
                )
            },
        )
    }

    fun toMap(r: TagRegistry): Map<String, Any?> = linkedMapOf(
        "schema" to 1,
        "tags" to r.tags.map { t ->
            linkedMapOf("uid" to t.uid, "label" to t.label, "notes" to t.notes, "profile" to t.profile, "token" to t.token)
        },
    )

    fun encode(r: TagRegistry): String = Json.write(toMap(r))

    /**
     * The reading for a launch that arrived without a tag read (section 10):
     * no identifier, tag type launch_link, and the delivered NDEF records, or one
     * URI record holding the link when only the URL arrived.
     */
    fun launchReading(link: String, records: List<NdefRecord> = emptyList()): TagReading =
        TagReading(
            family = TagFamily.MIFARE,
            tagType = "launch_link",
            identifier = ByteArray(0),
            ndef = records.ifEmpty { listOf(Ndef.uriRecord(link)) },
        )
}
