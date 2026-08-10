package com.coinpulse.coinpulsegame.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.coinpulse.coinpulsegame.ui.C
import com.coinpulse.coinpulsegame.ui.F
import kotlin.math.min
import kotlin.random.Random

/**
 * Draws the engine state. All paints are allocated once and every sprite is
 * pre-scaled through [SpriteCache], so a frame costs no allocations or rescales.
 */
class Renderer(private val engine: Engine, private val assets: Assets) {

    private val sprites = SpriteCache()
    private var background: Bitmap? = null
    private var w = 0f
    private var h = 0f
    private var minDim = 1f
    private val rnd = Random(7)

    // geometry the input layer needs
    var pulseCx = 0f; private set
    var pulseCy = 0f; private set
    var pulseR = 0f; private set
    val pauseRect = RectF()

    // ---- paints (created once) ----
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = F.title
        textAlign = Paint.Align.CENTER
    }
    private val textLeft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = F.title
        textAlign = Paint.Align.LEFT
    }
    private val rect = RectF()
    private val arcRect = RectF()

    fun resize(width: Int, height: Int, backgroundSrc: Bitmap?) {
        w = width.toFloat(); h = height.toFloat()
        minDim = min(w, h)
        sprites.clear()
        background = sprites.bakeBackground(backgroundSrc, width, height, C.bgDeep)

        pulseR = minDim * 0.115f
        pulseCx = w - pulseR - minDim * 0.055f
        pulseCy = h - pulseR - minDim * 0.055f

        val pb = minDim * 0.062f
        pauseRect.set(w - pb - minDim * 0.03f, minDim * 0.03f, w - minDim * 0.03f, minDim * 0.03f + pb)
    }

    fun draw(canvas: Canvas) {
        background?.let { canvas.drawBitmap(it, 0f, 0f, null) } ?: canvas.drawColor(C.bgDeep)

        val shake = engine.shake
        canvas.save()
        if (shake > 0.5f) {
            canvas.translate((rnd.nextFloat() - 0.5f) * shake, (rnd.nextFloat() - 0.5f) * shake)
        }

        drawPortals(canvas)
        drawOrbs(canvas)
        drawChargePreview(canvas)
        drawPulseWaves(canvas)
        drawEnemies(canvas)
        drawPlayer(canvas)
        drawParticles(canvas)
        drawFloaters(canvas)

        canvas.restore()

        if (engine.hitFlash > 0f) {
            fill.color = ((engine.hitFlash * 90f).toInt().coerceIn(0, 255) shl 24) or (C.red and 0xFFFFFF)
            canvas.drawRect(0f, 0f, w, h, fill)
        }

        drawJoystick(canvas)
        drawHud(canvas)
    }

    // ---------- world ----------
    private fun drawPortals(canvas: Canvas) {
        val size = (minDim * 0.10f).toInt()
        val bmp = sprites.scaled(assets.portal, size)
        bmpPaint.alpha = 150
        for (p in engine.portals) {
            canvas.drawBitmap(bmp, p.x - bmp.width / 2f, p.y - bmp.height / 2f, bmpPaint)
        }
        bmpPaint.alpha = 255
    }

    private fun drawOrbs(canvas: Canvas) {
        for (o in engine.orbs) {
            val size = (o.radius * 2.3f).toInt()
            val bmp = sprites.scaled(o.bmp, size)
            val pop = 0.6f + 0.4f * o.scale
            val bob = kotlin.math.sin(o.bob * 3f) * minDim * 0.004f
            canvas.save()
            canvas.translate(o.x, o.y + bob)
            canvas.scale(pop, pop)
            canvas.drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, bmpPaint)
            canvas.restore()
        }
    }

    /** Translucent circle showing exactly what the pulse will cover. */
    private fun drawChargePreview(canvas: Canvas) {
        if (!engine.charging) return
        val r = engine.previewRadius()
        val c = engine.charge
        fill.color = (((18 + 26 * c).toInt()) shl 24) or (C.cyan and 0xFFFFFF)
        canvas.drawCircle(engine.px, engine.py, r, fill)
        stroke.strokeWidth = minDim * 0.006f
        stroke.color = if (c >= 1f) C.gold else C.cyan
        stroke.alpha = 200
        canvas.drawCircle(engine.px, engine.py, r, stroke)
        stroke.alpha = 255
    }

    private fun drawPulseWaves(canvas: Canvas) {
        for (p in engine.waves) {
            val t = (p.r / p.target).coerceIn(0f, 1f)
            val alpha = ((1f - t) * 230f).toInt().coerceIn(0, 255)
            // soft interior
            fill.color = (((1f - t) * 45f).toInt().coerceIn(0, 255) shl 24) or (C.cyan and 0xFFFFFF)
            canvas.drawCircle(p.x, p.y, p.r, fill)
            // leading edge
            stroke.strokeWidth = minDim * 0.012f * (1f - t * 0.5f)
            stroke.color = (alpha shl 24) or (C.gold and 0xFFFFFF)
            canvas.drawCircle(p.x, p.y, p.r, stroke)
            stroke.strokeWidth = minDim * 0.006f
            stroke.color = ((alpha * 0.7f).toInt() shl 24) or (C.cyan and 0xFFFFFF)
            canvas.drawCircle(p.x, p.y, p.r * 0.86f, stroke)
        }
    }

    private fun drawEnemies(canvas: Canvas) {
        for (e in engine.enemies) {
            val size = (e.radius * 2.4f).toInt()
            val bmp = sprites.scaled(e.bmp, size)
            val spawning = e.spawnIn > 0f
            val scale = if (spawning) (1f - e.spawnIn / 0.9f).coerceIn(0.2f, 1f) else 1f

            canvas.save()
            canvas.translate(e.x, e.y)
            canvas.scale(scale, scale)
            if (spawning) bmpPaint.alpha = (scale * 255).toInt()
            canvas.drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, bmpPaint)
            bmpPaint.alpha = 255
            canvas.restore()

            if (spawning) {
                // telegraph ring so the player sees where an enemy is appearing
                stroke.strokeWidth = minDim * 0.004f
                stroke.color = C.red
                stroke.alpha = ((e.spawnIn / 0.9f) * 200f).toInt().coerceIn(0, 255)
                canvas.drawCircle(e.x, e.y, e.radius * (1.4f + e.spawnIn), stroke)
                stroke.alpha = 255
                continue
            }

            if (e.flash > 0f) {
                fill.color = ((e.flash * 170f).toInt().coerceIn(0, 255) shl 24) or 0xFFFFFF
                canvas.drawCircle(e.x, e.y, e.radius * 1.05f, fill)
            }

            if (e.barTimer > 0f && e.kind != EnemyKind.GUARDIAN) {
                val bw = e.radius * 2.1f
                val bh = minDim * 0.008f
                val top = e.y - e.radius * 1.7f
                rect.set(e.x - bw / 2f, top, e.x + bw / 2f, top + bh)
                fill.color = C.black30
                canvas.drawRoundRect(rect, bh, bh, fill)
                rect.right = rect.left + bw * (e.hp / e.maxHp).coerceIn(0f, 1f)
                fill.color = C.red
                canvas.drawRoundRect(rect, bh, bh, fill)
            }
        }
    }

    private fun drawPlayer(canvas: Canvas) {
        val blink = engine.invuln > 0f && ((System.currentTimeMillis() / 90) % 2 == 0L)
        val r = engine.pr

        // charge ring
        stroke.strokeWidth = minDim * 0.008f
        stroke.color = C.white20
        canvas.drawCircle(engine.px, engine.py, r * 1.45f, stroke)
        if (engine.charge > 0f) {
            stroke.color = if (engine.charge >= 1f) C.gold else C.cyan
            arcRect.set(
                engine.px - r * 1.45f, engine.py - r * 1.45f,
                engine.px + r * 1.45f, engine.py + r * 1.45f
            )
            canvas.drawArc(arcRect, -90f, 360f * engine.charge, false, stroke)
        }

        if (!blink) {
            val size = (r * 2.5f).toInt()
            val bmp = sprites.scaled(engine.playerBmp, size)
            canvas.drawBitmap(bmp, engine.px - bmp.width / 2f, engine.py - bmp.height / 2f, bmpPaint)
        }
    }

    private fun drawParticles(canvas: Canvas) {
        for (p in engine.particles) {
            if (!p.active) continue
            val a = ((p.life / p.maxLife) * 255f).toInt().coerceIn(0, 255)
            fill.color = (a shl 24) or (p.color and 0xFFFFFF)
            canvas.drawCircle(p.x, p.y, p.size, fill)
        }
    }

    private fun drawFloaters(canvas: Canvas) {
        for (f in engine.floaters) {
            if (!f.active) continue
            val t = f.life / f.maxLife
            val a = (t * 255f).toInt().coerceIn(0, 255)
            text.textSize = f.size
            text.color = (a shl 24) or (f.color and 0xFFFFFF)
            text.setShadowLayer(minDim * 0.008f, 0f, minDim * 0.003f, C.shadow)
            canvas.drawText(f.text, f.x, f.y, text)
            text.clearShadowLayer()
        }
    }

    // ---------- controls ----------
    private fun drawJoystick(canvas: Canvas) {
        if (!engine.joyActive) return
        val or = engine.joyRadius
        stroke.strokeWidth = minDim * 0.006f
        stroke.color = C.white20
        canvas.drawCircle(engine.joyOx, engine.joyOy, or, stroke)

        val dx = engine.joyX - engine.joyOx
        val dy = engine.joyY - engine.joyOy
        val d = kotlin.math.hypot(dx, dy)
        val clamped = if (d > or) or / d else 1f
        val kx = engine.joyOx + dx * clamped
        val ky = engine.joyOy + dy * clamped

        fill.color = 0x55FFFFFF
        canvas.drawCircle(kx, ky, or * 0.38f, fill)
        stroke.color = C.cyan
        canvas.drawCircle(kx, ky, or * 0.38f, stroke)
    }

    private fun drawHud(canvas: Canvas) {
        drawEnergy(canvas)
        drawScore(canvas)
        drawWaveBar(canvas)
        drawBossBar(canvas)
        drawPauseButton(canvas)
        drawPulseButton(canvas)
        if (engine.awaitingBossKill()) {
            text.textSize = minDim * 0.035f
            text.color = C.red
            canvas.drawText("DEFEAT THE GUARDIAN", w / 2f, h * 0.20f, text)
        }
    }

    private fun drawEnergy(canvas: Canvas) {
        val s = minDim * 0.032f
        val x0 = minDim * 0.035f
        val y0 = minDim * 0.04f
        for (i in 0 until engine.maxEnergy) {
            val filled = i < engine.energy
            rect.set(x0 + i * s * 1.35f, y0, x0 + i * s * 1.35f + s, y0 + s)
            fill.color = if (filled) C.red else 0x552A2350
            canvas.drawRoundRect(rect, s * 0.32f, s * 0.32f, fill)
            if (filled) {
                stroke.strokeWidth = minDim * 0.003f
                stroke.color = 0x88FFFFFF.toInt()
                canvas.drawRoundRect(rect, s * 0.32f, s * 0.32f, stroke)
            }
        }
    }

    private fun drawScore(canvas: Canvas) {
        text.textSize = minDim * 0.062f
        text.color = C.text
        text.setShadowLayer(minDim * 0.01f, 0f, minDim * 0.004f, C.shadow)
        canvas.drawText("${engine.score}", w / 2f, minDim * 0.095f, text)
        text.clearShadowLayer()

        if (engine.combo > 1) {
            text.textSize = minDim * 0.032f
            text.color = C.orange
            canvas.drawText("COMBO x${engine.combo}", w / 2f, minDim * 0.135f, text)
        }
    }

    private fun drawWaveBar(canvas: Canvas) {
        val bw = w * 0.26f
        val bh = minDim * 0.014f
        val left = w / 2f - bw / 2f
        val top = h - bh - minDim * 0.035f

        textLeft.textSize = minDim * 0.028f
        textLeft.color = C.textDim
        canvas.drawText("WAVE ${engine.wave} / ${engine.finalWave}", left, top - minDim * 0.014f, textLeft)

        rect.set(left, top, left + bw, top + bh)
        fill.color = C.black30
        canvas.drawRoundRect(rect, bh, bh, fill)
        val frac = (engine.waveTimer / engine.waveDuration).coerceIn(0f, 1f)
        rect.right = left + bw * frac
        fill.color = C.cyan
        canvas.drawRoundRect(rect, bh, bh, fill)
    }

    private fun drawBossBar(canvas: Canvas) {
        if (!engine.bossAlive) return
        val boss = engine.enemies.firstOrNull { it.kind == EnemyKind.GUARDIAN } ?: return
        val bw = w * 0.42f
        val bh = minDim * 0.022f
        val left = w / 2f - bw / 2f
        val top = minDim * 0.16f

        rect.set(left, top, left + bw, top + bh)
        fill.color = C.black30
        canvas.drawRoundRect(rect, bh / 2f, bh / 2f, fill)
        rect.right = left + bw * (boss.hp / boss.maxHp).coerceIn(0f, 1f)
        fill.color = C.red
        canvas.drawRoundRect(rect, bh / 2f, bh / 2f, fill)
        stroke.strokeWidth = minDim * 0.003f
        stroke.color = C.gold
        rect.right = left + bw
        canvas.drawRoundRect(rect, bh / 2f, bh / 2f, stroke)
    }

    private fun drawPauseButton(canvas: Canvas) {
        fill.color = C.black30
        canvas.drawRoundRect(pauseRect, minDim * 0.014f, minDim * 0.014f, fill)
        fill.color = C.text
        val cx = pauseRect.centerX(); val cy = pauseRect.centerY()
        val bw = pauseRect.width() * 0.11f
        val bh = pauseRect.height() * 0.28f
        canvas.drawRect(cx - bw * 2.2f, cy - bh, cx - bw * 0.4f, cy + bh, fill)
        canvas.drawRect(cx + bw * 0.4f, cy - bh, cx + bw * 2.2f, cy + bh, fill)
    }

    private fun drawPulseButton(canvas: Canvas) {
        val locked = engine.chargeLock > 0f
        // base
        fill.color = if (locked) 0x33FFFFFF else 0x44000000
        canvas.drawCircle(pulseCx, pulseCy, pulseR, fill)

        // charge fill
        if (engine.charging && engine.charge > 0f) {
            arcRect.set(pulseCx - pulseR, pulseCy - pulseR, pulseCx + pulseR, pulseCy + pulseR)
            fill.color = if (engine.charge >= 1f) 0xCCFFC53D.toInt() else 0x9938D6FF.toInt()
            canvas.drawArc(arcRect, -90f, 360f * engine.charge, true, fill)
        }

        stroke.strokeWidth = minDim * 0.007f
        stroke.color = if (engine.charge >= 1f && engine.charging) C.gold else C.white40
        canvas.drawCircle(pulseCx, pulseCy, pulseR, stroke)

        text.textSize = minDim * 0.036f
        text.color = C.text
        val label = if (engine.charge >= 1f && engine.charging) "MAX" else "HOLD"
        canvas.drawText(label, pulseCx, pulseCy + minDim * 0.013f, text)
    }
}
