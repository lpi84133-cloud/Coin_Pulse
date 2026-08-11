package com.coinpulse.coinpulsegame.dispatchbox

/**
 * Hands a pushed URL to a shell that is already running. Nothing here is ever
 * written to disk: a push URL is good for exactly one opening.
 *
 * Two arrivals need two answers. A push that lands while the shell is on screen
 * goes straight to [listener]. A push the user taps while the app sits in the
 * background reaches the launcher instead — and by then the shell has dropped
 * its listener, because that is what leaving the foreground does. So the URL
 * waits in [pending], the launcher draws nothing at all, and the shell collects
 * it on the way back up. Showing a splash for that would take the page the user
 * left off the screen for no reason.
 */
internal object LiveLink {

    @Volatile
    var listener: ((String) -> Unit)? = null

    /** True from the shell's onCreate to its onDestroy, screen state aside. */
    @Volatile
    var shellExists = false

    @Volatile
    private var pending: String? = null

    /** @return true when the running shell has it and the caller must not route. */
    fun offer(url: String): Boolean {
        listener?.let { deliver ->
            deliver(url)
            return true
        }
        if (!shellExists) return false
        pending = url
        return true
    }

    fun collect(): String? {
        val waiting = pending
        pending = null
        return waiting
    }
}
