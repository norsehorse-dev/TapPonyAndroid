package com.tappony.android.data

import android.content.Context
import com.tappony.core.TagEntry
import com.tappony.core.TagRegistry
import com.tappony.core.Tags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executors

/** tags.json beside the profiles (PROFILE_SCHEMA.md section 16). */
class TagsStore(context: Context) {

    private val file = File(context.filesDir, "tags.json")
    private val _current = MutableStateFlow(load())
    val current: StateFlow<TagRegistry> = _current.asStateFlow()
    private val io = Executors.newSingleThreadExecutor()

    private fun load(): TagRegistry =
        if (!file.exists()) TagRegistry() else runCatching { Tags.decode(file.readText()) }.getOrDefault(TagRegistry())

    /** Updates [current] at once; the file is written off the calling thread, in save order. */
    @Synchronized
    fun save(reg: TagRegistry) {
        _current.value = reg
        val text = Tags.encode(reg)
        io.execute {
            runCatching {
                val tmp = File(file.parentFile, "tags.json.tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            }
        }
    }

    /** Replaces the entry for the same tag (same token, or same UID), or adds it at the end. */
    @Synchronized
    fun upsert(entry: TagEntry, replacing: TagEntry? = null) {
        val list = _current.value.tags.toMutableList()
        val target = replacing ?: entry
        // The exact entry first, so a duplicate UID (hand-edited file) edits the row that was opened.
        val i = list.indexOfFirst { it == target }.takeIf { it >= 0 } ?: list.indexOfFirst { same(it, target) }
        if (i >= 0) list[i] = entry else list.add(entry)
        save(_current.value.copy(tags = list))
    }

    @Synchronized
    fun remove(entry: TagEntry) {
        save(_current.value.copy(tags = _current.value.tags.filterNot { it == entry }))
    }

    private fun same(a: TagEntry, b: TagEntry): Boolean =
        (a.token.isNotEmpty() && a.token == b.token) || (a.token.isEmpty() && b.token.isEmpty() && a.uid.isNotEmpty() && a.uid == b.uid)
}
