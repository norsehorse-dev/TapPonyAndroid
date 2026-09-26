package com.tappony.core

/** Picks the piece of a server reply shown after a scan. PROFILE_SCHEMA.md section 11. */
object ResponseMessage {

    const val CAP = 200
    private val INDEX = Regex("0|[1-9][0-9]*")

    fun extract(field: String?, headers: List<Pair<String, String>>, body: String?): String? {
        if (field.isNullOrEmpty()) return null
        if (field.startsWith("header:")) {
            val name = field.removePrefix("header:")
            return headers.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second?.let { cap(it) }
        }
        if (!field.startsWith("json:")) return null
        val path = field.removePrefix("json:")
        if (body == null) return null
        var cur: Any? = try {
            Json.parse(body)
        } catch (e: Json.ParseException) {
            return null
        }
        if (path.isNotEmpty()) {
            for (seg in path.split(".")) {
                cur = when {
                    cur is Map<*, *> && cur.containsKey(seg) -> cur[seg]
                    cur is List<*> && INDEX.matches(seg) && seg.length < 10 && seg.toInt() < cur.size -> cur[seg.toInt()]
                    else -> return null
                }
            }
        }
        val text = when (val v = cur) {
            null -> return null
            is String -> v
            is Boolean -> if (v) "true" else "false"
            is Long -> v.toString()
            is Double -> if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()
            else -> Json.write(v)
        }
        return cap(text)
    }

    private fun cap(s: String) = Encoding.capCodePoints(s, CAP)
}
