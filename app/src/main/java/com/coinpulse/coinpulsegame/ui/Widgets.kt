package com.coinpulse.coinpulsegame.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.coinpulse.coinpulsegame.App
import kotlin.math.max

/** Rounded panel background used across all screens. */
fun cardBg(c: Context, radius: Float = 18f, fill: Int = C.panel, border: Int = C.panelBorder): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = D.dpf(c, radius)
        colors = intArrayOf(C.panelHi, fill)
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        setStroke(D.dp(c, 1.5f), border)
    }

/**
 * Custom-drawn action button: gradient body, bevel highlight, accent glow and a
 * springy press animation. Replaces the flat TextView buttons.
 */
class NeonButton(
    context: Context,
    label: String,
    private val style: Style = Style.PRIMARY,
) : View(context) {

    enum class Style { PRIMARY, SECONDARY, DANGER, GHOST }

    var label: String = label
        set(value) { field = value; requestLayout(); invalidate() }

    var icon: Bitmap? = null
        set(value) { field = value; invalidate() }

    var onClick: (() -> Unit)? = null
    var enabledLook = true
        set(value) { field = value; alpha = if (value) 1f else 0.45f; invalidate() }

    private val topColor: Int
    private val bottomColor: Int
    private val glowColor: Int
    private val labelColor: Int

    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bevel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = F.title
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.06f
    }
    private val rect = RectF()
    private val iconDst = Rect()
    private var radius = 0f

    init {
        when (style) {
            Style.PRIMARY -> {
                topColor = C.gold; bottomColor = C.goldDark; glowColor = 0x66FFC53D
                labelColor = 0xFF3A2400.toInt()
            }
            Style.SECONDARY -> {
                topColor = C.purple; bottomColor = C.purpleDark; glowColor = 0x558A5CF6
                labelColor = C.text
            }
            Style.DANGER -> {
                topColor = C.red; bottomColor = 0xFFA82344.toInt(); glowColor = 0x66FF5C7A
                labelColor = C.text
            }
            Style.GHOST -> {
                topColor = 0x33FFFFFF; bottomColor = 0x1AFFFFFF; glowColor = 0x00000000
                labelColor = C.text
            }
        }
        isClickable = true
        setPadding(D.dp(context, 18f), 0, D.dp(context, 18f), 0)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        textPaint.textSize = D.dpf(context, 15f)
        val iconW = if (icon != null) D.dpf(context, 30f) else 0f
        val desiredW = (textPaint.measureText(label.uppercase()) + iconW + paddingLeft + paddingRight).toInt()
        val desiredH = D.dp(context, 52f)
        setMeasuredDimension(
            resolveSize(max(desiredW, D.dp(context, 96f)), widthMeasureSpec),
            resolveSize(desiredH, heightMeasureSpec)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        radius = h * 0.32f
        body.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            intArrayOf(topColor, bottomColor), null, Shader.TileMode.CLAMP)
        bevel.strokeWidth = D.dpf(context, 1.5f)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val inset = D.dpf(context, 2f)

        // outer glow
        if (glowColor != 0) {
            glow.color = glowColor
            glow.maskFilter = null
            rect.set(inset * 0.5f, inset * 0.5f, w - inset * 0.5f, h - inset * 0.5f)
            glow.setShadowLayer(D.dpf(context, 10f), 0f, D.dpf(context, 2f), glowColor)
            canvas.drawRoundRect(rect, radius, radius, glow)
            glow.clearShadowLayer()
        }

        // body
        rect.set(inset, inset, w - inset, h - inset)
        canvas.drawRoundRect(rect, radius, radius, body)

        // top bevel highlight
        bevel.color = C.white40
        rect.set(inset + bevel.strokeWidth, inset + bevel.strokeWidth,
            w - inset - bevel.strokeWidth, h - inset - bevel.strokeWidth)
        canvas.drawRoundRect(rect, radius, radius, bevel)

        // content
        val text = label.uppercase()
        textPaint.textSize = D.dpf(context, 15f)
        textPaint.color = labelColor
        val ic = icon
        val textW = textPaint.measureText(text)
        val cy = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        if (ic != null) {
            val size = D.dp(context, 26f)
            val totalW = textW + size + D.dpf(context, 8f)
            val startX = (w - totalW) / 2f
            iconDst.set(startX.toInt(), ((h - size) / 2f).toInt(),
                (startX + size).toInt(), ((h + size) / 2f).toInt())
            canvas.drawBitmap(ic, null, iconDst, null)
            canvas.drawText(text, startX + size + D.dpf(context, 8f) + textW / 2f, cy, textPaint)
        } else {
            canvas.drawText(text, w / 2f, cy, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!enabledLook) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> springTo(0.94f)
            MotionEvent.ACTION_UP -> {
                springTo(1f)
                if (isInside(event)) {
                    App.sound.play("button")
                    onClick?.invoke()
                }
            }
            MotionEvent.ACTION_CANCEL -> springTo(1f)
        }
        return true
    }

    private fun isInside(e: MotionEvent) =
        e.x >= 0 && e.y >= 0 && e.x <= width && e.y <= height

    private fun springTo(scale: Float) {
        animate().cancel()
        animate().scaleX(scale).scaleY(scale).setDuration(90).start()
    }
}

/** Custom animated switch (replaces the stock SwitchCompat look). */
class Toggle(context: Context, initial: Boolean) : View(context) {

    var checked: Boolean = initial
        private set
    var onChange: ((Boolean) -> Unit)? = null

    private var knob = if (initial) 1f else 0f
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.text }
    private val rect = RectF()

    init {
        isClickable = true
        setOnClickListener {
            checked = !checked
            App.sound.play("button")
            animateKnob()
            onChange?.invoke(checked)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(D.dp(context, 58f), widthMeasureSpec),
            resolveSize(D.dp(context, 32f), heightMeasureSpec)
        )
    }

    private fun animateKnob() {
        ValueAnimator.ofFloat(knob, if (checked) 1f else 0f).apply {
            duration = 160
            addUpdateListener { knob = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val r = h / 2f
        track.color = blend(C.panelBorder, C.green, knob)
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, r, r, track)

        val pad = h * 0.14f
        val tr = r - pad
        val cx = r + (w - h) * knob
        thumb.setShadowLayer(D.dpf(context, 4f), 0f, D.dpf(context, 1f), C.shadow)
        canvas.drawCircle(cx, r, tr, thumb)
        thumb.clearShadowLayer()
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val ar = (a shr 16) and 0xFF; val ag = (a shr 8) and 0xFF; val ab = a and 0xFF
        val br = (b shr 16) and 0xFF; val bg = (b shr 8) and 0xFF; val bb = b and 0xFF
        val r = (ar + (br - ar) * t).toInt()
        val g = (ag + (bg - ag) * t).toInt()
        val bl = (ab + (bb - ab) * t).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }
}

// ---------- small text helpers ----------

fun titleText(c: Context, s: String, sizeSp: Float = 22f, color: Int = C.gold): TextView =
    TextView(c).apply {
        text = s
        typeface = F.title
        setTextColor(color)
        textSize = sizeSp
        letterSpacing = 0.04f
    }

fun bodyText(c: Context, s: String, sizeSp: Float = 14f, color: Int = C.textDim): TextView =
    TextView(c).apply {
        text = s
        typeface = F.medium
        setTextColor(color)
        textSize = sizeSp
    }

fun valueText(c: Context, s: String, sizeSp: Float = 16f, color: Int = C.gold): TextView =
    TextView(c).apply {
        text = s
        typeface = F.title
        setTextColor(color)
        textSize = sizeSp
    }

/** Vertical spacer for LinearLayout stacks. */
fun gap(c: Context, dp: Float): View = View(c).apply {
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, D.dp(c, dp))
}
