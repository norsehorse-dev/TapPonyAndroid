package com.tappony.android.data

import android.content.Context
import com.tappony.core.RuleSet
import com.tappony.core.Rules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executors

/** rules.json beside the profiles (PROFILE_SCHEMA.md section 14). */
class RulesStore(context: Context) {

    private val file = File(context.filesDir, "rules.json")
    private val _current = MutableStateFlow(load())
    val current: StateFlow<RuleSet> = _current.asStateFlow()
    private val io = Executors.newSingleThreadExecutor()

    private fun load(): RuleSet =
        if (!file.exists()) RuleSet() else runCatching { Rules.decode(file.readText()) }.getOrDefault(RuleSet())

    /** Updates [current] at once; the file is written off the calling thread, in save order. */
    @Synchronized
    fun save(set: RuleSet) {
        _current.value = set
        val text = Rules.encode(set)
        io.execute {
            runCatching {
                val tmp = File(file.parentFile, "rules.json.tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            }
        }
    }
}
