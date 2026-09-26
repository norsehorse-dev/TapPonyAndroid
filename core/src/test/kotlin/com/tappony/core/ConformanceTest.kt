package com.tappony.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Runs the shared conformance fixtures (repo root fixtures/, on the test
 * classpath). TapPonyKit runs the same files; both must stay green.
 */
class ConformanceTest {

    @Suppress("UNCHECKED_CAST")
    private fun fixture(name: String): Map<String, Any?> {
        val stream = javaClass.classLoader!!.getResourceAsStream(name) ?: error("missing fixture $name")
        return Json.parse(stream.readBytes().toString(Charsets.UTF_8)) as Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    private fun strMap(v: Any?): Map<String, String> = (v as Map<String, Any?>).mapValues { it.value as String }

    private fun hex(s: String?): ByteArray? = s?.let { Encoding.parseHex(it) ?: error("bad hex $it") }

    @Test
    fun templates() {
        val f = fixture("template_vectors.json")
        val vars = strMap(f["variables"])
        val secrets = strMap(f["secrets"])
        var n = 0
        for (c in f["cases"] as List<*>) {
            c as Map<*, *>
            val id = c["id"] as String
            val ctx = TemplateContext.fromWire(c["context"] as String)
            val tpl = c["template"] as String
            val expectErr = c["error"] as String?
            try {
                val out = Template.render(tpl, ctx, vars, secrets)
                if (expectErr != null) fail("$id: expected error $expectErr, got '$out'")
                assertEquals(id, c["output"], out)
            } catch (e: TemplateException) {
                assertEquals("$id error", expectErr, e.code)
            }
            n++
        }
        assertTrue(n > 50)
    }

    @Test
    fun hostPolicy() {
        val f = fixture("hostpolicy_vectors.json")
        for (c in f["cases"] as List<*>) {
            c as Map<*, *>
            val r = HostPolicy.check(c["url"] as String, c["allowLocalHttp"] as Boolean)
            val verdict = if (r.allowed) "allowed" else "rejected"
            assertEquals("${c["url"]} verdict", c["verdict"], verdict)
            assertEquals("${c["url"]} detail", c["detail"], r.detail)
        }
        for (c in f["templateCases"] as List<*>) {
            c as Map<*, *>
            assertEquals(c["template"] as String, c["result"], HostPolicy.checkTemplate(c["template"] as String))
        }
    }

    @Test
    fun uids() {
        val f = fixture("uid_vectors.json")
        for (c in f["cases"] as List<*>) {
            c as Map<*, *>
            val got = Uid.variables(TagFamily.fromWire(c["family"] as String), hex(c["raw"] as String)!!)
            assertEquals("${c["family"]} ${c["raw"]}", strMap(c["expect"]), got)
        }
        for (c in f["chips"] as List<*>) {
            c as Map<*, *>
            val b = hex(c["response"] as String)!!
            val got = if (c["kind"] == "getVersion") Uid.chipFromGetVersion(b) else Uid.chipFromDesfireVersion(b)
            assertEquals("${c["kind"]} ${c["response"]}", c["chip"], got)
        }
    }

    @Test
    fun signing() {
        val f = fixture("signing_vectors.json")
        for (c in f["cases"] as List<*>) {
            c as Map<*, *>
            assertEquals(c["signature"], Signing.signature(c["key"] as String, c["timestamp"] as String, c["body"] as String))
        }
    }

    @Test
    fun variables() {
        val f = fixture("variables_vectors.json")
        for (c in f["cases"] as List<*>) {
            c as Map<*, *>
            val r = c["reading"] as Map<*, *>
            val x = c["context"] as Map<*, *>
            val reading = TagReading(
                family = TagFamily.fromWire(r["family"] as String),
                tagType = r["tagType"] as String,
                identifier = hex(r["identifier"] as String)!!,
                chip = r["chip"] as String? ?: "",
                signature = hex(r["signature"] as String?),
                counter = (r["counter"] as Number?)?.toInt(),
                atqa = hex(r["atqa"] as String?),
                sak = hex(r["sak"] as String?),
                dsfid = hex(r["dsfid"] as String?),
                afi = hex(r["afi"] as String?),
                blockSize = (r["blockSize"] as Number?)?.toInt(),
                blockCount = (r["blockCount"] as Number?)?.toInt(),
                pmm = hex(r["pmm"] as String?),
                systemCode = hex(r["systemCode"] as String?),
                historicalBytes = hex(r["historicalBytes"] as String?),
                applicationData = hex(r["applicationData"] as String?),
                ndef = (r["ndef"] as List<*>? ?: emptyList<Any?>()).map {
                    it as Map<*, *>
                    NdefRecord((it["tnf"] as Number).toInt(), hex(it["type"] as String)!!, hex(it["id"] as String? ?: "")!!, hex(it["payload"] as String)!!)
                },
            )
            val ctx = SendContext(
                scanTimeMs = (x["scanTimeMs"] as Number).toLong(),
                sendTimeMs = (x["sendTimeMs"] as Number).toLong(),
                timeZone = x["tz"] as String,
                profileName = x["profileName"] as String,
                profileId = x["profileId"] as String,
                deviceLabel = x["deviceLabel"] as String? ?: "",
                platform = x["platform"] as String,
                nonce = x["nonce"] as String,
                seq = (x["seq"] as Number).toLong(),
                tagLabel = x["tagLabel"] as String? ?: "",
            )
            val got = Variables.build(reading, ctx)
            val expect = strMap(c["expect"])
            assertEquals("${c["id"]} key set", expect.keys, got.keys)
            for ((k, v) in expect) assertEquals("${c["id"]}.$k", v, got[k])
            assertEquals("${c["id"]} ALL", Variables.ALL, got.keys)
        }
    }

    @Test
    fun requests() {
        val f = fixture("request_vectors.json")
        val vars = strMap(f["variables"])
        val secrets = strMap(f["secrets"])
        for (c in f["cases"] as List<*>) {
            c as Map<*, *>
            val id = c["id"] as String
            val profile = ProfileCodec.fromMap(c["profile"] as Map<*, *>)
            val expectErr = c["error"] as String?
            try {
                val got = RequestBuilder.build(profile, vars, secrets, (c["sendUnix"] as Number).toLong())
                if (expectErr != null) fail("$id: expected $expectErr")
                val e = c["expect"] as Map<*, *>
                assertEquals("$id method", e["method"], got.method)
                assertEquals("$id url", e["url"], got.url)
                assertEquals("$id headers", (e["headers"] as List<*>).map { (it as List<*>)[0] to it[1] }, got.headers)
                assertEquals("$id body", e["body"], got.body)
            } catch (ex: RequestException) {
                assertEquals("$id error", expectErr, ex.code)
            }
        }
    }

    @Test
    fun profileRoundTrip() {
        val f = fixture("request_vectors.json")
        for (c in f["cases"] as List<*>) {
            c as Map<*, *>
            val p = ProfileCodec.fromMap(c["profile"] as Map<*, *>)
            val again = ProfileCodec.decode(ProfileCodec.encode(p))
            assertEquals(p, again)
        }
        val withUnknown = "{\"schema\":1,\"id\":\"A\",\"name\":\"n\",\"future\":{\"x\":1},\"request\":{\"method\":\"GET\",\"url\":\"https://e.com\",\"newThing\":true}}"
        assertEquals("GET", ProfileCodec.decode(withUnknown).request.method)
        try {
            ProfileCodec.decode("{\"schema\":2,\"id\":\"A\",\"name\":\"n\"}")
            fail("newer schema accepted")
        } catch (e: ProfileException) {
            assertEquals("newerSchema", e.code)
        }
    }

    @Test
    fun presetsAreValid() {
        val vars = Variables.sample(SendContext(0, 0, "UTC", "p", "id", "", "android", "00", 1))
        for (p in Presets.ALL) {
            val profile = p.create("ID")
            assertEquals(p.key, "ok", HostPolicy.checkTemplate(profile.request.url))
            val secrets = RequestBuilder.requiredSecrets(profile).associateWith { "x" }
            val req = RequestBuilder.build(profile, vars, secrets, 0)
            assertTrue(p.key, req.url.startsWith("http"))
            assertEquals(profile, ProfileCodec.decode(ProfileCodec.encode(profile)))
        }
    }

    @Test
    fun jsonStrictness() {
        for (bad in listOf("", "{", "[1,]", "{\"a\":1,}", "01", "1.", ".5", "NaN", "\"\u0001\"", "{\"a\" 1}", "tru", "[1] [2]", "\"\\x\"")) {
            assertTrue("accepted: $bad", !Json.isValid(bad))
        }
        for (good in listOf("0", "-0.5e+3", " {\"a\":[1,true,null,\"\\u00e9\"]} ", "\"\\ud83d\\udc34\"", "[]", "{}")) {
            assertTrue("rejected: $good", Json.isValid(good))
        }
    }

    /** Same literal as TapPonyKit's testProfileEncodingIsStable: both platforms write identical bytes. */
    @Test
    fun profileEncodingIsStable() {
        val p = Presets.byKey("generic_get")!!.create("ID")
        assertEquals(
            "{\"schema\":1,\"id\":\"ID\",\"name\":\"GET with query\",\"request\":{\"method\":\"GET\",\"url\":\"https://example.com/REPLACE_ME?uid={uid}&t={timestamp}\",\"headers\":[],\"body\":{\"type\":\"none\",\"template\":\"\",\"contentType\":null,\"fields\":[]},\"timeoutSeconds\":15,\"followRedirects\":false,\"allowLocalHttp\":false},\"auth\":{\"type\":\"none\"},\"signing\":{\"enabled\":false,\"secret\":null},\"tag\":{\"technologies\":[\"iso14443\",\"iso15693\",\"felica\"],\"extendedReads\":true,\"requireNdef\":false},\"after\":{\"messageField\":null,\"keepBodies\":false,\"sound\":true,\"haptic\":true,\"queueOffline\":false,\"successText\":null,\"failureText\":null,\"speak\":false}}",
            ProfileCodec.encode(p),
        )
    }

    @Test
    fun responseMessages() {
        val f = fixture("message_vectors.json")
        for (c in f["cases"] as List<*>) {
            c as Map<*, *>
            val headers = (c["headers"] as List<*>).map { (it as List<*>)[0] as String to it[1] as String }
            val got = ResponseMessage.extract(c["field"] as String?, headers, c["body"] as String?)
            assertEquals("${c["field"]}", c["expect"], got)
        }
    }

    @Test
    fun historyCsv() {
        val f = fixture("history_csv_vectors.json")
        val rows = (f["rows"] as List<*>).map {
            it as Map<*, *>
            HistoryRow(
                timeMs = (it["timeMs"] as Number).toLong(),
                profile = it["profile"] as String,
                uid = it["uid"] as String,
                chip = it["chip"] as String,
                tagType = it["tagType"] as String,
                outcome = it["outcome"] as String,
                status = (it["status"] as Number?)?.toInt(),
                latencyMs = (it["latencyMs"] as Number?)?.toLong(),
                error = it["error"] as String? ?: "",
            )
        }
        assertEquals(f["csv"], HistoryCsv.document(rows))
        for (o in f["outcomes"] as List<*>) {
            o as Map<*, *>
            assertEquals(o["outcome"], HistoryCsv.outcome(o["buildError"] as String?, (o["status"] as Number?)?.toInt()))
        }
    }

    @Test
    fun rules() {
        val f = fixture("rules_vectors.json")
        val base = Rules.fromMap(f["ruleset"] as Map<*, *>)
        assertEquals(f["encoded"], Rules.encode(base))
        assertEquals(base, Rules.decode(Rules.encode(base)))
        for (c in f["cases"] as List<*>) {
            c as Map<*, *>
            val set = (c["rules"] as Map<*, *>?)?.let { Rules.fromMap(it) } ?: base
            val got = Rules.route(set, strMap(c["variables"]), c["activeProfileId"] as String?)
            assertEquals("${c["id"]} profiles", c["expectProfiles"], got.profileIds)
            assertEquals("${c["id"]} rule", c["expectRule"], got.ruleId)
        }
        val byId = base.rules.associateBy { it.id }
        for (e in f["errors"] as List<*>) {
            e as Map<*, *>
            assertEquals("${e["id"]}", e["error"], Rules.error(byId.getValue(e["id"] as String)))
        }
        for (e in f["extraErrors"] as List<*>) {
            e as Map<*, *>
            val rule = Rules.fromMap(mapOf("rules" to listOf(e["rule"]))).rules.single()
            assertEquals(e["error"], Rules.error(rule))
        }
    }

    @Test
    fun resultText() {
        val f = fixture("result_text_vectors.json")
        for (c in f["cases"] as List<*>) {
            c as Map<*, *>
            val got = ResultText.render(c["template"] as String?, (c["status"] as Number?)?.toInt(), c["message"] as String?, c["uid"] as String, c["profile"] as String)
            assertEquals("${c["template"]}", c["expect"], got)
        }
    }
}
