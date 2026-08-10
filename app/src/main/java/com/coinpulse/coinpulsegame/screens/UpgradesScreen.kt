package com.coinpulse.coinpulsegame.screens

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.coinpulse.coinpulsegame.App
import com.coinpulse.coinpulsegame.Nav
import com.coinpulse.coinpulsegame.data.Upgrade
import com.coinpulse.coinpulsegame.ui.C
import com.coinpulse.coinpulsegame.ui.D
import com.coinpulse.coinpulsegame.ui.NeonButton
import com.coinpulse.coinpulsegame.ui.bodyText
import com.coinpulse.coinpulsegame.ui.cardBg
import com.coinpulse.coinpulsegame.ui.valueText

/** Permanent upgrades laid out as a 2-column grid so nothing needs scrolling. */
@SuppressLint("ViewConstructor")
class UpgradesScreen(context: Context, nav: Nav) : BaseScreen(context, nav, "Permanent Upgrades") {

    private val grid = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    init {
        content.addView(grid)
        rebuild()
    }

    private fun rebuild() {
        grid.removeAllViews()
        val all = Upgrade.values()
        var i = 0
        while (i < all.size) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                ).apply { bottomMargin = D.dp(context, 8f) }
            }
            row.addView(cell(all[i]), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                .apply { marginEnd = D.dp(context, 5f) })
            if (i + 1 < all.size) {
                row.addView(cell(all[i + 1]), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    .apply { marginStart = D.dp(context, 5f) })
            } else {
                row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            }
            grid.addView(row)
            i += 2
        }
    }

    private fun cell(u: Upgrade): View {
        val level = store.upgradeLevel(u)
        val maxed = level >= u.maxLevel
        val cost = u.costFor(level)
        val affordable = store.coins >= cost

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBg(context, 16f)
            setPadding(D.dp(context, 14f), D.dp(context, 10f), D.dp(context, 12f), D.dp(context, 10f))
        }

        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        info.addView(valueText(context, u.title, 15f, C.gold))
        info.addView(bodyText(context, u.desc, 11f))
        info.addView(pips(level, u.maxLevel))
        card.addView(info)

        if (maxed) {
            card.addView(valueText(context, "MAX", 14f, C.green))
        } else {
            card.addView(NeonButton(context, "$cost", NeonButton.Style.PRIMARY).apply {
                enabledLook = affordable
                onClick = {
                    if (store.coins >= cost) {
                        store.coins -= cost
                        store.setUpgradeLevel(u, level + 1)
                        App.sound.play("upgrade")
                        refreshCoins()
                        rebuild()
                    }
                }
                layoutParams = LinearLayout.LayoutParams(D.dp(context, 78f), D.dp(context, 44f))
            })
        }
        return card
    }

    private fun pips(level: Int, max: Int): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = D.dp(context, 6f) }
        }
        for (i in 0 until max) {
            row.addView(View(context).apply {
                background = cardBg(context, 3f,
                    if (i < level) C.cyan else 0xFF2A2358.toInt(),
                    if (i < level) C.cyan else C.panelBorder)
                layoutParams = LinearLayout.LayoutParams(D.dp(context, 15f), D.dp(context, 7f)).apply {
                    marginEnd = D.dp(context, 3f)
                }
            })
        }
        return row
    }
}
