package com.tappony.core

/** One history entry as exported. Bodies are never part of it. */
data class HistoryRow(
    val timeMs: Long,
    val profile: String,
    val uid: String,
    val chip: String,
    val tagType: String,
    val outcome: String,
    val status: Int?,
    val latencyMs: Long?,
    val error: String,
)

/** History CSV export, PROFILE_SCHEMA.md section 12. */
object HistoryCsv {

    const val OK = "ok"
    const val HTTP_ERROR = "http_error"
    const val NETWORK_ERROR = "network_error"
    const val NOT_SENT = "not_sent"

    val HEADER = listOf("time", "profile", "uid", "chip", "tag_type", "outcome", "status", "latency_ms", "error")

    fun outcome(buildError: String?, status: Int?): String = when {
        !buildError.isNullOrEmpty() -> NOT_SENT
        status == null -> NETWORK_ERROR
        status in 200..299 -> OK
        else -> HTTP_ERROR
    }

    fun field(raw: String): String {
        var s = raw
        if (s.isNotEmpty() && s[0] in "=+-@\t\r") s = "'$s"
        val needsQuotes = s.any { it == ',' || it == '"' || it == '\r' || it == '\n' } || s != s.trim(' ')
        return if (needsQuotes) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    fun row(r: HistoryRow): String = listOf(
        Variables.isoUtc(r.timeMs), r.profile, r.uid, r.chip, r.tagType, r.outcome,
        r.status?.toString() ?: "", r.latencyMs?.toString() ?: "", r.error,
    ).joinToString(",") { field(it) }

    fun document(rows: List<HistoryRow>): String =
        (listOf(HEADER.joinToString(",")) + rows.map { row(it) }).joinToString("\r\n") + "\r\n"
}
