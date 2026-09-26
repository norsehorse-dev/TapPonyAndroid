package com.tappony.core

/**
 * The profile document, schema version 1. PROFILE_SCHEMA.md section 1.
 *
 * Read and written with the core's own JSON codec rather than a reflection or
 * codegen library, so :core stays dependency-free and the exact on-disk shape is
 * tested against the same fixtures TapPonyKit uses.
 */
data class Profile(
    val schema: Int = SCHEMA_VERSION,
    val id: String,
    val name: String,
    val request: RequestSpec = RequestSpec(),
    val auth: Auth = Auth.None,
    val signing: SigningSpec = SigningSpec(),
    val tag: TagSpec = TagSpec(),
    val after: AfterSpec = AfterSpec(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
        val METHODS = listOf("GET", "POST", "PUT", "PATCH", "DELETE")
    }
}

data class Header(val name: String, val value: String, val secret: Boolean = false)
data class FormField(val name: String, val value: String)

enum class BodyType(val wire: String) {
    JSON("json"), FORM("form"), RAW("raw"), NONE("none");

    companion object {
        fun fromWire(s: String): BodyType = values().firstOrNull { it.wire == s } ?: throw ProfileException("badBodyType")
    }
}

data class BodySpec(
    val type: BodyType = BodyType.JSON,
    val template: String = "",
    val contentType: String? = null,
    val fields: List<FormField> = emptyList(),
)

data class RequestSpec(
    val method: String = "POST",
    val url: String = "",
    val headers: List<Header> = emptyList(),
    val body: BodySpec = BodySpec(),
    val timeoutSeconds: Int = 15,
    val followRedirects: Boolean = false,
    val allowLocalHttp: Boolean = false,
)

sealed class Auth {
    object None : Auth()
    data class Bearer(val secret: String) : Auth()
    data class Basic(val username: String, val passwordSecret: String) : Auth()
    data class ApiKey(val header: String, val secret: String) : Auth()
}

data class SigningSpec(val enabled: Boolean = false, val secret: String? = null)

data class TagSpec(
    val technologies: List<String> = listOf("iso14443", "iso15693", "felica"),
    val extendedReads: Boolean = true,
    val requireNdef: Boolean = false,
)

data class AfterSpec(
    val messageField: String? = null,
    val keepBodies: Boolean = false,
    val sound: Boolean = true,
    val haptic: Boolean = true,
    val queueOffline: Boolean = false,
)

class ProfileException(val code: String) : Exception(code)

object ProfileCodec {

    fun decode(text: String): Profile {
        val root = try { Json.parse(text) } catch (e: Json.ParseException) { throw ProfileException("invalidJson") }
        return fromMap(root as? Map<*, *> ?: throw ProfileException("notAnObject"))
    }

    @Suppress("UNCHECKED_CAST")
    fun fromMap(m: Map<*, *>): Profile {
        val schema = (m["schema"] as? Number)?.toInt() ?: throw ProfileException("missingSchema")
        if (schema > Profile.SCHEMA_VERSION) throw ProfileException("newerSchema")
        val id = m["id"] as? String ?: throw ProfileException("missingId")
        val name = m["name"] as? String ?: throw ProfileException("missingName")
        val r = m["request"] as? Map<*, *> ?: emptyMap<String, Any?>()
        val b = r["body"] as? Map<*, *> ?: emptyMap<String, Any?>()
        val request = RequestSpec(
            method = (r["method"] as? String ?: "POST").also { if (it !in Profile.METHODS) throw ProfileException("badMethod") },
            url = r["url"] as? String ?: "",
            headers = (r["headers"] as? List<*> ?: emptyList<Any?>()).map {
                val h = it as Map<*, *>
                Header(h["name"] as? String ?: "", h["value"] as? String ?: "", h["secret"] as? Boolean ?: false)
            },
            body = BodySpec(
                type = BodyType.fromWire(b["type"] as? String ?: "none"),
                template = b["template"] as? String ?: "",
                contentType = b["contentType"] as? String,
                fields = (b["fields"] as? List<*> ?: emptyList<Any?>()).map {
                    val f = it as Map<*, *>
                    FormField(f["name"] as? String ?: "", f["value"] as? String ?: "")
                },
            ),
            timeoutSeconds = ((r["timeoutSeconds"] as? Number)?.toInt() ?: 15).coerceIn(1, 120),
            followRedirects = r["followRedirects"] as? Boolean ?: false,
            allowLocalHttp = r["allowLocalHttp"] as? Boolean ?: false,
        )
        val a = m["auth"] as? Map<*, *>
        val auth = when (a?.get("type") as? String ?: "none") {
            "none" -> Auth.None
            "bearer" -> Auth.Bearer(a?.get("secret") as? String ?: throw ProfileException("badAuth"))
            "basic" -> Auth.Basic(
                a?.get("username") as? String ?: throw ProfileException("badAuth"),
                a["passwordSecret"] as? String ?: throw ProfileException("badAuth"),
            )
            "apiKey" -> Auth.ApiKey(
                a?.get("header") as? String ?: throw ProfileException("badAuth"),
                a["secret"] as? String ?: throw ProfileException("badAuth"),
            )
            else -> throw ProfileException("badAuth")
        }
        val s = m["signing"] as? Map<*, *>
        val t = m["tag"] as? Map<*, *>
        val af = m["after"] as? Map<*, *>
        return Profile(
            schema = schema,
            id = id,
            name = name,
            request = request,
            auth = auth,
            signing = SigningSpec(s?.get("enabled") as? Boolean ?: false, s?.get("secret") as? String),
            tag = TagSpec(
                technologies = (t?.get("technologies") as? List<*>)?.filterIsInstance<String>() ?: TagSpec().technologies,
                extendedReads = t?.get("extendedReads") as? Boolean ?: true,
                requireNdef = t?.get("requireNdef") as? Boolean ?: false,
            ),
            after = AfterSpec(
                messageField = af?.get("messageField") as? String,
                keepBodies = af?.get("keepBodies") as? Boolean ?: false,
                sound = af?.get("sound") as? Boolean ?: true,
                haptic = af?.get("haptic") as? Boolean ?: true,
                queueOffline = af?.get("queueOffline") as? Boolean ?: false,
            ),
        )
    }

    fun toMap(p: Profile): Map<String, Any?> = linkedMapOf(
        "schema" to p.schema,
        "id" to p.id,
        "name" to p.name,
        "request" to linkedMapOf(
            "method" to p.request.method,
            "url" to p.request.url,
            "headers" to p.request.headers.map { linkedMapOf("name" to it.name, "value" to it.value, "secret" to it.secret) },
            "body" to linkedMapOf(
                "type" to p.request.body.type.wire,
                "template" to p.request.body.template,
                "contentType" to p.request.body.contentType,
                "fields" to p.request.body.fields.map { linkedMapOf("name" to it.name, "value" to it.value) },
            ),
            "timeoutSeconds" to p.request.timeoutSeconds,
            "followRedirects" to p.request.followRedirects,
            "allowLocalHttp" to p.request.allowLocalHttp,
        ),
        "auth" to when (val a = p.auth) {
            Auth.None -> linkedMapOf("type" to "none")
            is Auth.Bearer -> linkedMapOf("type" to "bearer", "secret" to a.secret)
            is Auth.Basic -> linkedMapOf("type" to "basic", "username" to a.username, "passwordSecret" to a.passwordSecret)
            is Auth.ApiKey -> linkedMapOf("type" to "apiKey", "header" to a.header, "secret" to a.secret)
        },
        "signing" to linkedMapOf("enabled" to p.signing.enabled, "secret" to p.signing.secret),
        "tag" to linkedMapOf(
            "technologies" to p.tag.technologies,
            "extendedReads" to p.tag.extendedReads,
            "requireNdef" to p.tag.requireNdef,
        ),
        "after" to linkedMapOf(
            "messageField" to p.after.messageField,
            "keepBodies" to p.after.keepBodies,
            "sound" to p.after.sound,
            "haptic" to p.after.haptic,
            "queueOffline" to p.after.queueOffline,
        ),
    )

    fun encode(p: Profile): String = Json.write(toMap(p))
}
