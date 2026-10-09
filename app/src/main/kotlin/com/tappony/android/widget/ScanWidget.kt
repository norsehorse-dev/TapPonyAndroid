package com.tappony.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.tappony.android.MainActivity
import com.tappony.android.R
import com.tappony.android.Shortcuts
import com.tappony.android.TapPonyApp
import com.tappony.android.i18n.LanguageManager

/**
 * Home screen widget: one tap opens TapPony on the Scan tab with the profile picked
 * when the widget was placed, or with whatever profile is active. Like the Quick
 * Settings tile it only gets you to the Scan screen; Android reads tags only while
 * an app is in the foreground. The tap is the same tappony://scan link the
 * launcher shortcuts use.
 */
class ScanWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, manager, it) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.clear(context, it) }
    }

    companion object {

        /** Redraws every placed widget. Called when profiles, the active profile or the language change. */
        fun refreshAll(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context) ?: return
                manager.getAppWidgetIds(ComponentName(context, ScanWidget::class.java)).forEach { render(context, manager, it) }
            }
        }

        fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val app = context.applicationContext as TapPonyApp
            val profiles = app.profiles.profiles.value
            // A pinned profile that is gone falls back to the active profile rather than a dead button.
            // The pin itself is kept, so a profile that failed to load once comes back on the next redraw.
            val pinnedProfile = WidgetPrefs.profileFor(context, widgetId)?.let { id -> profiles.firstOrNull { it.id == id } }
            val pinned: String? = pinnedProfile?.id
            val shown = pinnedProfile
                ?: profiles.firstOrNull { it.id == app.settings.activeProfileId.value }
                ?: profiles.firstOrNull()
            val label = shown?.name?.ifBlank { null }
                ?: LanguageManager.localized(context).getString(if (shown == null) R.string.scan_no_profile else R.string.editor_untitled)
            val link = if (pinned != null) Shortcuts.linkFor(pinned) else Uri.parse("tappony://scan")
            val open = Intent(Intent.ACTION_VIEW, link, context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val tap = PendingIntent.getActivity(context, widgetId, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val views = RemoteViews(context.packageName, R.layout.widget_scan).apply {
                setTextViewText(R.id.widget_label, label)
                val res = LanguageManager.localized(context)
                setContentDescription(R.id.widget_root, if (shown == null) res.getString(R.string.tab_scan) else res.getString(R.string.shortcut_scan_with, label))
                setOnClickPendingIntent(R.id.widget_root, tap)
            }
            manager.updateAppWidget(widgetId, views)
        }
    }
}

/** Which profile each placed widget scans with. No entry means it follows the active profile. */
internal object WidgetPrefs {
    private const val FILE = "widgets"
    private fun key(widgetId: Int) = "profile_$widgetId"

    fun profileFor(context: Context, widgetId: Int): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(key(widgetId), null)

    fun set(context: Context, widgetId: Int, profileId: String?) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().apply {
            if (profileId == null) remove(key(widgetId)) else putString(key(widgetId), profileId)
        }.apply()
    }

    fun clear(context: Context, widgetId: Int) = set(context, widgetId, null)
}
