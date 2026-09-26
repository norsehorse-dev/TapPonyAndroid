package com.tappony.core

/** Custom success and failure text, PROFILE_SCHEMA.md section 15. */
object ResultText {

    const val CAP = 200

    fun render(template: String?, status: Int?, message: String?, uid: String, profile: String): String? {
        if (template.isNullOrEmpty()) return null
        val vars = mapOf("status" to (status?.toString() ?: ""), "message" to (message ?: ""), "uid" to uid, "profile" to profile)
        val out = try {
            Template.render(template, TemplateContext.RAW, vars)
        } catch (e: TemplateException) {
            template
        }
        return Encoding.capCodePoints(out, CAP)
    }
}
