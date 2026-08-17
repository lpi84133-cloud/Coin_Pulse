package com.coinpulse.coinpulsegame.bylaw

import com.coinpulse.coinpulsegame.BuildConfig
import com.coinpulse.coinpulsegame.coffer.Cloak

/**
 * Read-only window onto the values the build derived for this application.
 * Nothing here decides anything; it exists so no other file has to mention
 * BuildConfig, and so a timeout or an endpoint is never typed out by hand
 * somewhere it would become a constant two applications share.
 */
internal object Bylaw {

    val bundleId: String get() = BuildConfig.SHELL_ID
    val uaToken: String get() = BuildConfig.SHELL_TAG
    val uaCarriesTail: Boolean get() = BuildConfig.UA_TAIL

    /** How long each step of the launch is allowed to take. */
    object Wait {
        val coldAttribution: Long get() = BuildConfig.ATTR_COLD_MS
        val warmAttribution: Long get() = BuildConfig.ATTR_WARM_MS
        val deferredLink: Long get() = BuildConfig.LINK_WAIT_MS
        val endpoint: Long get() = BuildConfig.CFG_WAIT_MS
        val directAttribution: Long get() = BuildConfig.GCD_WAIT_MS
        val organicRecheck: Long get() = BuildConfig.ORGANIC_PAUSE_MS
        val connectionGrace: Long get() = BuildConfig.NET_GRACE_MS
        val edgeInjection: Long get() = BuildConfig.EDGE_DELAY_MS
        val connectionProbe: Long get() = BuildConfig.PULSE_MS
    }

    val hopBudget: Int get() = BuildConfig.HOP_BUDGET
    val promptSnoozeSeconds: Long get() = BuildConfig.ASK_AGAIN_SEC

    fun endpoint(): String = Cloak.open(BuildConfig.HID_CFG)
    fun trackerKey(): String = Cloak.open(BuildConfig.HID_TRACK)
    fun analyticsProject(): String = Cloak.open(BuildConfig.HID_FB)
    fun directAttributionBase(): String = Cloak.open(BuildConfig.HID_GCD)

    /** Blank in release: the build script overwrites the field for that variant. */
    val forcedUrl: String get() = BuildConfig.FORCE_URL
}
