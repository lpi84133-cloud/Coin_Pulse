package com.coinpulse.coinpulsegame

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.coinpulse.coinpulsegame.BuildConfig
import com.coinpulse.coinpulsegame.pulsegate.PulseApp
import com.coinpulse.coinpulsegame.bylaw.Bylaw
import com.coinpulse.coinpulsegame.bylaw.Echo
import com.coinpulse.coinpulsegame.bylaw.HostGate
import com.coinpulse.coinpulsegame.bylaw.RouteVerdict
import com.coinpulse.coinpulsegame.deck.BootCurtain
import com.coinpulse.coinpulsegame.deck.EdgeFit
import com.coinpulse.coinpulsegame.deck.handOverFlat
import com.coinpulse.coinpulsegame.deck.LinkLostDeck
import com.coinpulse.coinpulsegame.deck.PermitDeck
import com.coinpulse.coinpulsegame.deck.WebDeck
import com.coinpulse.coinpulsegame.dispatchbox.LiveLink
import com.coinpulse.coinpulsegame.relaynet.LinkWatch
import com.coinpulse.coinpulsegame.relaynet.RouteAsk
import com.coinpulse.coinpulsegame.coffer.Locker
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * The launcher, and the only place that decides which face of this application
 * the user gets.
 *
 * Undecided (a first launch):
 *   * no link → the no-signal board as the very first frame. Nothing started,
 *     nothing asked, nothing written down; the board relaunches this activity
 *     when the link comes back, and that run is a clean first ask.
 *   * a link → start the tracker, wait for the origin and the deferred link
 *     together, ask the backend.
 *       a page      → the lane becomes STREAM and every later launch still
 *                     needs the link, because the shell it runs in is the web
 *                     one.
 *       anything else → the lane becomes GAME and stays there for the life of
 *                     the install. The native game does not talk to a
 *                     backend, so nothing asks about the link again — a user
 *                     who landed in the game once plays it forever, offline
 *                     included.
 *
 * Stream (the page, last time):
 *   * no link → the no-signal board with the saved page to come back to
 *   * a parked push URL wins over everything else
 *   * otherwise ask again, and fall back to the saved page if nobody answers
 *
 * Game (the game, last time): the game, always — including when a push arrives
 * for this install. An install that settled on the game stays there.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var locker: Locker
    private lateinit var link: LinkWatch
    private var curtain: BootCurtain? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locker = Locker(applicationContext)
        link = LinkWatch(applicationContext)

        // A fire-and-forget priming of the push token. FirebaseMessaging can
        // only fetch a fresh token when the process is online, and an install
        // whose first launch is offline (the classic reinstall-from-cached-APK
        // flow) never gets to onNewToken because there is nothing to hand back
        // yet. Once the network comes up the retry from the offline board
        // relaunches this activity, the same call resolves, and the token is
        // in the locker in time for the next gate question — which is the one
        // that tells the backend where to send pushes for this install.
        warmMessagingToken()

        val pushed = pushTargetIn(intent)

        // A tap while the shell is still alive: hand the URL over and get out of
        // the way. Drawing anything here would take the page the user left off
        // the screen for no reason.
        if (pushed != null && locker.lane == Locker.Lane.STREAM && LiveLink.offer(pushed)) {
            Echo.note(TAG, "warm push handed to the live shell")
            finish()
            handOverFlat()
            return
        }

        if (locker.lane == Locker.Lane.GAME) {
            Echo.note(TAG, "settled install → game, nothing is asked")
            raiseCurtain()
            toGame()
            return
        }

        // Installed from a link with the radio off. Straight to the board — no
        // splash, and no progress bar filling for a decision that will not be
        // made on this launch.
        if (pushed == null && locker.lane == Locker.Lane.UNSET && !link.up()) {
            Echo.note(TAG, "first launch with no link → no-signal board first frame")
            startActivity(Intent(this, LinkLostDeck::class.java))
            finish()
            handOverFlat()
            return
        }

        raiseCurtain()
        if (pushed != null) locker.parkedPush = pushed
        scope.launch { decide() }
    }

    private fun raiseCurtain() {
        val board = BootCurtain(this)
        curtain = board
        setContentView(board)
        EdgeFit.immerse(this)
        EdgeFit.spanCutout(this)
    }

    // ── The decision ────────────────────────────────────────────────────────

    private suspend fun decide() {
        val forced = Bylaw.forcedUrl
        if (BuildConfig.DEBUG && forced.isNotBlank()) {
            Echo.odd(TAG, "debug build with a forced URL — skipping the whole decision")
            toPage(forced)
            return
        }

        val parked = locker.takeParkedPush()
        if (!parked.isNullOrBlank() && HostGate.permits(parked)) {
            Echo.note(TAG, "parked push URL → the page")
            if (locker.lane == Locker.Lane.UNSET) locker.lane = Locker.Lane.STREAM
            toPage(parked)
            return
        }

        when (locker.lane) {
            Locker.Lane.GAME -> toGame()
            Locker.Lane.STREAM -> returningRun()
            Locker.Lane.UNSET -> firstRun()
        }
    }

    private suspend fun firstRun() {
        if (!waitForLink(firstEver = true)) return

        val origin = askWhereFrom(Bylaw.Wait.coldAttribution)
        when (val verdict = RouteAsk.put(question(origin))) {
            is RouteVerdict.Handover -> {
                locker.lane = Locker.Lane.STREAM
                locker.target = verdict.url
                locker.targetExpiry = verdict.expiresAt
                toPage(verdict.url)
            }

            RouteVerdict.Refused -> {
                // The native game runs offline for good and does not talk to a
                // backend, so a user who lands in it on the first launch never
                // needs the link again. That is the product rule, and it means
                // the lane is fixed to GAME the moment we decide to hand the
                // game over — whether the refusal came with a paid origin or
                // an empty/organic one. The trade-off is a slow OneLink match
                // that only arrives on a later launch: the install has already
                // committed to the game and will not switch. `originIsPaid`
                // is kept for the log, so a refusal on a paid conversion is
                // still visible as such.
                locker.lane = Locker.Lane.GAME
                if (originIsPaid(origin)) {
                    Echo.note(TAG, "backend refused a paid install → game, and it sticks")
                } else {
                    Echo.note(TAG, "organic or empty origin → game, and it sticks")
                }
                toGame()
            }

            RouteVerdict.Unheard -> {
                // Same reason as above: no more launches will have a chance to
                // change this install's mind, because the game path will not
                // ask again. An install that spent its one shot at attribution
                // and got silence is a game install for the rest of its life.
                locker.lane = Locker.Lane.GAME
                Echo.note(TAG, "nobody answered → game, and it sticks")
                toGame()
            }
        }
    }

    private suspend fun returningRun() {
        if (!waitForLink(firstEver = false)) return

        val saved = if (locker.targetUsable()) locker.target else null
        val origin = askWhereFrom(Bylaw.Wait.warmAttribution)

        when (val verdict = RouteAsk.put(question(origin))) {
            is RouteVerdict.Handover -> {
                locker.target = verdict.url
                locker.targetExpiry = verdict.expiresAt
                toPage(verdict.url)
            }

            else -> if (!saved.isNullOrBlank()) {
                Echo.note(TAG, "no fresh answer → the page this install already had")
                toPage(saved)
            } else {
                Echo.note(TAG, "no answer and nothing saved → no-signal board")
                handOver {
                    startActivity(Intent(this, LinkLostDeck::class.java))
                    finish()
                    handOverFlat()
                }
            }
        }
    }

    /**
     * Whether this origin names a real paid source, and so may settle the
     * install as native for good.
     *
     * A first-launch conversion frequently arrives as `Organic` before the
     * click has been matched — the false organic — and an empty map arrives
     * when the SDK was started with no link. Neither is a statement about the
     * user, so neither may lock the lane. `Non-organic`, or any concrete
     * media source that is not itself "organic", is.
     */
    private fun originIsPaid(origin: Map<String, Any?>): Boolean {
        if (origin.isEmpty()) return false
        val status = origin["af_status"]?.toString()?.trim()
        if (status.equals("Non-organic", ignoreCase = true)) return true
        val source = origin["media_source"]?.toString()?.trim()
        return !source.isNullOrEmpty() && !source.equals("organic", ignoreCase = true)
    }

    private suspend fun askWhereFrom(limitMs: Long): Map<String, Any?> {
        val tracker = (application as PulseApp).tracker
        tracker.ignite(this)
        tracker.askAgainIfEmpty(this)
        return tracker.awaitOrigin(limitMs)
    }

    private suspend fun question(origin: Map<String, Any?>) =
        (application as PulseApp).tracker.composeQuestion(
            origin = origin,
            pushToken = locker.messagingToken ?: currentToken()?.also { locker.messagingToken = it }
        )

    /**
     * A link that is merely slow to come up is worth a short wait; one that is
     * not there at all is not. Nothing is started and nothing is written down
     * on the way out — the install is still undecided, and an undecided install
     * is not an organic one.
     */
    private suspend fun waitForLink(firstEver: Boolean): Boolean {
        if (link.up()) return true

        val restored = withTimeoutOrNull(Bylaw.Wait.connectionGrace) {
            link.changes.first { it }
        }
        if (restored == true) return true

        val comeBackTo = if (!firstEver && locker.targetUsable()) locker.target else null
        startActivity(
            Intent(this, LinkLostDeck::class.java).apply {
                if (!comeBackTo.isNullOrBlank()) putExtra(LinkLostDeck.EXTRA_RETURN_URL, comeBackTo)
            }
        )
        finish()
        handOverFlat()
        return false
    }

    /** Legacy by the SDK's reckoning, and what the backend still asks for. */
    @Suppress("DEPRECATION")
    private suspend fun currentToken(): String? = withTimeoutOrNull(TOKEN_WAIT_MS) {
        suspendCancellableCoroutine { waiter ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (waiter.isActive) waiter.resume(if (task.isSuccessful) task.result else null)
            }
        }
    }

    /**
     * A fire-and-forget priming of the messaging token so the value is in the
     * locker before anything needs it. Ordinarily [onNewToken] is what puts a
     * token in the locker, but the SDK only calls it when a token first
     * appears — and on a first launch whose network only came up a moment ago
     * the SDK is still on the way to fetching one when the gate question is
     * being composed. The gate call will still block briefly on the same
     * future through [currentToken], but this warms the value ahead of time
     * so the second launch never has to wait.
     *
     * Failures — no Firebase, no Play services, still offline — are simply
     * dropped: the launch decision is not blocked by a missing token, and
     * push targeting for this install will resolve on the next launch that
     * finds a signal.
     */
    @Suppress("DEPRECATION")
    private fun warmMessagingToken() {
        runCatching {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) return@addOnCompleteListener
                val fresh = task.result ?: return@addOnCompleteListener
                if (locker.messagingToken == fresh) return@addOnCompleteListener
                locker.messagingToken = fresh
                Echo.note(TAG, "messaging token primed")
            }
        }.onFailure { Echo.odd(TAG, "messaging token could not be primed: ${it.javaClass.simpleName}") }
    }

    // ── Hand-over ───────────────────────────────────────────────────────────

    /** The bar runs out to the end first; nobody is moved on by a bar stuck at 70%. */
    private fun handOver(go: () -> Unit) {
        val board = curtain
        if (board == null) go() else board.finishOff { if (!isFinishing) go() }
    }

    private fun toGame() = handOver {
        startActivity(
            Intent(this, HubActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun toPage(url: String) = handOver {
        val next = if (locker.promptIsDue()) {
            Intent(this, PermitDeck::class.java).putExtra(PermitDeck.EXTRA_ONWARD_URL, url)
        } else {
            Intent(this, WebDeck::class.java).putExtra(WebDeck.EXTRA_PAGE_URL, url)
        }
        startActivity(next.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    // ── Push arriving at the launcher ───────────────────────────────────────

    /**
     * The URL a notification tap carried, in either shape it can arrive in.
     *
     * A data-only message reaches our own service, which builds the tap intent
     * with the extras named below. A message carrying a `notification` block is
     * drawn by the Firebase SDK whenever the app is not in the foreground — that
     * path never runs our service, and the tap opens the launcher with the data
     * payload attached as plain string extras instead. Reading only our own
     * extras is how a pushed link gets dropped while foreground tests pass.
     */
    private fun pushTargetIn(intent: Intent): String? {
        val ours = if (intent.getBooleanExtra(EXTRA_CAME_FROM_PUSH, false)) {
            intent.getStringExtra(EXTRA_PUSH_TARGET)
        } else null
        val raw = PAYLOAD_KEYS.firstNotNullOfOrNull { intent.getStringExtra(it)?.takeIf { v -> v.isNotBlank() } }
        return (ours ?: raw)?.trim()?.takeIf { it.isNotEmpty() && HostGate.permits(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val pushed = pushTargetIn(intent) ?: return
        when (locker.lane) {
            Locker.Lane.GAME -> Echo.note(TAG, "push tapped by a settled install — the game stays")

            Locker.Lane.STREAM -> {
                if (LiveLink.offer(pushed)) {
                    finish()
                    return
                }
                val saved = locker.target?.takeIf { locker.targetUsable() }
                startActivity(
                    Intent(this, WebDeck::class.java)
                        .putExtra(WebDeck.EXTRA_PAGE_URL, saved ?: pushed)
                        .putExtra(WebDeck.EXTRA_PUSH_URL, pushed)
                        .putExtra(WebDeck.EXTRA_PUSH_WARM, true)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                finish()
            }

            Locker.Lane.UNSET -> locker.parkedPush = pushed
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CAME_FROM_PUSH = "came_from_push"
        const val EXTRA_PUSH_TARGET = "push_target"

        private const val TAG = "MainActivity"

        /**
         * Keys the messaging SDK forwards verbatim when it drew the card itself.
         * Listed from most to least common so the first non-null wins.
         */
        private val PAYLOAD_KEYS = listOf("url", "link", "deep_link", "target_url", "redirect_url")

        private const val TOKEN_WAIT_MS = 5_000L
    }
}
