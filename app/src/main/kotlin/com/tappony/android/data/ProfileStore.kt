package com.tappony.android.data

import android.content.Context
import com.tappony.core.Profile
import com.tappony.core.ProfileCodec
import com.tappony.core.ProfileException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/**
 * Profiles are JSON documents, one file per profile, in app-private storage.
 * The on-disk format is the export format and the cross-platform format
 * (PROFILE_SCHEMA.md). Secrets are never in these files.
 */
class ProfileStore(context: Context) {

    private val dir = File(context.filesDir, "profiles").apply { mkdirs() }
    private val _profiles = MutableStateFlow(load())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private fun load(): List<Profile> =
        dir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { f -> runCatching { ProfileCodec.decode(f.readText()) }.getOrNull() }
            .sortedBy { it.name.lowercase() }

    fun get(id: String): Profile? = _profiles.value.firstOrNull { it.id == id }

    @Synchronized
    fun save(profile: Profile) {
        val target = File(dir, "${profile.id}.json")
        val tmp = File(dir, "${profile.id}.json.tmp")
        tmp.writeText(ProfileCodec.encode(profile))
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
        _profiles.value = load()
    }

    @Synchronized
    fun delete(id: String) {
        File(dir, "$id.json").delete()
        _profiles.value = load()
    }

    fun import(json: String): Profile {
        val p = ProfileCodec.decode(json)
        val fresh = if (get(p.id) != null) p.copy(id = newId()) else p
        save(fresh)
        return fresh
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString().uppercase()

        fun decodeOrNull(json: String): Profile? = try {
            ProfileCodec.decode(json)
        } catch (e: ProfileException) {
            null
        }
    }
}
