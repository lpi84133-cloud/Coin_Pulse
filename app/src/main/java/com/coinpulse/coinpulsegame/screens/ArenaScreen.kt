package com.coinpulse.coinpulsegame.screens

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import com.coinpulse.coinpulsegame.App
import com.coinpulse.coinpulsegame.Nav
import com.coinpulse.coinpulsegame.data.ArenaDef
import com.coinpulse.coinpulsegame.ui.C
import com.coinpulse.coinpulsegame.ui.D
import com.coinpulse.coinpulsegame.ui.NeonButton
import com.coinpulse.coinpulsegame.ui.bodyText
import com.coinpulse.coinpulsegame.ui.cardBg
import com.coinpulse.coinpulsegame.ui.valueText

@SuppressLint("ViewConstructor")
class ArenaScreen(context: Context, nav: Nav) : BaseScreen(context, nav, "Select Arena") {

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    init {
        content.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        })
        rebuild()
    }

    private fun rebuild() {
        row.removeAllViews()
        for (a in store.arenas) row.addView(card(a))
    }

    private fun card(a: ArenaDef): View {
        val unlocked = store.isArenaUnlocked(a.id)
        val selected = store.selectedArena == a.id

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = cardBg(context, 18f, C.panel, if (selected) C.gold else C.panelBorder)
            setPadding(D.dp(context, 12f), D.dp(context, 12f), D.dp(context, 12f), D.dp(context, 12f))
            layoutParams = LinearLayout.LayoutParams(
                D.dp(context, 236f), LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = D.dp(context, 12f) }
        }

        card.addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            assets.backgrounds[a.bg]?.let { setImageBitmap(it) }
            alpha = if (unlocked) 1f else 0.3f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, D.dp(context, 96f)
            )
        })
        card.addView(valueText(context, a.name, 16f, C.gold).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = D.dp(context, 10f) })
        card.addView(bodyText(context, "Difficulty x${a.difficulty}", 12f).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val action: View = when {
            selected -> bodyText(context, "SELECTED", 13f, C.green).apply { gravity = Gravity.CENTER }
            unlocked -> NeonButton(context, "Select", NeonButton.Style.SECONDARY).apply {
                onClick = { store.selectedArena = a.id; rebuild() }
            }
            else -> NeonButton(context, "Unlock ${a.cost}", NeonButton.Style.PRIMARY).apply {
                enabledLook = store.coins >= a.cost
                onClick = {
                    if (store.coins >= a.cost) {
                        store.coins -= a.cost
                        store.unlockArena(a.id)
                        store.selectedArena = a.id
                        App.sound.play("reward")
                        refreshCoins()
                        rebuild()
                    }
                }
            }
        }
        card.addView(action, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, D.dp(context, 44f)
        ).apply { topMargin = D.dp(context, 10f) })
        return card
    }
}
