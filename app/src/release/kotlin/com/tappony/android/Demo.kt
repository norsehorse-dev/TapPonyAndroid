package com.tappony.android

import android.content.Intent
import com.tappony.android.ui.ScanViewModel

/** Release builds have no screenshot demo; see src/debug for the real one. */
object Demo {
    val active: Boolean get() = false

    @Suppress("UNUSED_PARAMETER")
    fun handle(app: TapPonyApp, intent: Intent?, scanVm: ScanViewModel): String? = null
}
