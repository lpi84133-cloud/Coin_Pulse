package com.coinpulse.coinpulsegame.deck

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.coinpulse.coinpulsegame.MainActivity
import com.coinpulse.coinpulsegame.R
import com.coinpulse.coinpulsegame.charter.Echo
import com.coinpulse.coinpulsegame.pulsegate.EntryGate
import com.coinpulse.coinpulsegame.relaynet.LinkWatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The board for a device with no way out.
 *
 * It is also the first frame of a first launch that begins offline, and that
 * case decides the shape of the whole screen: nothing has been started, nothing
 * has been asked and nothing has been written down, because an install whose
 * origin has never been looked up is not an organic install — it is an
 * undecided one. When the link comes back, the launch is run again from the
 * top rather than patched up in place, and that run is the first time the
 * tracker is asked anything.
 *
 * The game sits behind the second button. It needs no network at all, so a user
 * with no signal is never left with a dead end.
 */
class LinkLostDeck : AppCompatActivity() {

    private lateinit var link: LinkWatch
    private val scope = CoroutineScope(Dispatchers.Main)
    private var retry: TextView? = null
    private var comeBackTo: String? = null
    private var leaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        link = LinkWatch(applicationContext)
        comeBackTo = intent.getStringExtra(EXTRA_RETURN_URL)

        val board = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = false
        }
        board.addView(
            DeckArt.backdrop(this, R.drawable.pulse_lost_up, R.drawable.pulse_lost_wide),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val lane = DeckArt.controlLane(this)
        val again = DeckArt.key(this, "Retry", DeckArt.Tone.GOLD).apply {
            setOnClickListener { attempt() }
        }
        retry = again
        val offline = DeckArt.key(this, "Play offline", DeckArt.Tone.SHADOW).apply {
            setOnClickListener { toGame() }
        }
        // Retry above Play offline on a phone, retry to the left of it in
        // landscape — the two are the same size in both, so they read as a
        // pair with no obvious primary and no obvious secondary.
        lane.addView(again, keySize())
        lane.addView(DeckArt.gap(this, GAP_DP))
        lane.addView(offline, keySize())

        board.addView(lane, rowPlacement())
        setContentView(board)

        EdgeFit.immerse(this)
        EdgeFit.spanCutout(this)

        // The board watches the radio itself and leaves on its own the moment
        // there is something to leave for.
        scope.launch {
            link.changes.collect { up -> if (up) attempt() }
        }
    }

    private fun attempt() {
        if (leaving) return
        retry?.apply {
            text = "Connecting"
            isEnabled = false
        }
        scope.launch {
            if (link.reaches()) {
                leaving = true
                startActivity(onwardIntent())
                finish()
                handOverFlat()
            } else {
                retry?.apply {
                    text = "Retry"
                    isEnabled = true
                }
            }
        }
    }

    /**
     * With a page to go back to, go back to it. With none, this was a launch
     * that never got as far as a decision, so the launcher runs it again in
     * full — patching state here instead would settle the install as organic.
     */
    private fun onwardIntent(): Intent {
        val page = comeBackTo
        return if (!page.isNullOrBlank()) {
            Intent(this, WebDeck::class.java)
                .putExtra(WebDeck.EXTRA_PAGE_URL, page)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        } else {
            Intent(this, EntryGate::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun toGame() {
        Echo.note(TAG, "offline board → game")
        leaving = true
        startActivity(
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
        handOverFlat()
    }

    // "Play offline" is the wider label, so both buttons take the width it
    // needs and nothing has to be re-measured to make them look like a pair.
    private fun keySize() = LinearLayout.LayoutParams(
        DeckArt.dp(this, 196f),
        DeckArt.dp(this, 54f)
    )

    private fun rowPlacement(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            val screenHeight = resources.displayMetrics.heightPixels
            val ratio =
                if (DeckArt.landscape(this@LinkLostDeck)) 0.11f else 0.055f
            bottomMargin = (screenHeight * ratio).toInt()
        }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        recreate()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RETURN_URL = "return_url"
        private const val TAG = "LinkLostDeck"
        private const val GAP_DP = 16f
    }
}
