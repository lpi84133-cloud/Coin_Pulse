package com.coinpulse.coinpulsegame.deck

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Edge-to-edge, bars hidden, and the window allowed to extend into the cutout.
 *
 * Both calls have to come after `setContentView`: the controller is fetched
 * from the decor view, and asking for it before there is one is a null
 * dereference in `onCreate` rather than a missing bar.
 */
internal object EdgeFit {

    fun immerse(activity: Activity) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * Lets the window use the short edges of a cutout screen. Content still has
     * to keep itself out of the notch — that is what the padding in the WebView
     * host is for — but without this the system letterboxes the whole window and
     * the artwork loses a band on every side.
     */
    fun spanCutout(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        activity.window.attributes = activity.window.attributes.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}

/**
 * Hands over to the next screen with no animation at all.
 *
 * The boards are full-bleed artwork on black, and one sliding over another
 * reads as a flicker rather than as motion. Suppressed rather than migrated:
 * the newer per-activity call sets the animation of the activity it is called
 * on, which is the wrong end of the handover for three of these sites.
 */
@Suppress("DEPRECATION")
internal fun Activity.handOverFlat() {
    overridePendingTransition(0, 0)
}
