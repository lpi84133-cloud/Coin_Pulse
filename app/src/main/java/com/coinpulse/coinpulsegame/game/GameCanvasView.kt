package com.coinpulse.coinpulsegame.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import com.coinpulse.coinpulsegame.App
import kotlin.math.hypot

/**
 * Hardware-accelerated game surface. A Choreographer callback drives update+draw
 * on the main thread in step with the display, which removed both the stutter and
 * the thread races of the previous SurfaceView implementation.
 */
@SuppressLint("ViewConstructor")
class GameCanvasView(
    context: Context,
    val engine: Engine,
    private val onPauseTapped: () -> Unit,
) : View(context) {

    private val renderer = Renderer(engine, App.assets)
    private var lastFrame = 0L
    private var joyPointer = -1
    private var pulsePointer = -1

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAttachedToWindow) return
            val dt = if (lastFrame == 0L) 0.016f
            else ((frameTimeNanos - lastFrame) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            lastFrame = frameTimeNanos
            engine.update(dt)
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        keepScreenOn = true
        isFocusable = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrame = 0L
        Choreographer.getInstance().postFrameCallback(frame)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frame)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        engine.resize(w, h)
        renderer.resize(w, h, App.assets.backgrounds[App.store.arena().bg])
    }

    override fun onDraw(canvas: Canvas) {
        renderer.draw(canvas)
    }

    // ---------- input ----------
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val id = event.getPointerId(idx)
                val x = event.getX(idx); val y = event.getY(idx)

                if (renderer.pauseRect.contains(x, y)) {
                    App.sound.play("button")
                    onPauseTapped()
                    return true
                }
                if (hypot(x - renderer.pulseCx, y - renderer.pulseCy) <= renderer.pulseR * 1.3f) {
                    pulsePointer = id
                    engine.pulseDown()
                    return true
                }
                if (joyPointer == -1) {
                    joyPointer = id
                    engine.joystickDown(x, y)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (joyPointer != -1) {
                    val pi = event.findPointerIndex(joyPointer)
                    if (pi != -1) engine.joystickMove(event.getX(pi), event.getY(pi))
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = event.getPointerId(event.actionIndex)
                if (id == pulsePointer) {
                    pulsePointer = -1
                    engine.pulseUp()
                }
                if (id == joyPointer) {
                    joyPointer = -1
                    engine.joystickUp()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                pulsePointer = -1; joyPointer = -1
                engine.joystickUp()
            }
        }
        return true
    }
}
