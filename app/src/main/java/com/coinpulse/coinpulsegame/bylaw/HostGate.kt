package com.coinpulse.coinpulsegame.bylaw

import android.net.Uri
import com.coinpulse.coinpulsegame.BuildConfig

/**
 * The gate on the two places an outsider can hand this app a URL: the config
 * endpoint's answer and a push payload. A host passes when it equals an entry
 * or sits under one.
 *
 * Navigation *inside* the WebView is deliberately not gated. A redirect chain
 * crosses hosts nobody can list in advance, and gating it would break the
 * product rather than protect it.
 */
internal object HostGate {

    private val allowed: List<String> by lazy {
        BuildConfig.HOST_LIST.split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
    }

    val armed: Boolean get() = allowed.isNotEmpty()

    fun permits(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (allowed.isEmpty()) return true
        val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase() ?: return false
        return allowed.any { entry -> host == entry || host.endsWith(".$entry") }
    }

    fun complainIfOpen() {
        if (!armed) Echo.odd("HostGate", "no host list compiled in — any incoming URL is accepted")
    }
}
