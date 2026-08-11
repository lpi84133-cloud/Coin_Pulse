package com.coinpulse.coinpulsegame.deck

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.coinpulse.coinpulsegame.R
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * The board shown while the launch decides where to go.
 *
 * The bar is deliberately not on a timer. A launch takes as long as the network
 * takes, and a bar tied to a fixed duration either reaches the end and sits
 * there or gets cut off part-way when routing finishes early — the version of
 * this that shipped once jumped to the next screen at sixty per cent. Instead
 * it eases towards ninety-odd per cent and waits; [finishOff] is what carries
 * it the rest of the way, and only then does the next screen appear.
 *
 * There is exactly one of these per launch. It must not come back afterwards —
 * not after the notification board, not for a push arriving at a live shell.
 */
internal class BootCurtain(context: Context) : View(context) {

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letters = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private var art: Bitmap? = null
    private var artIsWide = false
    private val source = Rect()
    private val destination = RectF()

    private var openedAt = 0L
    private var closingFrom = 0f
    private var closingAt = 0L
    private var handOver: (() -> Unit)? = null
    private var handedOver = false
    private var shown = 0f

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        openedAt = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        val wide = w > h
        if (art == null || wide != artIsWide) loadArt(wide)
    }

    override fun onDetachedFromWindow() {
        art = null
        super.onDetachedFromWindow()
    }

    /**
     * Routing is done. Run the bar out, hold long enough for the eye to see it
     * full, then hand over. Calling this twice changes nothing.
     */
    fun finishOff(next: () -> Unit) {
        if (closingAt != 0L) return
        handOver = next
        closingFrom = shown
        closingAt = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        paintArt(canvas, w, h)

        val elapsed = SystemClock.uptimeMillis() - openedAt
        shown = if (closingAt == 0L) {
            // Fast at first, then asymptotic: it never claims to be finished
            // while there is still a decision outstanding.
            val seconds = elapsed / 1000f
            min(CEILING, CEILING * (1f - exp(-seconds / 1.15f)))
        } else {
            val run = ((SystemClock.uptimeMillis() - closingAt).toFloat() / CLOSE_MS).coerceIn(0f, 1f)
            closingFrom + (1f - closingFrom) * run
        }

        paintCaption(canvas, w, h, elapsed)
        paintBar(canvas, w, h, shown, elapsed)

        if (shown >= 1f && !handedOver) {
            handedOver = true
            val next = handOver
            handOver = null
            postDelayed({ next?.invoke() }, REST_MS)
            return
        }
        postInvalidateOnAnimation()
    }

    private fun loadArt(wide: Boolean) {
        artIsWide = wide
        art = runCatching {
            val chosen = if (wide) R.drawable.pulse_boot_wide else R.drawable.pulse_boot_up
            val decoded = android.graphics.BitmapFactory.decodeResource(
                resources, chosen,
                android.graphics.BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inSampleSize = sampleFor(chosen)
                }
            )
            decoded
        }.getOrNull()
    }

    private fun sampleFor(resource: Int): Int {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeResource(resources, resource, bounds)
        if (bounds.outWidth <= 0 || width <= 0) return 1
        var step = 1
        while (bounds.outWidth / (step * 2) >= width && bounds.outHeight / (step * 2) >= height) {
            step *= 2
        }
        return step
    }

    /** Centre-crop, computed here so the artwork never stretches on an odd aspect. */
    private fun paintArt(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(BACKDROP)
        val bitmap = art ?: return
        val scale = max(w / bitmap.width, h / bitmap.height)
        val drawnW = bitmap.width * scale
        val drawnH = bitmap.height * scale
        source.set(0, 0, bitmap.width, bitmap.height)
        destination.set(
            (w - drawnW) / 2f,
            (h - drawnH) / 2f,
            (w + drawnW) / 2f,
            (h + drawnH) / 2f
        )
        canvas.drawBitmap(bitmap, source, destination, ink)
    }

    private fun paintCaption(canvas: Canvas, w: Float, h: Float, elapsed: Long) {
        val dots = ".".repeat(((elapsed / 380L) % 4L).toInt())
        letters.textSize = min(h, w) * 0.036f
        letters.color = GOLD
        letters.setShadowLayer(h * 0.006f, 0f, h * 0.003f, Color.BLACK)
        canvas.drawText("Loading$dots", w / 2f, barTop(h) - letters.textSize * 0.75f, letters)
        letters.clearShadowLayer()
    }

    private fun barTop(h: Float) = h * 0.900f

    private fun paintBar(canvas: Canvas, w: Float, h: Float, progress: Float, elapsed: Long) {
        val barW = w * (if (width > height) 0.44f else 0.66f)
        val barH = min(h * 0.020f, dp(14f))
        val left = (w - barW) / 2f
        val top = barTop(h)
        val radius = barH / 2f

        ink.style = Paint.Style.FILL
        ink.shader = null
        ink.color = TRACK
        canvas.drawRoundRect(left, top, left + barW, top + barH, radius, radius, ink)

        if (progress > 0f) {
            val filled = barW * progress
            ink.shader = LinearGradient(
                left, top, left + barW, top, GOLD_DEEP, GOLD, Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(left, top, left + filled, top + barH, radius, radius, ink)
            ink.shader = null

            // A highlight travelling along the filled part, so the bar still
            // reads as alive during a long wait.
            if (filled > barH) {
                val sweepW = barW * 0.2f
                val phase = (elapsed % SWEEP_MS).toFloat() / SWEEP_MS
                val head = left + (filled + sweepW) * phase - sweepW
                val from = head.coerceIn(left, left + filled)
                val to = (head + sweepW).coerceIn(left, left + filled)
                if (to > from) {
                    ink.shader = LinearGradient(
                        from, top, to, top, 0x00FFFFFF, 0x70FFFFFF, Shader.TileMode.CLAMP
                    )
                    canvas.drawRoundRect(from, top, to, top + barH, radius, radius, ink)
                    ink.shader = null
                }
            }
        }

        ink.style = Paint.Style.STROKE
        ink.strokeWidth = dp(1.6f)
        ink.color = GOLD_EDGE
        canvas.drawRoundRect(left, top, left + barW, top + barH, radius, radius, ink)
        ink.style = Paint.Style.FILL
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private companion object {
        const val BACKDROP = 0xFF120A2C.toInt()
        const val GOLD = 0xFFF7C23C.toInt()
        const val GOLD_DEEP = 0xFFD9600F.toInt()
        const val GOLD_EDGE = 0xCCFFE58A.toInt()
        const val TRACK = 0x99000000.toInt()

        /** Where the bar waits while the decision is still open. */
        const val CEILING = 0.93f

        const val CLOSE_MS = 300f
        const val REST_MS = 400L
        const val SWEEP_MS = 1_500L
    }
}
