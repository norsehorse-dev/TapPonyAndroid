package com.tappony.android.net

import com.tappony.core.HostPolicy
import com.tappony.core.PreparedRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Outcome of one send, as shown on the Scan screen and later in History. */
data class SendResult(
    val status: Int?,
    val latencyMs: Long,
    val responseBody: String?,
    val responseHeaders: List<Pair<String, String>>,
    val error: String?,
) {
    val ok get() = status != null && status in 200..299
}

/**
 * Sends a [PreparedRequest] with OkHttp. Redirects are never followed by the
 * client; when a profile opts in, this follows up to five hops itself, same
 * scheme and same host only, each hop re-checked by the host policy.
 */
class Sender {

    private val base = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun send(req: PreparedRequest, allowLocal: Boolean): SendResult = withContext(Dispatchers.IO) {
        val t = req.timeoutSeconds.toLong()
        val client = base.newBuilder()
            .callTimeout(t, TimeUnit.SECONDS)
            .connectTimeout(t, TimeUnit.SECONDS)
            .readTimeout(t, TimeUnit.SECONDS)
            .build()
        val started = System.nanoTime()
        var url = req.url
        try {
            for (hop in 0..MAX_HOPS) {
                val httpUrl = url.toHttpUrlOrNull() ?: return@withContext fail(started, "malformedUrl")
                val body = if (req.method == "GET" || req.method == "DELETE") null
                else (req.body ?: "").toByteArray(Charsets.UTF_8).toRequestBody(null)
                val rb = Request.Builder().url(httpUrl).method(req.method, body)
                for ((n, v) in req.headers) rb.addHeader(n, v)
                var next: String? = null
                val result = client.newCall(rb.build()).execute().use { resp ->
                    val code = resp.code
                    if (req.followRedirects && hop < MAX_HOPS && code in REDIRECTS) {
                        val loc = resp.header("Location")?.let { httpUrl.resolve(it) }
                        if (loc != null && loc.scheme == httpUrl.scheme && loc.host.equals(httpUrl.host, ignoreCase = true) &&
                            HostPolicy.check(loc.toString(), allowLocal).allowed
                        ) {
                            next = loc.toString()
                            return@use null
                        }
                    }
                    val source = resp.body?.source()
                    source?.request(MAX_BODY)
                    val bytes = source?.buffer?.let { buf -> buf.readByteArray(minOf(buf.size, MAX_BODY)) }
                    SendResult(
                        status = code,
                        latencyMs = elapsed(started),
                        responseBody = bytes?.toString(Charsets.UTF_8),
                        responseHeaders = resp.headers.map { it.first to it.second },
                        error = null,
                    )
                }
                if (result != null) return@withContext result
                url = next ?: return@withContext fail(started, "redirect")
            }
            fail(started, "tooManyRedirects")
        } catch (e: IOException) {
            fail(started, e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""))
        } catch (e: IllegalArgumentException) {
            // OkHttp rejects header values outside printable ASCII (for example a non-ASCII device label).
            fail(started, e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""))
        }
    }

    private fun elapsed(started: Long) = (System.nanoTime() - started) / 1_000_000

    private fun fail(started: Long, msg: String) = SendResult(null, elapsed(started), null, emptyList(), msg)

    private companion object {
        const val MAX_BODY = 64L * 1024
        const val MAX_HOPS = 5
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}
