package com.tappony.android

/**
 * The one gate for Plus features (PLANNING_1_0_0.md sections 8 and 9).
 *
 * TapPony 1.0 is free with every feature on, so [plus] is always true. The
 * gate exists so a future store unlock only changes this object. The F-Droid
 * build and the direct APK keep every feature on for good.
 */
object Entitlements {
    val plus: Boolean get() = true

    const val FREE_PROFILES = 3
    const val FREE_TAGS = 25

    fun canAddProfile(count: Int): Boolean = plus || count < FREE_PROFILES

    /** Named tags in the registry beyond the free 25. */
    fun canAddTag(count: Int): Boolean = plus || count < FREE_TAGS

    /** Routing rules, fan-out and ignoring unmatched tags. */
    val rules: Boolean get() = plus

    /** Batch mode with its running list and once-per-batch dedupe. */
    val batch: Boolean get() = plus

    /** Holding scans that got no response and sending them later. */
    val offlineQueue: Boolean get() = plus

    /** Reply message field, custom result text and spoken confirmation. */
    val responseRules: Boolean get() = plus

    /** Notes and a default profile on a named tag. */
    val tagDefaults: Boolean get() = plus
}
