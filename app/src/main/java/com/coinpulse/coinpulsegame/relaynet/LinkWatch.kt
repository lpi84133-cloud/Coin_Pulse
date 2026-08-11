package com.coinpulse.coinpulsegame.relaynet

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Whether this device currently has a way out.
 *
 * [up] is the instant answer and the only one allowed to decide the first frame
 * of a launch — waiting for a grace period is exactly what the no-wifi-first
 * rule exists to avoid. [changes] follows the *default* network rather than a
 * capability-filtered request, because that is the subscription that reports a
 * loss immediately even when no request is in flight. [reaches] opens a real
 * socket, for the moment a user taps Retry and deserves a truthful answer.
 */
internal class LinkWatch(context: Context) {

    private val manager = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun up(): Boolean {
        val active = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun reaches(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(PROBE_HOST, PROBE_PORT), PROBE_TIMEOUT_MS)
                true
            }
        }.getOrDefault(false)
    }

    val changes: Flow<Boolean> = callbackFlow {
        val watcher = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onUnavailable() {
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        manager.registerDefaultNetworkCallback(watcher)
        trySend(up())
        awaitClose { runCatching { manager.unregisterNetworkCallback(watcher) } }
    }.distinctUntilChanged()

    private companion object {
        const val PROBE_HOST = "8.8.8.8"
        const val PROBE_PORT = 53
        const val PROBE_TIMEOUT_MS = 3_000
    }
}
