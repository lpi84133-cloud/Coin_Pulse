package com.coinpulse.coinpulsegame.screens

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
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
import com.coinpulse.coinpulsegame.ui.F
import com.coinpulse.coinpulsegame.ui.NeonButton
import com.coinpulse.coinpulsegame.ui.bodyText
import com.coinpulse.coinpulsegame.ui.cardBg
import com.coinpulse.coinpulsegame.ui.valueText

@SuppressLint("ViewConstructor")
class MenuScreen(context: Context, private val nav: Nav) : FrameLayout(context) {

    private val store = App.store
    private val assets = App.assets
    private var coinValue: TextView? = null

    init {
        setBackgroundColor(C.bgDeep)
        addView(Backdrop(context), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setPadding(D.dp(context, 26f), D.dp(context, 18f), D.dp(context, 26f), D.dp(context, 18f))
        }
        row.addView(leftSide(), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.15f))
        row.addView(View(context), LinearLayout.LayoutParams(D.dp(context, 22f), 1))
        row.addView(rightSide(), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        addView(row)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        coinValue?.text = "${store.coins}"
    }

    private fun leftSide(): View {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val logo = ImageView(context).apply {
            setImageBitmap(assets.logo)
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        col.addView(logo)
        // gentle float so the hero art feels alive
        ObjectAnimator.ofFloat(logo, "translationY", -D.dpf(context, 6f), D.dpf(context, 6f)).apply {
            duration = 2200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        col.addView(coinChip(), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = D.dp(context, 8f) })

        val links = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        links.addView(link("Privacy Policy") {
            nav.openWeb("privacy_policy.html", "https://coinnpullse.com/privacy-policy.html")
        })
        links.addView(bodyText(context, "   •   ", 12f, C.textFaint))
        links.addView(link("Support") {
            nav.openWeb("support.html", "https://coinnpullse.com/support.html")
        })
        col.addView(links, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = D.dp(context, 12f) })

        return col
    }

    private fun rightSide(): View {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // hero PLAY
        col.addView(NeonButton(context, "Play", NeonButton.Style.PRIMARY).apply {
            icon = assets.playerCoinDefault
            onClick = { nav.toGame() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, D.dp(context, 68f)
            ).apply { bottomMargin = D.dp(context, 14f) }
        })

        col.addView(buttonRow(
            "Arenas" to { nav.toArenas() },
            "Collection" to { nav.toCollection() },
        ))
        col.addView(buttonRow(
            "Upgrades" to { nav.toUpgrades() },
            "Profile" to { nav.toProfile() },
        ))

        col.addView(NeonButton(context, "Settings", NeonButton.Style.GHOST).apply {
            onClick = { nav.toSettings() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, D.dp(context, 48f)
            ).apply { topMargin = D.dp(context, 4f) }
        })
        return col
    }

    private fun buttonRow(
        left: Pair<String, () -> Unit>,
        right: Pair<String, () -> Unit>,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = D.dp(context, 10f) }
        }
        row.addView(NeonButton(context, left.first, NeonButton.Style.SECONDARY).apply {
            onClick = left.second
            layoutParams = LinearLayout.LayoutParams(0, D.dp(context, 52f), 1f).apply {
                marginEnd = D.dp(context, 5f)
            }
        })
        row.addView(NeonButton(context, right.first, NeonButton.Style.SECONDARY).apply {
            onClick = right.second
            layoutParams = LinearLayout.LayoutParams(0, D.dp(context, 52f), 1f).apply {
                marginStart = D.dp(context, 5f)
            }
        })
        return row
    }

    private fun coinChip(): View {
        val chip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = cardBg(context, 22f, C.panel, C.gold)
            setPadding(D.dp(context, 16f), D.dp(context, 8f), D.dp(context, 18f), D.dp(context, 8f))
        }
        chip.addView(ImageView(context).apply {
            assets.coins.firstOrNull()?.let { setImageBitmap(it) }
            layoutParams = LinearLayout.LayoutParams(D.dp(context, 26f), D.dp(context, 26f)).apply {
                marginEnd = D.dp(context, 8f)
            }
        })
        val value = valueText(context, "${store.coins}", 19f, C.gold)
        coinValue = value
        chip.addView(value)
        return chip
    }

    private fun link(label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            typeface = F.medium
            setTextColor(C.cyan)
            textSize = 13f
            paint.isUnderlineText = true
            setPadding(D.dp(context, 4f), D.dp(context, 6f), D.dp(context, 4f), D.dp(context, 6f))
            setOnClickListener { App.sound.play("button"); onClick() }
        }
}
