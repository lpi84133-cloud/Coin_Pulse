package com.coinpulse.coinpulsegame.relaynet

import com.coinpulse.coinpulsegame.bylaw.Bylaw
import com.coinpulse.coinpulsegame.bylaw.Echo
import com.coinpulse.coinpulsegame.bylaw.HostGate
import com.coinpulse.coinpulsegame.bylaw.RouteVerdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The one call to the backend: here is where this install came from, is there a
 * page for it.
 *
 * The distinction the return type makes is the point of the whole file. A reply
 * that arrives is a verdict, whatever it says. A request that never reaches
 * anyone is not, and the caller must not write it down as one.
 *
 * A URL the endpoint names but the host list refuses is treated as a refusal
 * rather than a handover — the endpoint did answer, but its answer was rejected
 * by our own gate, so whether the server has ruled on this install is still
 * open the next time the app starts.
 */
internal object RouteAsk {

    suspend fun put(question: JSONObject): RouteVerdict = withContext(Dispatchers.IO) {
        val endpoint = Bylaw.endpoint()
        if (endpoint.isEmpty()) {
            Echo.odd(TAG, "no endpoint compiled in — nobody to ask")
            return@withContext RouteVerdict.Unheard
        }

        val reply = Wire.postJson(
            endpoint = endpoint,
            payload = question.toString(),
            timeoutMs = Bylaw.Wait.endpoint.toInt()
        )
        if (reply == null) {
            Echo.odd(TAG, "endpoint unreachable")
            return@withContext RouteVerdict.Unheard
        }

        Echo.note(TAG, "endpoint answered ${reply.status} (${reply.text.length} chars)")
        if (reply.status !in 200..299) return@withContext RouteVerdict.Refused
        read(reply.text)
    }

    private fun read(raw: String): RouteVerdict {
        if (raw.isBlank()) return RouteVerdict.Refused
        val body = runCatching { JSONObject(raw) }.getOrElse {
            Echo.odd(TAG, "answer was not JSON")
            return RouteVerdict.Refused
        }

        val granted = body.optBoolean("ok", false)
        val url = body.optString("url").trim()
        if (!granted || url.isEmpty()) return RouteVerdict.Refused

        if (!HostGate.permits(url)) {
            Echo.odd(TAG, "the named URL is not on the host list")
            return RouteVerdict.Refused
        }
        return RouteVerdict.Handover(url, body.optLong("expires", 0L))
    }

    private const val TAG = "RouteAsk"
}
