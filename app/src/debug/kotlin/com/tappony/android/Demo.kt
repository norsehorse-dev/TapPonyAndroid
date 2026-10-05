package com.tappony.android

import android.content.Intent
import com.tappony.android.data.HistoryEntry
import com.tappony.android.i18n.LanguageManager
import com.tappony.android.i18n.SupportedLanguage
import com.tappony.android.net.SendResult
import com.tappony.android.ui.ScanViewModel
import com.tappony.core.AfterSpec
import com.tappony.core.Auth
import com.tappony.core.BodySpec
import com.tappony.core.BodyType
import com.tappony.core.HistoryCsv
import com.tappony.core.Profile
import com.tappony.core.RequestSpec
import com.tappony.core.Rule
import com.tappony.core.RuleMatch
import com.tappony.core.RuleSet
import com.tappony.core.TagEntry
import com.tappony.core.TagRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Store screenshots, debug builds only (tools/play-screenshots.sh drives it over adb):
 *
 *   am start -n com.tappony.android.debug/com.tappony.android.MainActivity --es demo_lang de --es demo_screen scan
 *
 * demo_lang switches the in-app language and seeds a small warehouse setup in that language
 * (profiles, rules, named tags, history, and a finished scan on the Scan screen). demo_screen
 * picks the screen. No NFC and no network are involved; hosts are reserved example domains.
 * Same data as the iOS DemoSeed.
 */
object Demo {
    @Volatile
    var active = false
        private set

    private class Texts(
        val warehouse: String, val door: String, val rounds: String, val dock: String, val shelf: String,
        val frontDoor: String, val checkpoint: String, val dockRule: String, val result: String, val message: String,
    )

    private val texts = mapOf(
        "en" to Texts("Warehouse check-in", "Front door log", "Security rounds", "Loading dock A", "Shelf 12",
            "Front door", "Checkpoint 4", "Dock and shelves", "Shelf 12 checked in", "4 of 9 shelves done"),
        "de" to Texts("Lager-Check-in", "Haustür-Protokoll", "Kontrollgang", "Laderampe A", "Regal 12",
            "Haustür", "Kontrollpunkt 4", "Rampe und Regale", "Regal 12 eingecheckt", "4 von 9 Regalen erledigt"),
        "es" to Texts("Registro de almacén", "Registro de la puerta", "Rondas de seguridad", "Muelle de carga A", "Estante 12",
            "Puerta principal", "Punto de control 4", "Muelle y estantes", "Estante 12 registrado", "4 de 9 estantes listos"),
        "fr" to Texts("Pointage entrepôt", "Journal porte d’entrée", "Rondes de sécurité", "Quai de chargement A", "Étagère 12",
            "Porte d’entrée", "Point de contrôle 4", "Quai et étagères", "Étagère 12 pointée", "4 étagères sur 9 faites"),
        "pt-BR" to Texts("Check-in do depósito", "Registro da porta", "Rondas de segurança", "Doca de carga A", "Prateleira 12",
            "Porta da frente", "Ponto de controle 4", "Doca e prateleiras", "Prateleira 12 registrada", "4 de 9 prateleiras feitas"),
        "ru" to Texts("Отметка на складе", "Журнал входной двери", "Обход охраны", "Погрузочная рампа A", "Стеллаж 12",
            "Входная дверь", "Контрольная точка 4", "Рампа и стеллажи", "Стеллаж 12 отмечен", "Готово 4 из 9 стеллажей"),
        "zh-Hans" to Texts("仓库签到", "前门记录", "安保巡查", "装卸区 A", "12 号货架",
            "前门", "4 号检查点", "装卸区和货架", "12 号货架已签到", "已完成 4/9 个货架"),
    )

    /** Returns the navigation route to show, or null when the intent isn't a demo request. */
    fun handle(app: TapPonyApp, intent: Intent?, scanVm: ScanViewModel): String? {
        val screen = intent?.getStringExtra("demo_screen") ?: return null
        active = true
        intent.getStringExtra("demo_lang")?.let { tag ->
            val lang = SupportedLanguage.resolve(tag) ?: SupportedLanguage.EN
            seed(app, lang.tag, scanVm)
            if (LanguageManager.current(app) != lang) LanguageManager.set(lang)
            intent.removeExtra("demo_lang")
        }
        return when (screen) {
            "profile" -> "editor/$WAREHOUSE"
            "rules", "tags", "history", "settings", "profiles" -> screen
            else -> "scan"
        }
    }

    private const val WAREHOUSE = "DEMO-WAREHOUSE"

    private fun seed(app: TapPonyApp, langTag: String, scanVm: ScanViewModel) {
        val t = texts[langTag] ?: texts.getValue("en")
        app.profiles.profiles.value.forEach { app.profiles.delete(it.id) }
        app.secrets.put("INVENTORY_TOKEN", "demo-token")

        val warehouse = Profile(
            id = WAREHOUSE, name = t.warehouse,
            request = RequestSpec(
                method = "POST", url = "https://inventory.example.com/api/scan",
                body = BodySpec(BodyType.JSON, "{\n  \"tag\": \"{uid}\",\n  \"name\": \"{tag_label}\",\n  \"time\": \"{timestamp}\"\n}"),
            ),
            auth = Auth.Bearer("INVENTORY_TOKEN"),
            after = AfterSpec(messageField = "json:message", successText = "{message}"),
        )
        val door = Profile(
            id = "DEMO-DOOR", name = t.door,
            request = RequestSpec(method = "POST", url = "https://home.example.net/hooks/front-door",
                body = BodySpec(BodyType.JSON, "{\"uid\":\"{uid}\",\"at\":\"{timestamp}\"}")),
        )
        val rounds = Profile(
            id = "DEMO-ROUNDS", name = t.rounds,
            request = RequestSpec(method = "GET", url = "https://rounds.example.org/checkpoint?tag={uid}", body = BodySpec(BodyType.NONE)),
        )
        listOf(warehouse, door, rounds).forEach { app.profiles.save(it) }
        app.settings.setActiveProfile(warehouse.id)

        app.tags.save(TagRegistry(tags = listOf(
            TagEntry(uid = "04A27F1B5E8000", label = t.shelf, profile = warehouse.id),
            TagEntry(uid = "04A2113C6A2280", label = t.dock, profile = warehouse.id),
            TagEntry(uid = "04B91C2A7F6D80", label = t.frontDoor, profile = door.id),
            TagEntry(uid = "E004015012345678", label = t.checkpoint, profile = rounds.id),
        )))
        app.rules.save(RuleSet(enabled = true, unmatched = RuleSet.UNMATCHED_ACTIVE, rules = listOf(
            Rule(id = "DEMO-RULE-1", name = t.dockRule, match = RuleMatch("uid", "prefix", "04A2"), profiles = listOf(warehouse.id)),
            Rule(id = "DEMO-RULE-2", name = t.frontDoor, match = RuleMatch("tag_label", "equals", t.frontDoor), profiles = listOf(door.id)),
        )))

        val now = System.currentTimeMillis()
        val minute = 60_000L
        data class Row(val ago: Long, val p: Profile, val uid: String, val status: Int?, val latency: Long?, val error: String)
        val rows = listOf(
            Row(190, rounds, "E004015012345678", 200, 142, ""),
            Row(150, door, "04B91C2A7F6D80", 200, 96, ""),
            Row(95, warehouse, "04A2113C6A2280", 503, 211, ""),
            Row(94, warehouse, "04A2113C6A2280", 200, 88, ""),
            Row(60, rounds, "E004015012345678", null, null, "UnknownHostException"),
            Row(31, door, "04B91C2A7F6D80", 200, 73, ""),
            Row(12, warehouse, "04A2113C6A2280", 200, 91, ""),
            Row(1, warehouse, "04A27F1B5E8000", 200, 84, ""),
        )
        runBlocking(Dispatchers.IO) {
            app.history.clear()
            rows.forEach { r ->
                app.history.record(HistoryEntry(
                    timeMs = now - r.ago * minute, profileId = r.p.id, profileName = r.p.name, uid = r.uid, chip = "", tagType = "",
                    outcome = HistoryCsv.outcome(null, r.status), status = r.status, latencyMs = r.latency, error = r.error,
                    message = if (r.status == 200 && r.p.id == warehouse.id) t.message else null,
                    request = "${r.p.request.method} ${r.p.request.url}", requestBody = null, responseBody = null,
                ))
            }
        }

        scanVm.showDemo(listOf(ScanOutcome(
            scanTimeMs = now - minute, profileId = warehouse.id, profileName = warehouse.name,
            uid = "04A27F1B5E8000", chip = "", tagType = "", randomUid = false, request = null,
            result = SendResult(status = 200, latencyMs = 84, responseBody = "{\"message\":\"${t.message}\"}", responseHeaders = emptyList(), error = null),
            buildError = null, message = t.message, resultText = t.result,
        )))
    }
}
