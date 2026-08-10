package com.coinpulse.coinpulsegame.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import com.coinpulse.coinpulsegame.App
import com.coinpulse.coinpulsegame.ui.C
import com.coinpulse.coinpulsegame.ui.F
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Honest loading screen: the bar is driven by [Assets.load], which ticks once per
 * real decode/preload task, so 100% is only reached when everything is in memory.
 * Adapts to both portrait and landscape.
 */
@SuppressLint("ViewConstructor")
class LoadingScreen(context: Context) : View(context) {

    private val progress = MutableStateFlow(0f)
    private var shown = 0f
    private var time = 0f
    private var label = "Loading"

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = F.title
        textAlign = Paint.Align.CENTER
    }
    private val track = RectF()

    fun start(scope: CoroutineScope, onDone: () -> Unit) {
        scope.launch {
            App.assets.load { done, total -> progress.value = done.toFloat() / total }
            App.assetsReady = true
        }
        scope.launch {
            while (shown < 1f || progress.value < 1f) {
                val target = progress.value
                shown += (target - shown) * 0.22f
                if (target >= 1f && shown > 0.995f) shown = 1f
                label = if (target < 1f) "Loading ${(target * 100).toInt()}%" else "Ready"
                time += 0.016f
                invalidate()
                delay(16)
            }
            shown = 1f
            invalidate()
            delay(220)
            onDone()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (w <= 0 || h <= 0) return
        bg.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            intArrayOf(C.bgViolet, C.bgMid, C.bgDeep), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        glow.shader = RadialGradient(w / 2f, h * 0.42f, min(w, h) * 0.7f,
            intArrayOf(0x558A5CF6, 0x00000000), null, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val md = min(w, h)
        if (w <= 0f) return

        canvas.drawRect(0f, 0f, w, h, bg)
        canvas.drawRect(0f, 0f, w, h, glow)

        val cx = w / 2f
        val cy = h * 0.40f

        // breathing rings behind the title
        for (k in 0 until 3) {
            val phase = (time * 0.35f + k * 0.33f) % 1f
            stroke.strokeWidth = md * 0.006f
            stroke.color = (((1f - phase) * 90f).toInt().coerceIn(0, 255) shl 24) or (C.cyan and 0xFFFFFF)
            canvas.drawCircle(cx, cy, md * (0.14f + phase * 0.30f), stroke)
        }

        // title
        text.textSize = md * 0.13f
        text.color = C.gold
        text.setShadowLayer(md * 0.02f, 0f, md * 0.006f, 0xAA000000.toInt())
        canvas.drawText("COIN", cx, cy - md * 0.01f, text)
        text.color = C.cyan
        canvas.drawText("PULSE", cx, cy + md * 0.125f, text)
        text.clearShadowLayer()

        // progress track
        val barW = w * 0.55f
        val barH = md * 0.032f
        val left = cx - barW / 2f
        val top = h * 0.78f
        track.set(left, top, left + barW, top + barH)
        fill.shader = null
        fill.color = 0x66100C2E
        canvas.drawRoundRect(track, barH, barH, fill)
        stroke.strokeWidth = md * 0.004f
        stroke.color = C.panelBorder
        canvas.drawRoundRect(track, barH, barH, stroke)

        // fill left -> right
        val inner = barH * 0.18f
        val fw = (barW - inner * 2) * shown.coerceIn(0f, 1f)
        if (fw > 1f) {
            track.set(left + inner, top + inner, left + inner + fw, top + barH - inner)
            fill.shader = LinearGradient(left, top, left + barW, top,
                intArrayOf(C.cyan, C.gold), null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(track, barH, barH, fill)
            fill.shader = null
        }

        text.textSize = md * 0.032f
        text.color = C.textDim
        canvas.drawText(label, cx, top + barH + md * 0.075f, text)
    }
}
