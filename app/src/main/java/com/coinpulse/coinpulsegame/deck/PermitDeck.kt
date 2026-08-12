package com.coinpulse.coinpulsegame.deck

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.coinpulse.coinpulsegame.R
import com.coinpulse.coinpulsegame.strongbox.Locker

/**
 * The board that asks for notifications, shown once the launch has decided on
 * the WebView and before the page appears.
 *
 * Neither this board nor the no-signal one applies window insets. The artwork is
 * centre-cropped, which puts the picture's centre line on the window's, and the
 * controls are centred in the window — so they land under the plate that was
 * painted in the middle of the image. Padding the row by the safe insets would
 * shift it by the width of a side cutout, and on a landscape screen that
 * cutout is on one side only: the row would visibly sit off-centre against the
 * plate above it. Nothing here goes near an edge, so there is nothing the notch
 * can cover.
 */
class PermitDeck : AppCompatActivity() {

    private lateinit var locker: Locker
    private var onwardUrl: String? = null

    private val ask = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        when {
            granted -> locker.promptGranted = true
            // A refusal the system will not show again is permanent (two "Don't
            // allow" taps on Android 13+, or the user hitting the app-info
            // switch): fix the answer forever.
            !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) ->
                locker.promptRefusedForGood = true

            // A soft "not now" from the OS dialog is treated the same as tapping
            // Skip on this board: quiet for a few days and then the deck offers
            // the choice again. Without this the screen would come back on the
            // very next entry and that reads as a nag.
            else -> locker.snoozePrompt()
        }
        onward()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locker = Locker(applicationContext)
        onwardUrl = intent.getStringExtra(EXTRA_ONWARD_URL)

        val board = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = false
        }

        board.addView(
            DeckArt.backdrop(this, R.drawable.pulse_permit_up, R.drawable.pulse_permit_wide),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val lane = DeckArt.controlLane(this)
        val accept = DeckArt.key(this, "Allow", DeckArt.Tone.GOLD).apply {
            setOnClickListener { onAllow() }
        }
        val skip = DeckArt.key(this, "Skip", DeckArt.Tone.SHADOW).apply {
            // Skip quiets the deck for a snooze window (a few days, per the
            // build's ASK_AGAIN_SEC). Showing it on every entry the way an
            // "always ask" would is the sort of nag that trains users to
            // reflex-skip past the actual system dialog next time.
            setOnClickListener {
                locker.snoozePrompt()
                onward()
            }
        }
        // The two buttons carry the same weight in this decision, so they sit
        // at the same size and the accept comes first — above the skip on a
        // stack, to the left of it in a row.
        lane.addView(accept, keySize())
        lane.addView(DeckArt.gap(this, GAP_DP))
        lane.addView(skip, keySize())

        board.addView(lane, rowPlacement())
        setContentView(board)

        EdgeFit.immerse(this)
        EdgeFit.spanCutout(this)
    }

    private fun onAllow() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Before Android 13 the permission is granted at install time, so
            // there is nothing to ask and the answer is already yes.
            locker.promptGranted = true
            onward()
            return
        }
        val already = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        if (already == PackageManager.PERMISSION_GRANTED) {
            locker.promptGranted = true
            onward()
        } else {
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Straight to the shell, never back through the launcher: the splash has had
     * its one showing for this launch, and bringing it back after a permission
     * dialog reads as the app restarting itself.
     */
    private fun onward() {
        startActivity(
            Intent(this, WebDeck::class.java).apply {
                onwardUrl?.let { putExtra(WebDeck.EXTRA_PAGE_URL, it) }
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
        finish()
        handOverFlat()
    }

    // Landscape stays exactly as it was — the row of 168×54 buttons at 11 % from
    // the floor lines up with the plate above it and reads as designed. Portrait
    // is the only case that changed: both stacked buttons grow to 200×60 and
    // the stack itself is lifted well above the bottom edge so it clears the
    // reels underneath instead of overlapping their glyphs.
    private fun keySize() = android.widget.LinearLayout.LayoutParams(
        DeckArt.dp(this, if (DeckArt.landscape(this)) 168f else 200f),
        DeckArt.dp(this, if (DeckArt.landscape(this)) 54f else 60f)
    )

    private fun rowPlacement(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            val screenHeight = resources.displayMetrics.heightPixels
            val ratio =
                if (DeckArt.landscape(this@PermitDeck)) 0.11f else 0.13f
            bottomMargin = (screenHeight * ratio).toInt()
        }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The board is a different painting in each orientation, and the row
        // sits at a different height above the floor in each.
        recreate()
    }

    companion object {
        const val EXTRA_ONWARD_URL = "onward_url"
        private const val GAP_DP = 16f
    }
}
