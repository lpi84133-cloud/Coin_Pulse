package com.coinpulse.coinpulsegame.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

/**
 * Pre-scales bitmaps to the exact pixel size they are drawn at, once.
 * The previous renderer rescaled every sprite (and a 1280px background) on every
 * frame, which is what made the game stutter.
 */
class SpriteCache {

    private val cache = HashMap<Long, Bitmap>()

    fun scaled(src: Bitmap, targetWidth: Int): Bitmap {
        val w = targetWidth.coerceAtLeast(2)
        val h = (w * src.height.toFloat() / src.width).toInt().coerceAtLeast(2)
        val key = (System.identityHashCode(src).toLong() shl 20) or w.toLong()
        cache[key]?.let { return it }
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        cache[key] = scaled
        return scaled
    }

    /**
     * Bakes the arena background into a single screen-sized bitmap with darkening
     * and a vignette, so each frame is one flat drawBitmap call.
     */
    fun bakeBackground(src: Bitmap?, w: Int, h: Int, fallbackColor: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

        if (src != null) {
            // cover-crop the source into the target
            val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
            val dw = src.width * scale
            val dh = src.height * scale
            val left = (w - dw) / 2f
            val top = (h - dh) / 2f
            canvas.save()
            canvas.translate(left, top)
            canvas.scale(scale, scale)
            canvas.drawBitmap(src, 0f, 0f, paint)
            canvas.restore()
        } else {
            canvas.drawColor(fallbackColor)
        }

        // darken so gameplay sprites read clearly against the art
        paint.shader = null
        paint.color = 0x99070518.toInt()
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        // subtle top-down light + vignette
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            intArrayOf(0x33000000, 0x00000000), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        paint.shader = RadialGradient(w / 2f, h / 2f, maxOf(w, h) * 0.62f,
            intArrayOf(0x00000000, 0xB0000000.toInt()), floatArrayOf(0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        return out
    }

    fun clear() {
        cache.clear()
    }
}
