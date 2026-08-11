package com.coinpulse.coinpulsegame.deck

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.coinpulse.coinpulsegame.charter.Echo

/**
 * The shared look of the three full-screen boards — the splash, the no-signal
 * board and the notification board.
 *
 * Everything is built in code rather than inflated, for one practical reason:
 * the artwork is a full-bleed painting, and the only thing on top of it is a
 * row of buttons that has to sit dead centre. A layout file would add a second
 * place where that centring can be broken.
 */
internal object DeckArt {

    enum class Tone { GOLD, SHADOW }

    // Sampled from the artwork so the controls belong to the picture rather
    // than sitting on it.
    private const val GOLD_LIGHT = 0xFFFFE58A.toInt()
    private const val GOLD_MID = 0xFFF7C23C.toInt()
    private const val GOLD_DEEP = 0xFFD9880F.toInt()
    private const val GOLD_EDGE = 0xFFFFF0BE.toInt()
    private const val GOLD_TEXT = 0xFFFFD75A.toInt()
    private const val INK = 0xFF2B1400.toInt()
    private const val SHADOW_FILL = 0xD2160B2E.toInt()

    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    fun landscape(context: Context): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /**
     * The board's artwork, cropped to fill.
     *
     * Centre-crop is what keeps the buttons honest: it maps the picture's
     * horizontal centre onto the window's, so a row centred in the window is
     * also centred under the plate that was painted in the middle of the image.
     */
    fun backdrop(context: Context, @DrawableRes upright: Int, @DrawableRes wide: Int): ImageView {
        val art = if (landscape(context)) wide else upright
        return ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.BLACK)
            val bitmap = decodeToScreen(context, art)
            if (bitmap != null) setImageBitmap(bitmap) else setImageResource(art)
        }
    }

    /**
     * Decodes the board at roughly the size it will be drawn at. The source
     * files are 1080×2400, and handing that to a 720p device at full resolution
     * costs about ten megabytes of heap for pixels it will immediately throw
     * away.
     */
    private fun decodeToScreen(context: Context, @DrawableRes art: Int): Bitmap? {
        val metrics: DisplayMetrics = context.resources.displayMetrics
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(context.resources, art, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var step = 1
            while (bounds.outWidth / (step * 2) >= metrics.widthPixels &&
                bounds.outHeight / (step * 2) >= metrics.heightPixels
            ) {
                step *= 2
            }

            BitmapFactory.decodeResource(
                context.resources, art,
                BitmapFactory.Options().apply {
                    inSampleSize = step
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
            )
        }.onFailure { Echo.odd(TAG, "board artwork would not decode: ${it.javaClass.simpleName}") }
            .getOrNull()
    }

    /**
     * A group of buttons, arranged for the current orientation.
     *
     * Landscape lays them out horizontally with equal sizing so they read as a
     * pair rather than a primary and an afterthought. Portrait stacks them —
     * the accept action above the skip — because a phone held upright has more
     * height than width to give the controls, and side-by-side buttons on that
     * canvas end up too narrow to hold their labels honestly.
     *
     * It is deliberately given no window insets. On a landscape screen the
     * cutout lives on one side only, so padding the row by the safe insets
     * would move its centre away from the picture's — which reads, correctly,
     * as buttons that do not line up with the plate above them. Nothing in the
     * group comes near an edge, so there is nothing for the notch to cover.
     */
    fun controlLane(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation =
            if (landscape(context)) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        clipChildren = false
    }

    /**
     * A gap sized for whichever axis controlLane is running on. Horizontal in
     * landscape (a fixed-width strut between two buttons in a row), vertical in
     * portrait (a fixed-height gutter between two stacked buttons).
     */
    fun gap(context: Context, dp: Float): View = View(context).apply {
        layoutParams = if (landscape(context)) {
            LinearLayout.LayoutParams(dp(context, dp), 1)
        } else {
            LinearLayout.LayoutParams(1, dp(context, dp))
        }
    }

    /**
     * A button in the board's own vocabulary: gold plate for the action that
     * moves the user forward, dark glass with a gold rim for the one that steps
     * around it.
     *
     * The second tone exists because a plain light label on this artwork is
     * invisible — the background is bright, busy and gold in places, and a
     * "skip" the user cannot find is a "skip" they never press.
     */
    fun key(context: Context, label: String, tone: Tone): TextView = TextView(context).apply {
        text = label
        isAllCaps = true
        gravity = Gravity.CENTER
        includeFontPadding = false
        setTypeface(Typeface.DEFAULT_BOLD)
        letterSpacing = 0.07f
        textSize = 15.5f
        isClickable = true
        isFocusable = true
        background = plate(context, tone)

        if (tone == Tone.GOLD) {
            setTextColor(INK)
            setShadowLayer(dp(context, 1f).toFloat(), 0f, dp(context, 1f).toFloat(), 0x66FFFFFF)
        } else {
            setTextColor(GOLD_TEXT)
            setShadowLayer(dp(context, 2.5f).toFloat(), 0f, 0f, Color.BLACK)
        }
        elevation = dp(context, 6f).toFloat()
        pressFeedback(this)
    }

    private fun plate(context: Context, tone: Tone): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(context, 16f).toFloat()
        if (tone == Tone.GOLD) {
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            colors = intArrayOf(GOLD_LIGHT, GOLD_MID, GOLD_DEEP)
            setStroke(dp(context, 2f), GOLD_EDGE)
        } else {
            setColor(SHADOW_FILL)
            setStroke(dp(context, 2f), GOLD_MID)
        }
    }

    private fun pressFeedback(view: View) {
        view.setOnTouchListener { target, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> target.animate()
                    .scaleX(0.96f).scaleY(0.96f).alpha(0.9f).setDuration(70L).start()

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> target.animate()
                    .scaleX(1f).scaleY(1f).alpha(1f).setDuration(110L).start()
            }
            false
        }
    }

    private const val TAG = "DeckArt"
}
