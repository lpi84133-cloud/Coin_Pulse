package com.coinpulse.coinpulsegame.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.Choreographer
import android.view.View
import com.coinpulse.coinpulsegame.App
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animated menu background: deep gradient, breathing pulse rings and slowly
 * drifting game sprites. Hardware accelerated, sprites are pre-scaled once.
 */
class Backdrop(context: Context) : View(context) {

    private class Item(
        val bmp: Bitmap, var x: Float, var y: Float,
        val vx: Float, val vy: Float, var rot: Float, val rotSpeed: Float, val alpha: Int,
    )

    private val items = ArrayList<Item>()
    private val rnd = Random(42)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var time = 0f
    private var lastFrame = 0L
    private var w = 0f
    private var h = 0f

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAttachedToWindow) return
            val dt = if (lastFrame == 0L) 0.016f
            else ((frameTimeNanos - lastFrame) / 1_000_000_000f).coerceAtMost(0.05f)
            lastFrame = frameTimeNanos
            time += dt
            step(dt)
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrame = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(nw: Int, nh: Int, ow: Int, oh: Int) {
        w = nw.toFloat(); h = nh.toFloat()
        if (w <= 0f || h <= 0f) return

        bgPaint.shader = LinearGradient(0f, 0f, 0f, h,
            intArrayOf(C.bgViolet, C.bgMid, C.bgDeep), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        glowPaint.shader = RadialGradient(w * 0.5f, h * 0.15f, h * 0.75f,
            intArrayOf(0x668A5CF6, 0x00000000), null, Shader.TileMode.CLAMP)
        vignettePaint.shader = RadialGradient(w * 0.5f, h * 0.5f, maxOf(w, h) * 0.62f,
            intArrayOf(0x00000000, 0xAA000000.toInt()), floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP)
        ringPaint.strokeWidth = D.dpf(context, 1.5f)

        buildItems()
    }

    private fun buildItems() {
        items.clear()
        if (!App.assetsReady) return
        val a = App.assets
        val pool = ArrayList<Bitmap>()
        a.coins.firstOrNull()?.let { pool.add(it) }
        a.crystals.firstOrNull()?.let { pool.add(it) }
        a.stars.firstOrNull()?.let { pool.add(it) }
        a.bells.firstOrNull()?.let { pool.add(it) }
        a.fruits.firstOrNull()?.let { pool.add(it) }
        if (pool.isEmpty()) return

        val min = minOf(w, h)
        repeat(12) {
            val src = pool[rnd.nextInt(pool.size)]
            val size = (min * (0.055f + rnd.nextFloat() * 0.055f)).toInt().coerceAtLeast(8)
            val scaled = Bitmap.createScaledBitmap(src, size, size, true)
            items.add(
                Item(
                    bmp = scaled,
                    x = rnd.nextFloat() * w,
                    y = rnd.nextFloat() * h,
                    vx = (rnd.nextFloat() - 0.5f) * min * 0.02f,
                    vy = -min * (0.008f + rnd.nextFloat() * 0.016f),
                    rot = rnd.nextFloat() * 360f,
                    rotSpeed = (rnd.nextFloat() - 0.5f) * 22f,
                    alpha = 45 + rnd.nextInt(55),
                )
            )
        }
    }

    private fun step(dt: Float) {
        for (i in items) {
            i.x += i.vx * dt
            i.y += i.vy * dt
            i.rot += i.rotSpeed * dt
            val pad = i.bmp.width.toFloat()
            if (i.y < -pad) { i.y = h + pad; i.x = rnd.nextFloat() * w }
            if (i.x < -pad) i.x = w + pad
            if (i.x > w + pad) i.x = -pad
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (w <= 0f) return
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        canvas.drawRect(0f, 0f, w, h, glowPaint)

        // breathing pulse rings from the centre
        val base = minOf(w, h)
        for (k in 0 until 3) {
            val phase = (time * 0.22f + k * 0.333f) % 1f
            val r = base * (0.12f + phase * 0.55f)
            val alpha = ((1f - phase) * 70f).toInt().coerceIn(0, 255)
            ringPaint.color = (alpha shl 24) or (C.cyan and 0x00FFFFFF)
            canvas.drawCircle(w * 0.5f, h * 0.52f, r, ringPaint)
        }

        // drifting sprites
        for (i in items) {
            spritePaint.alpha = i.alpha
            canvas.save()
            canvas.translate(i.x, i.y + sin(time * 0.8f + i.x) * base * 0.006f)
            canvas.rotate(i.rot)
            canvas.drawBitmap(i.bmp, -i.bmp.width / 2f, -i.bmp.height / 2f, spritePaint)
            canvas.restore()
        }
        spritePaint.alpha = 255

        canvas.drawRect(0f, 0f, w, h, vignettePaint)
    }
}
