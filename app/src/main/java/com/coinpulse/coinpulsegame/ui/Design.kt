package com.coinpulse.coinpulsegame.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View

/**
 * Design tokens. Every colour is a pre-resolved Int so nothing parses strings
 * at draw time (the old code called Color.parseColor inside onDraw).
 */
object C {
    val bgDeep = 0xFF07061A.toInt()
    val bgMid = 0xFF14103C.toInt()
    val bgViolet = 0xFF241A5E.toInt()

    val panel = 0xFF221A52.toInt()
    val panelHi = 0xFF322566.toInt()
    val panelBorder = 0xFF5B49B0.toInt()
    val panelGlow = 0x448A5CF6.toInt()

    val gold = 0xFFFFC53D.toInt()
    val goldLight = 0xFFFFE9A8.toInt()
    val goldDark = 0xFFC97F09.toInt()

    val cyan = 0xFF38D6FF.toInt()
    val cyanDark = 0xFF0E7FA8.toInt()
    val purple = 0xFF8A5CF6.toInt()
    val purpleDark = 0xFF4A2FA8.toInt()

    val text = 0xFFFFFFFF.toInt()
    val textDim = 0xFFB9AEE8.toInt()
    val textFaint = 0x99B9AEE8.toInt()

    val green = 0xFF39D98A.toInt()
    val red = 0xFFFF5C7A.toInt()
    val orange = 0xFFFF9B42.toInt()

    val shadow = 0x66000000
    val scrim = 0xE6070518.toInt()
    val white20 = 0x33FFFFFF
    val white40 = 0x66FFFFFF
    val black30 = 0x4D000000
}

object F {
    val title: Typeface by lazy { Typeface.create("sans-serif-black", Typeface.NORMAL) }
    val bold: Typeface by lazy { Typeface.create("sans-serif", Typeface.BOLD) }
    val medium: Typeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }
}

object D {
    fun dp(c: Context, v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.resources.displayMetrics).toInt()

    fun dpf(c: Context, v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.resources.displayMetrics)

    /** Sticky fullscreen so the game owns the whole landscape screen. */
    fun immersive(view: View) {
        @Suppress("DEPRECATION")
        view.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    fun paint(color: Int, stroke: Float = 0f, font: Typeface? = null): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            if (stroke > 0f) {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
            }
            font?.let { typeface = it }
        }
}
