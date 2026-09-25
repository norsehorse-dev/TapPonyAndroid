package com.tappony.core

/**
 * Starting points for common receivers. Each is an ordinary profile the user
 * edits after creating it. Titles and explainers are localized in each app under
 * the keys preset_<key>_title and preset_<key>_explainer; the core only owns the
 * request shape, so both platforms create identical profiles.
 *
 * Placeholders in URLs that the user must replace are written as
 * REPLACE_ME segments: literal text, never template syntax, so a preset can be
 * saved as-is and fails loudly at the receiver instead of rendering nonsense.
 */
object Presets {

    data class Preset(val key: String, val create: (id: String) -> Profile)

    private const val STANDARD_JSON =
        "{\"uid\":\"{uid}\",\"chip\":\"{chip}\",\"tag_type\":\"{tag_type}\",\"payload\":\"{payload}\",\"timestamp\":\"{timestamp}\",\"device\":\"{device_label}\",\"seq\":{seq}}"

    private fun json(id: String, name: String, url: String, template: String = STANDARD_JSON, local: Boolean = false, auth: Auth = Auth.None) =
        Profile(
            id = id,
            name = name,
            request = RequestSpec(method = "POST", url = url, body = BodySpec(BodyType.JSON, template), allowLocalHttp = local),
            auth = auth,
        )

    val ALL: List<Preset> = listOf(
        Preset("ha_webhook") {
            json(it, "Home Assistant webhook", "http://homeassistant.local:8123/api/webhook/REPLACE_ME", local = true)
        },
        Preset("ha_tag_scanned") {
            json(
                it, "Home Assistant tag", "http://homeassistant.local:8123/api/events/tag_scanned",
                template = "{\"tag_id\":\"{uid}\",\"device_id\":\"{device_label|default:tappony}\"}",
                local = true, auth = Auth.Bearer("HA_TOKEN"),
            )
        },
        Preset("node_red") { json(it, "Node-RED", "http://nodered.local:1880/REPLACE_ME", local = true) },
        Preset("n8n") { json(it, "n8n", "https://n8n.example.com/webhook/REPLACE_ME") },
        Preset("zapier") { json(it, "Zapier", "https://hooks.zapier.com/hooks/catch/REPLACE_ME/") },
        Preset("make") { json(it, "Make", "https://hook.eu1.make.com/REPLACE_ME") },
        Preset("ifttt") {
            json(
                it, "IFTTT", "https://maker.ifttt.com/trigger/REPLACE_ME/json/with/key/{secret:IFTTT_KEY}",
            )
        },
        Preset("ntfy") {
            Profile(
                id = it, name = "ntfy",
                request = RequestSpec(
                    method = "POST", url = "https://ntfy.sh/REPLACE_ME",
                    headers = listOf(Header("Title", "TapPony: {tag_label|default:tag scanned}")),
                    body = BodySpec(BodyType.RAW, "{uid} {chip} {payload}"),
                ),
            )
        },
        Preset("discord") {
            json(it, "Discord", "https://discord.com/api/webhooks/REPLACE_ME", template = "{\"content\":\"Tag {uid} scanned: {payload}\"}")
        },
        Preset("slack") {
            json(it, "Slack", "https://hooks.slack.com/services/REPLACE_ME", template = "{\"text\":\"Tag {uid} scanned: {payload}\"}")
        },
        Preset("google_sheets") {
            json(it, "Google Sheets (Apps Script)", "https://script.google.com/macros/s/REPLACE_ME/exec")
        },
        Preset("php_csv") { json(it, "PHP CSV receiver", "https://example.com/tappony.php") },
        Preset("generic_json") { json(it, "JSON POST", "https://example.com/REPLACE_ME") },
        Preset("generic_form") {
            Profile(
                id = it, name = "Form POST",
                request = RequestSpec(
                    method = "POST", url = "https://example.com/REPLACE_ME",
                    body = BodySpec(
                        BodyType.FORM,
                        fields = listOf(FormField("uid", "{uid}"), FormField("payload", "{payload}"), FormField("timestamp", "{timestamp}")),
                    ),
                ),
            )
        },
        Preset("generic_get") {
            Profile(
                id = it, name = "GET with query",
                request = RequestSpec(
                    method = "GET", url = "https://example.com/REPLACE_ME?uid={uid}&t={timestamp}",
                    body = BodySpec(BodyType.NONE),
                ),
            )
        },
    )

    fun byKey(key: String): Preset? = ALL.firstOrNull { it.key == key }
}
