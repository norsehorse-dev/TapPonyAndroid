package com.tappony.core

/** A request ready for the platform HTTP client. Body is null when none is sent. */
data class PreparedRequest(
    val method: String,
    val url: String,
    val headers: List<Pair<String, String>>,
    val body: String?,
    val timeoutSeconds: Int,
    val followRedirects: Boolean,
)

class RequestException(val code: String) : Exception(code)

/** Request assembly, PROFILE_SCHEMA.md section 9, pinned by fixtures/request_vectors.json. */
object RequestBuilder {

    private val TOKEN = Regex("[!#$%&'*+\\-.^_`|~0-9A-Za-z]+")
    private val DEFAULT_CONTENT_TYPE = mapOf(
        BodyType.JSON to "application/json",
        BodyType.FORM to "application/x-www-form-urlencoded",
        BodyType.RAW to "text/plain; charset=utf-8",
    )

    fun build(profile: Profile, variables: Map<String, String>, secrets: Map<String, String>, sendUnix: Long): PreparedRequest {
        val req = profile.request
        if (req.method !in Profile.METHODS) throw RequestException("badMethod")
        val tpl = HostPolicy.checkTemplate(req.url)
        if (tpl != "ok") throw RequestException("urlTemplate:$tpl")
        val url = render(req.url, TemplateContext.URL, variables, secrets)
        val policy = HostPolicy.check(url, req.allowLocalHttp)
        if (!policy.allowed) throw RequestException("hostPolicy:${policy.detail}")

        val headers = ArrayList<Pair<String, String>>()
        for (h in req.headers) {
            if (!TOKEN.matches(h.name)) throw RequestException("badHeaderName")
            headers.add(h.name to render(h.value, TemplateContext.HEADER, variables, secrets))
        }
        fun secret(name: String) = secrets[name] ?: throw RequestException("template:unknownSecret")
        when (val a = profile.auth) {
            Auth.None -> Unit
            is Auth.Bearer -> headers.add("Authorization" to "Bearer " + secret(a.secret))
            is Auth.Basic -> headers.add(
                "Authorization" to "Basic " + Encoding.base64((a.username + ":" + secret(a.passwordSecret)).toByteArray(Charsets.UTF_8)),
            )
            is Auth.ApiKey -> {
                if (!TOKEN.matches(a.header)) throw RequestException("badHeaderName")
                headers.add(a.header to secret(a.secret))
            }
        }
        for (i in headers.indices) headers[i] = headers[i].first to Template.stripHeader(headers[i].second)

        val bodySpec = req.body
        var body: String? = null
        if (req.method != "GET" && req.method != "DELETE" && bodySpec.type != BodyType.NONE) {
            body = when (bodySpec.type) {
                BodyType.JSON -> render(bodySpec.template, TemplateContext.JSON, variables, secrets)
                BodyType.RAW -> render(bodySpec.template, TemplateContext.RAW, variables, secrets)
                BodyType.FORM -> bodySpec.fields.joinToString("&") {
                    render(it.name, TemplateContext.FORM, variables, secrets) + "=" + render(it.value, TemplateContext.FORM, variables, secrets)
                }
                BodyType.NONE -> null
            }
            if (headers.none { it.first.equals("content-type", ignoreCase = true) }) {
                headers.add("Content-Type" to (bodySpec.contentType ?: DEFAULT_CONTENT_TYPE.getValue(bodySpec.type)))
            }
        }
        if (profile.signing.enabled) {
            val key = secret(profile.signing.secret ?: throw RequestException("template:unknownSecret"))
            val ts = sendUnix.toString()
            headers.add(Signing.TIMESTAMP_HEADER to ts)
            headers.add(Signing.NONCE_HEADER to (variables["nonce"] ?: ""))
            headers.add(Signing.SIGNATURE_HEADER to Signing.signature(key, ts, body ?: ""))
        }
        return PreparedRequest(req.method, url, headers, body, req.timeoutSeconds, req.followRedirects)
    }

    private fun render(t: String, c: TemplateContext, v: Map<String, String>, s: Map<String, String>): String =
        try {
            Template.render(t, c, v, s)
        } catch (e: TemplateException) {
            throw RequestException("template:${e.code}")
        }

    /** Names of secrets a profile needs, so the editor can show which are missing. */
    fun requiredSecrets(profile: Profile): Set<String> {
        val out = LinkedHashSet<String>()
        fun scan(t: String) = try { out.addAll(Template.referencedSecrets(t)) } catch (_: TemplateException) { false }
        scan(profile.request.url)
        profile.request.headers.forEach { scan(it.value) }
        scan(profile.request.body.template)
        profile.request.body.fields.forEach { scan(it.name); scan(it.value) }
        when (val a = profile.auth) {
            is Auth.Bearer -> out.add(a.secret)
            is Auth.Basic -> out.add(a.passwordSecret)
            is Auth.ApiKey -> out.add(a.secret)
            Auth.None -> Unit
        }
        profile.signing.secret?.takeIf { profile.signing.enabled }?.let { out.add(it) }
        return out
    }

    /** Header values with secrets replaced, for the Test view and history. */
    fun masked(request: PreparedRequest, secrets: Map<String, String>): PreparedRequest {
        val values = secrets.values.filter { it.isNotEmpty() }.sortedByDescending { it.length }
        fun mask(s: String): String {
            var out = s
            for (v in values) out = out.replace(v, MASK)
            for (v in values) out = out.replace(Encoding.percent(v), MASK)
            return out
        }
        val authMasked = request.headers.map { (n, v) ->
            if (n.equals("Authorization", ignoreCase = true)) n to (v.substringBefore(' ') + " " + MASK) else n to mask(v)
        }
        return request.copy(url = mask(request.url), headers = authMasked, body = request.body?.let { mask(it) })
    }

    const val MASK = "••••"
}
