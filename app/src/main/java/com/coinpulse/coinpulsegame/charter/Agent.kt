package com.coinpulse.coinpulsegame.charter

import android.os.Build

/**
 * The single User-Agent this application presents. The config POST, the direct
 * attribution lookup and the WebView all read this one string: a backend that
 * ruled on one UA and a partner page that then sees another is how sessions get
 * dropped on the first navigation.
 *
 * The device part is real — brand, model and build come from the running phone.
 * The Chrome version tuple is drawn from this application's seed, which is the
 * part that would otherwise be identical across a portfolio.
 */
internal object Agent {

    val line: String by lazy(LazyThreadSafetyMode.PUBLICATION) { compose() }

    private fun compose(): String {
        val head = StringBuilder(160)
        head.append("Mozilla/5.0 (Linux; Android ")
            .append(Build.VERSION.RELEASE)
            .append("; ")
            .append(printable(Build.BRAND))
            .append(' ')
            .append(printable(Build.MODEL).replace(' ', '_'))

        val buildId = printable(Build.ID)
        if (buildId.isNotEmpty()) head.append(" Build/").append(buildId)

        head.append(") AppleWebKit/537.36 (KHTML, like Gecko) Chrome/")
            .append(com.coinpulse.coinpulsegame.BuildConfig.UA_MAJOR).append(".0.")
            .append(com.coinpulse.coinpulsegame.BuildConfig.UA_BUILD).append('.')
            .append(com.coinpulse.coinpulsegame.BuildConfig.UA_PATCH)
            .append(" Mobile Safari/537.36")

        if (Charter.uaCarriesTail) {
            head.append(' ').append(Charter.uaToken)
        }
        return head.toString()
    }

    private fun printable(raw: String?): String = raw?.filter { it in ' '..'~' }.orEmpty()
}
