package com.tappony.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.tappony.core.Profile

/**
 * Launcher shortcuts: long-press the app icon to jump straight to scanning with
 * a profile, or pin one to the home screen from the profile editor. Every
 * shortcut is a tappony://scan?profile=<id> link, the same one other apps use.
 */
object Shortcuts {

    private const val MAX_DYNAMIC = 4

    fun linkFor(profileId: String): Uri = Uri.parse("tappony://scan").buildUpon().appendQueryParameter("profile", profileId).build()

    private fun info(context: Context, p: Profile): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, "profile-${p.id}")
            .setShortLabel(p.name.take(24).ifBlank { context.getString(R.string.editor_untitled) })
            .setLongLabel(context.getString(R.string.shortcut_scan_with, p.name).take(48))
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(Intent(Intent.ACTION_VIEW, linkFor(p.id), context, MainActivity::class.java))
            .build()

    /** Mirrors the first few profiles (active one first) into the launcher's long-press menu. */
    fun sync(context: Context, profiles: List<Profile>, activeId: String?) {
        val ordered = profiles.sortedByDescending { it.id == activeId }.take(MAX_DYNAMIC)
        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(context, ordered.map { info(context, it) })
            val live = profiles.map { "profile-${it.id}" }.toSet()
            val stale = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
                .map { it.id }.filter { it.startsWith("profile-") && it !in live }
            if (stale.isNotEmpty()) ShortcutManagerCompat.disableShortcuts(context, stale, context.getString(R.string.shortcut_profile_deleted))
        }
    }

    /** Asks the launcher to pin a shortcut for this profile. Returns false when the launcher can't. */
    fun pin(context: Context, p: Profile): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        return runCatching { ShortcutManagerCompat.requestPinShortcut(context, info(context, p), null) }.getOrDefault(false)
    }
}
