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
import com.coinpulse.coinpulsegame.data.SkinDef
import com.coinpulse.coinpulsegame.ui.C
import com.coinpulse.coinpulsegame.ui.D
import com.coinpulse.coinpulsegame.ui.NeonButton
import com.coinpulse.coinpulsegame.ui.bodyText
import com.coinpulse.coinpulsegame.ui.cardBg
import com.coinpulse.coinpulsegame.ui.valueText

@SuppressLint("ViewConstructor")
class CollectionScreen(context: Context, nav: Nav) : BaseScreen(context, nav, "Coin Collection") {

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
        for (skin in store.skins) row.addView(card(skin))
    }

    private fun card(skin: SkinDef): View {
        val unlocked = store.isSkinUnlocked(skin.id)
        val selected = store.selectedSkin == skin.id

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = cardBg(context, 18f, C.panel, if (selected) C.gold else C.panelBorder)
            setPadding(D.dp(context, 16f), D.dp(context, 18f), D.dp(context, 16f), D.dp(context, 16f))
            layoutParams = LinearLayout.LayoutParams(
                D.dp(context, 152f), LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = D.dp(context, 12f) }
        }

        card.addView(ImageView(context).apply {
            setImageBitmap(assets.loadBitmap(skin.asset))
            alpha = if (unlocked) 1f else 0.35f
            layoutParams = LinearLayout.LayoutParams(D.dp(context, 82f), D.dp(context, 82f))
        })
        card.addView(valueText(context, skin.name, 15f, C.gold).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = D.dp(context, 10f) })

        val action: View = when {
            selected -> bodyText(context, "EQUIPPED", 13f, C.green).apply { gravity = Gravity.CENTER }
            unlocked -> NeonButton(context, "Equip", NeonButton.Style.SECONDARY).apply {
                onClick = { store.selectedSkin = skin.id; rebuild() }
            }
            else -> NeonButton(context, "${skin.cost}", NeonButton.Style.PRIMARY).apply {
                enabledLook = store.coins >= skin.cost
                onClick = {
                    if (store.coins >= skin.cost) {
                        store.coins -= skin.cost
                        store.unlockSkin(skin.id)
                        store.selectedSkin = skin.id
                        App.sound.play("reward")
                        refreshCoins()
                        rebuild()
                    }
                }
            }
        }
        card.addView(action, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, D.dp(context, 44f)
        ).apply { topMargin = D.dp(context, 12f) })
        return card
    }
}
