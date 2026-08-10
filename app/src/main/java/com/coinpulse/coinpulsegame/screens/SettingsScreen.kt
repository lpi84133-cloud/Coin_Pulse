package com.coinpulse.coinpulsegame.screens

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.coinpulse.coinpulsegame.Nav
import com.coinpulse.coinpulsegame.ui.C
import com.coinpulse.coinpulsegame.ui.D
import com.coinpulse.coinpulsegame.ui.NeonButton
import com.coinpulse.coinpulsegame.ui.Toggle
import com.coinpulse.coinpulsegame.ui.bodyText
import com.coinpulse.coinpulsegame.ui.cardBg
import com.coinpulse.coinpulsegame.ui.valueText

@SuppressLint("ViewConstructor")
class SettingsScreen(context: Context, nav: Nav) : BaseScreen(context, nav, "Settings") {

    init {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        val left = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        left.addView(toggleCard("Sound Effects", "Pulse, coins, hits", store.soundOn) { store.soundOn = it })
        left.addView(toggleCard("Music", "Background theme", store.musicOn) { store.musicOn = it })
        left.addView(toggleCard("Vibration", "Haptics when you take a hit", store.vibrationOn) { store.vibrationOn = it })
        row.addView(left)

        row.addView(View(context), LinearLayout.LayoutParams(D.dp(context, 14f), 1))

        val right = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        right.addView(infoCard("Language", "English"))
        right.addView(infoCard("Version", "1.0"))
        right.addView(NeonButton(context, "Replay Tutorial", NeonButton.Style.SECONDARY).apply {
            onClick = { store.tutorialSeen = false; nav.toGame() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, D.dp(context, 48f)
            ).apply { topMargin = D.dp(context, 4f) }
        })
        row.addView(right)

        content.addView(row)
    }

    private fun toggleCard(title: String, desc: String, initial: Boolean, onChange: (Boolean) -> Unit): View {
        val card = shell()
        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        info.addView(valueText(context, title, 15f, C.text))
        info.addView(bodyText(context, desc, 11f))
        card.addView(info)
        card.addView(Toggle(context, initial).apply { this.onChange = onChange })
        return card
    }

    private fun infoCard(title: String, value: String): View {
        val card = shell()
        card.addView(valueText(context, title, 15f, C.text),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(valueText(context, value, 15f, C.gold))
        return card
    }

    private fun shell(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = cardBg(context, 16f)
        setPadding(D.dp(context, 16f), D.dp(context, 12f), D.dp(context, 16f), D.dp(context, 12f))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = D.dp(context, 9f) }
    }
}
