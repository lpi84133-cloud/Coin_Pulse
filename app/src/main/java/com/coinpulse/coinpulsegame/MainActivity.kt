package com.coinpulse.coinpulsegame

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.coinpulse.coinpulsegame.BuildConfig
import com.coinpulse.coinpulsegame.pulsegate.PulseApp
import com.coinpulse.coinpulsegame.charter.Charter
import com.coinpulse.coinpulsegame.charter.Echo
import com.coinpulse.coinpulsegame.charter.HostGate
import com.coinpulse.coinpulsegame.charter.RouteVerdict
import com.coinpulse.coinpulsegame.deck.BootCurtain
import com.coinpulse.coinpulsegame.deck.EdgeFit
import com.coinpulse.coinpulsegame.deck.handOverFlat
import com.coinpulse.coinpulsegame.deck.LinkLostDeck
import com.coinpulse.coinpulsegame.deck.PermitDeck
import com.coinpulse.coinpulsegame.deck.WebDeck
import com.coinpulse.coinpulsegame.dispatchbox.LiveLink
import com.coinpulse.coinpulsegame.relaynet.LinkWatch
import com.coinpulse.coinpulsegame.relaynet.RouteAsk
import com.coinpulse.coinpulsegame.strongbox.Locker
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
 *       a page      → remember the lane, then the page
 *       a refusal   → the game, and the lane is only written down when the
 *                     backend actually answered *and* the question carried a
 *                     real origin. Anything else leaves the decision open for
 *                     the next launch.
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
        val forced = Charter.forcedUrl
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

        val origin = askWhereFrom(Charter.Wait.coldAttribution)
        when (val verdict = RouteAsk.put(question(origin))) {
            is RouteVerdict.Handover -> {
                locker.lane = Locker.Lane.STREAM
                locker.target = verdict.url
                locker.targetExpiry = verdict.expiresAt
                toPage(verdict.url)
            }

            RouteVerdict.Refused -> {
                // A refusal sticks for the life of the install, so it has to be
                // earned. "The backend answered" is not enough on its own: a
                // question with no real source behind it — an empty conversion,
                // or the false "Organic" AppsFlyer hands back on a first launch
                // before the click is matched — gets a 404 that is about the
                // question, not about the user. Locking on that is exactly how a
                // paid OneLink install ends up in the game for good. So the lane
                // is only fixed when a real, paid source was actually reported.
                if (originIsPaid(origin)) {
                    locker.lane = Locker.Lane.GAME
                    Echo.note(TAG, "backend refused a paid install → game, and it sticks")
                } else {
                    Echo.note(TAG, "no paid attribution behind the refusal → game, decision left open")
                }
                toGame()
            }

            RouteVerdict.Unheard -> {
                Echo.note(TAG, "nobody answered → game for now, decision left open")
                toGame()
            }
        }
    }

    private suspend fun returningRun() {
        if (!waitForLink(firstEver = false)) return

        val saved = if (locker.targetUsable()) locker.target else null
        val origin = askWhereFrom(Charter.Wait.warmAttribution)

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

        val restored = withTimeoutOrNull(Charter.Wait.connectionGrace) {
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

    // ── Hand-over ───────────────────────────────────────────────────────────

    /** The bar runs out to the end first; nobody is moved on by a bar stuck at 70%. */
    private fun handOver(go: () -> Unit) {
        val board = curtain
        if (board == null) go() else board.finishOff { if (!isFinishing) go() }
    }

    private fun toGame() = handOver {
        startActivity(
            Intent(this, GameActivity::class.java)
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
        val raw = intent.getStringExtra(PAYLOAD_URL) ?: intent.getStringExtra(PAYLOAD_LINK)
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

        /** Keys the messaging SDK forwards verbatim when it drew the card itself. */
        private const val PAYLOAD_URL = "url"
        private const val PAYLOAD_LINK = "link"

        private const val TOKEN_WAIT_MS = 5_000L
    }
}
