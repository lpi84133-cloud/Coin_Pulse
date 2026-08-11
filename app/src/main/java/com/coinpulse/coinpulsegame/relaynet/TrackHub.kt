package com.coinpulse.coinpulsegame.relaynet

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.appsflyer.deeplink.DeepLinkListener
import com.appsflyer.deeplink.DeepLinkResult
import com.coinpulse.coinpulsegame.BuildConfig
import com.coinpulse.coinpulsegame.charter.Agent
import com.coinpulse.coinpulsegame.charter.Charter
import com.coinpulse.coinpulsegame.charter.Echo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.Collections
import java.util.Locale

/**
 * Where this install came from, asked exactly once and answered once.
 *
 * The two halves of starting the tracker are deliberately far apart:
 *
 *  * [wireUp] runs in the Application and puts nothing on the wire. What it
 *    does do is register the activity-lifecycle callbacks the SDK uses to
 *    notice the app reached the foreground — register those while an activity
 *    is already resumed and the SDK misses the event, queues the launch, and
 *    sends it at the next activity transition, half a minute later, long after
 *    the routing decision has been made and written down.
 *  * [ignite] is the call that speaks, it takes the Activity rather than the
 *    application context (handing it the application context is the same
 *    thirty-second hang), and it must never run before a connection has been
 *    confirmed. Started offline, the SDK fails within milliseconds and that
 *    empty answer is final for the session: turning Wi-Fi on afterwards gets
 *    the cached nothing back, the endpoint is asked with no attribution, and
 *    the paid install is filed as organic.
 */
internal class TrackHub(context: Context) {

    private val app = context.applicationContext

    /** null while the answer is still open; empty map means "asked, got nothing". */
    private val answer = MutableStateFlow<Map<String, Any?>?>(null)

    private val linkArrived = MutableStateFlow(false)
    private val linkFields = Collections.synchronizedMap(LinkedHashMap<String, String>())

    private var wired = false
    private var speaking = false

    @Volatile private var askedTwice = false
    private var secondAskAt = 0L

    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Bringing the SDK up ─────────────────────────────────────────────────

    fun wireUp() {
        if (wired) return
        wired = true

        val key = Charter.trackerKey()
        if (key.isEmpty()) {
            Echo.odd(TAG, "no tracker key compiled in — attribution resolves empty")
            close(emptyMap())
            return
        }

        val outcome = runCatching {
            AppsFlyerLib.getInstance().apply {
                setDebugLog(BuildConfig.DEBUG)
                subscribeForDeepLink(linkListener)   // always before init
                init(key, conversions, app)
            }
        }
        if (outcome.isFailure) {
            Echo.odd(TAG, "SDK refused to wire up: ${outcome.exceptionOrNull()?.message}")
            close(emptyMap())
        }
    }

    fun ignite(host: Activity) {
        wireUp()
        if (speaking || Charter.trackerKey().isEmpty()) return
        speaking = true

        val outcome = runCatching { AppsFlyerLib.getInstance().start(host) }
        if (outcome.isFailure) {
            Echo.odd(TAG, "SDK refused to start: ${outcome.exceptionOrNull()?.message}")
            close(emptyMap())
            return
        }
        Echo.note(TAG, "tracker started from ${host.javaClass.simpleName}")
    }

    /**
     * Asks again when the previous attempt came back with nothing — the shape a
     * start that happened without a connection leaves behind. An attempt still
     * in flight is left alone, and so is any real answer, organic included:
     * asking over a healthy launch would put a second launch event on the wire.
     */
    fun askAgainIfEmpty(host: Activity) {
        if (!speaking) {
            ignite(host)
            return
        }
        val settled = answer.value ?: return
        if (settled.isNotEmpty()) return

        answer.value = null
        askedTwice = true
        secondAskAt = SystemClock.elapsedRealtime()
        runCatching { AppsFlyerLib.getInstance().start(host) }
        Echo.note(TAG, "previous answer was empty — asking again now the link is up")
    }

    // ── Waiting ─────────────────────────────────────────────────────────────

    /**
     * The conversion and the deferred deep link are waited on together, with the
     * link on the shorter leash: the endpoint is entitled to see the click's
     * parameters next to the attribution, but a launch that carries no deep link
     * must not be held up for one.
     */
    suspend fun awaitOrigin(limitMs: Long): Map<String, Any?> = coroutineScope {
        val link = async {
            withTimeoutOrNull(Charter.Wait.deferredLink) { linkArrived.first { it } }
        }
        val data = async { awaitConversion(limitMs) }
        link.await()
        data.await()
    }

    private suspend fun awaitConversion(limitMs: Long): Map<String, Any?> {
        // A repeat question gets a shorter window: the SDK may never call back.
        val window = if (askedTwice) minOf(limitMs, SECOND_ASK_WINDOW_MS) else limitMs
        val fromSdk = withTimeoutOrNull(window) { answer.filterNotNull().first() } ?: emptyMap()
        if (fromSdk.isNotEmpty()) return fromSdk

        if (askedTwice) {
            val spent = SystemClock.elapsedRealtime() - secondAskAt
            if (spent < SECOND_ASK_WINDOW_MS) delay(SECOND_ASK_WINDOW_MS - spent)
        }

        val direct = askServerDirectly()
        if (direct.isNullOrEmpty()) return fromSdk
        Echo.note(TAG, "attribution recovered from the direct lookup")
        answer.value = direct
        return direct
    }

    // ── SDK callbacks ───────────────────────────────────────────────────────

    private val conversions = object : AppsFlyerConversionListener {

        override fun onConversionDataSuccess(data: MutableMap<String, Any?>) {
            Echo.note(TAG, "conversion data arrived")
            val snapshot = HashMap(data)
            worker.launch {
                val organic = snapshot["af_status"]?.toString().equals("Organic", ignoreCase = true)
                val resolved = if (!organic) snapshot else {
                    // "Organic" on a first run is often a false positive: the
                    // click has not been matched yet. Give it a beat, then ask
                    // AppsFlyer directly before believing it.
                    delay(Charter.Wait.organicRecheck)
                    askServerDirectly() ?: snapshot
                }
                close(resolved)
            }
        }

        override fun onConversionDataFail(reason: String?) {
            Echo.odd(TAG, "conversion failed: $reason")
            close(emptyMap())
        }

        override fun onAppOpenAttribution(data: MutableMap<String, String>?) {
            data?.forEach { (name, value) -> linkFields[name] = value }
        }

        override fun onAttributionFailure(reason: String?) {
            Echo.odd(TAG, "attribution failed: $reason")
            close(emptyMap())
        }
    }

    /** Every branch completes the gate, or the parallel wait always burns its full window. */
    private val linkListener = DeepLinkListener { result ->
        if (result.status == DeepLinkResult.Status.FOUND) {
            runCatching {
                val click = result.deepLink.clickEvent
                click.keys().forEach { name -> linkFields[name] = click.opt(name)?.toString().orEmpty() }
            }
        } else {
            Echo.note(TAG, "deep link status ${result.status}")
        }
        linkArrived.value = true
    }

    private fun close(data: Map<String, Any?>) {
        answer.value = data
        linkArrived.value = true
    }

    // ── Direct lookup ───────────────────────────────────────────────────────

    /**
     * AppsFlyer's own record for this install, read over HTTP. Worth trying when
     * the SDK has nothing to say — but only after it has had a few seconds:
     * the lookup answers 400 for an install AppsFlyer has not been told about
     * yet, and an install queued while the device was offline is only filed
     * once the launch actually goes out.
     */
    private suspend fun askServerDirectly(): Map<String, Any?>? = withContext(Dispatchers.IO) {
        val base = Charter.directAttributionBase()
        val key = Charter.trackerKey()
        if (base.isEmpty() || key.isEmpty()) return@withContext null

        val uid = runCatching { AppsFlyerLib.getInstance().getAppsFlyerUID(app) }.getOrNull()
        if (uid.isNullOrEmpty()) return@withContext null

        val reply = Wire.get(
            endpoint = "$base${Charter.bundleId}?device_id=$uid",
            headers = mapOf(
                "Authorization" to "Bearer $key",
                "Accept" to "application/json"
            ),
            timeoutMs = Charter.Wait.directAttribution.toInt()
        ) ?: return@withContext null

        if (reply.status !in 200..299) {
            Echo.odd(TAG, "direct lookup answered ${reply.status}")
            return@withContext null
        }
        runCatching {
            val parsed = JSONObject(reply.text)
            parsed.keys().asSequence().associateWith { parsed.opt(it) }
        }.getOrNull()
    }

    fun trackerId(): String =
        runCatching { AppsFlyerLib.getInstance().getAppsFlyerUID(app) }.getOrNull().orEmpty()

    // ── The body the endpoint is asked with ─────────────────────────────────

    /**
     * Conversion fields go in verbatim, then any deep-link parameter the
     * conversion did not already carry, then this device's own fields, which
     * have the last word.
     */
    fun composeQuestion(origin: Map<String, Any?>, pushToken: String?): JSONObject =
        JSONObject().apply {
            origin.forEach { (name, value) -> if (value != null) put(name, value.toString()) }
            synchronized(linkFields) {
                linkFields.forEach { (name, value) -> if (!has(name)) put(name, value) }
            }

            put("af_id", trackerId())
            put("bundle_id", Charter.bundleId)
            put("store_id", Charter.bundleId)
            put("os", "Android")
            put("locale", Locale.getDefault().toLanguageTag().replace('-', '_'))
            if (!pushToken.isNullOrBlank()) put("push_token", pushToken)
            Charter.analyticsProject().takeIf { it.isNotEmpty() }
                ?.let { put("firebase_project_id", it) }

            Echo.note(TAG, "question composed with ${length()} fields")
        }

    fun shutdown() = worker.cancel()

    private companion object {
        const val TAG = "TrackHub"

        /** How long a repeat question waits before the direct lookup takes over. */
        const val SECOND_ASK_WINDOW_MS = 8_000L
    }
}
