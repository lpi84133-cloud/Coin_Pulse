package com.coinpulse.coinpulsegame.screens

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.coinpulse.coinpulsegame.App
import com.coinpulse.coinpulsegame.Nav
import com.coinpulse.coinpulsegame.ui.Backdrop
import com.coinpulse.coinpulsegame.ui.C
import com.coinpulse.coinpulsegame.ui.D
import com.coinpulse.coinpulsegame.ui.NeonButton
import com.coinpulse.coinpulsegame.ui.cardBg
import com.coinpulse.coinpulsegame.ui.titleText
import com.coinpulse.coinpulsegame.ui.valueText

/** Shared scaffold: animated backdrop, top bar (back, title, coin balance), content. */
@SuppressLint("ViewConstructor")
open class BaseScreen(context: Context, protected val nav: Nav, title: String) : FrameLayout(context) {

    protected val store = App.store
    protected val assets = App.assets
    protected val content = FrameLayout(context)
    private var coinValue: TextView? = null

    init {
        setBackgroundColor(C.bgDeep)
        addView(Backdrop(context), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setPadding(D.dp(context, 22f), D.dp(context, 14f), D.dp(context, 22f), D.dp(context, 14f))
        }
        col.addView(topBar(title))
        content.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        col.addView(content)
        addView(col)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshCoins()
    }

    protected fun refreshCoins() {
        coinValue?.text = "${store.coins}"
    }

    private fun topBar(title: String): View {
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = D.dp(context, 12f) }
        }

        bar.addView(NeonButton(context, "Back", NeonButton.Style.GHOST).apply {
            onClick = { nav.toMenu() }
            layoutParams = LinearLayout.LayoutParams(D.dp(context, 104f), D.dp(context, 44f))
        })

        bar.addView(titleText(context, title, 21f).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = D.dp(context, 12f); marginEnd = D.dp(context, 12f)
        })

        bar.addView(coinChip())
        return bar
    }

    protected fun coinChip(): View {
        val chip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = cardBg(context, 22f, C.panel, C.gold)
            setPadding(D.dp(context, 14f), D.dp(context, 7f), D.dp(context, 16f), D.dp(context, 7f))
        }
        chip.addView(ImageView(context).apply {
            assets.coins.firstOrNull()?.let { setImageBitmap(it) }
            layoutParams = LinearLayout.LayoutParams(D.dp(context, 24f), D.dp(context, 24f)).apply {
                marginEnd = D.dp(context, 7f)
            }
        })
        val value = valueText(context, "${store.coins}", 17f, C.gold)
        coinValue = value
        chip.addView(value)
        return chip
    }
}
