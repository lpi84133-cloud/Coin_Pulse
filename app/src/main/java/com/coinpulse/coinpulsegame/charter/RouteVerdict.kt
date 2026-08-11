package com.coinpulse.coinpulsegame.charter

/**
 * What the config endpoint had to say.
 *
 * [Refused] and [Unheard] are both "no WebView this time", and keeping them
 * apart is the whole point of the type. A server that replied — 404, `ok:false`,
 * an empty body, anything with a status line — has ruled on this install, and
 * that ruling is worth writing down for good. A request that never reached one
 * has ruled on nothing, and recording it would lock a paid user out of the gray
 * side for the life of the install over one bad second of network.
 */
internal sealed class RouteVerdict {

    data class Handover(val url: String, val expiresAt: Long) : RouteVerdict()

    /** The endpoint answered, and the answer was no. */
    object Refused : RouteVerdict()

    /** Nobody answered: no status line, no verdict, nothing to persist. */
    object Unheard : RouteVerdict()
}
