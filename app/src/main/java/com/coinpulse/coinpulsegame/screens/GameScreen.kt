package com.coinpulse.coinpulsegame.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.coinpulse.coinpulsegame.App
import com.coinpulse.coinpulsegame.Nav
import com.coinpulse.coinpulsegame.game.Engine
import com.coinpulse.coinpulsegame.game.GameCanvasView
import com.coinpulse.coinpulsegame.game.GameResult
import com.coinpulse.coinpulsegame.game.RunUpgrade
import com.coinpulse.coinpulsegame.ui.C
import com.coinpulse.coinpulsegame.ui.D
import com.coinpulse.coinpulsegame.ui.NeonButton
import com.coinpulse.coinpulsegame.ui.bodyText
import com.coinpulse.coinpulsegame.ui.cardBg
import com.coinpulse.coinpulsegame.ui.titleText
import com.coinpulse.coinpulsegame.ui.valueText

@SuppressLint("ViewConstructor")
class GameScreen(context: Context, private val nav: Nav) : FrameLayout(context) {

    private val store = App.store
    private val assets = App.assets
    private val engine = Engine(store, assets, App.sound)
    private val canvasView: GameCanvasView
    private var overlay: View? = null
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    init {
        setBackgroundColor(C.bgDeep)
        canvasView = GameCanvasView(context, engine) { showPause() }
        addView(canvasView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        engine.onWaveComplete = { options, choose -> post { showUpgrades(options, choose) } }
        engine.onEnded = { result -> post { showResult(result) } }
        engine.onVibrate = { ms -> vibrate(ms) }

        if (!store.tutorialSeen) showTutorial()
    }

    fun stop() {
        engine.paused = true
    }

    private fun vibrate(ms: Long) {
        if (!store.vibrationOn) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        // Haptics are cosmetic: never let a vendor/permission quirk kill the run.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") v.vibrate(ms)
            }
        } catch (_: Exception) {
        }
    }

    // ---------- overlay plumbing ----------
    private fun scrim(): FrameLayout = FrameLayout(context).apply {
        setBackgroundColor(C.scrim)
        isClickable = true
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    private fun panel(title: String, titleColor: Int = C.gold): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = cardBg(context, 22f)
            setPadding(D.dp(context, 28f), D.dp(context, 22f), D.dp(context, 28f), D.dp(context, 22f))
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            addView(titleText(context, title, 26f, titleColor).apply { gravity = Gravity.CENTER })
            addView(View(context), LinearLayout.LayoutParams(1, D.dp(context, 14f)))
        }

    private fun setOverlay(v: View?) {
        overlay?.let { removeView(it) }
        overlay = v
        v?.let {
            addView(it)
            it.alpha = 0f
            it.animate().alpha(1f).setDuration(160).start()
        }
    }

    private fun button(label: String, style: NeonButton.Style, widthDp: Float = 220f, onClick: () -> Unit): NeonButton =
        NeonButton(context, label, style).apply {
            this.onClick = onClick
            layoutParams = LinearLayout.LayoutParams(D.dp(context, widthDp), D.dp(context, 50f)).apply {
                topMargin = D.dp(context, 8f)
            }
        }

    // ---------- tutorial ----------
    private fun showTutorial() {
        engine.paused = true
        val root = scrim()
        val p = panel("HOW TO PLAY", C.cyan)
        p.addView(hintRow(assets.playerCoinDefault, "Drag anywhere", "Move your coin with a floating joystick"))
        p.addView(hintRow(assets.crystals.firstOrNull(), "Hold HOLD button", "Charge the pulse — longer hold, bigger blast"))
        p.addView(hintRow(assets.stars.firstOrNull(), "Release to fire", "The wave kills enemies and collects orbs"))
        p.addView(button("Start", NeonButton.Style.PRIMARY) {
            store.tutorialSeen = true
            setOverlay(null)
            engine.paused = false
        })
        root.addView(p)
        setOverlay(root)
    }

    private fun hintRow(icon: Bitmap?, title: String, desc: String): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                D.dp(context, 340f), LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = D.dp(context, 10f) }
        }
        row.addView(ImageView(context).apply {
            icon?.let { setImageBitmap(it) }
            layoutParams = LinearLayout.LayoutParams(D.dp(context, 38f), D.dp(context, 38f)).apply {
                marginEnd = D.dp(context, 12f)
            }
        })
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        col.addView(valueText(context, title, 15f, C.text))
        col.addView(bodyText(context, desc, 12f))
        row.addView(col)
        return row
    }

    // ---------- pause ----------
    private fun showPause() {
        if (engine.ended) return
        engine.paused = true
        val root = scrim()
        val p = panel("PAUSED")
        p.addView(button("Resume", NeonButton.Style.PRIMARY) {
            setOverlay(null); engine.paused = false
        })
        p.addView(button("Restart", NeonButton.Style.SECONDARY) { nav.toGame() })
        p.addView(button("Main Menu", NeonButton.Style.GHOST) { nav.toMenu() })
        root.addView(p)
        setOverlay(root)
    }

    // ---------- upgrades ----------
    private fun showUpgrades(options: List<RunUpgrade>, choose: (RunUpgrade) -> Unit) {
        val root = scrim()
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        col.addView(titleText(context, "WAVE ${engine.wave} CLEARED", 18f, C.green).apply {
            gravity = Gravity.CENTER
        })
        col.addView(titleText(context, "CHOOSE AN UPGRADE", 26f, C.gold).apply {
            gravity = Gravity.CENTER
        })
        col.addView(View(context), LinearLayout.LayoutParams(1, D.dp(context, 16f)))

        val cards = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for (u in options) {
            cards.addView(upgradeCard(u) {
                setOverlay(null)
                choose(u)
            })
        }
        col.addView(cards)
        root.addView(col)
        setOverlay(root)
    }

    private fun upgradeCard(u: RunUpgrade, onPick: () -> Unit): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = cardBg(context, 18f, C.panel, C.panelBorder)
            setPadding(D.dp(context, 14f), D.dp(context, 18f), D.dp(context, 14f), D.dp(context, 18f))
            layoutParams = LinearLayout.LayoutParams(
                D.dp(context, 148f), LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = D.dp(context, 9f); marginEnd = D.dp(context, 9f) }
            isClickable = true
        }
        card.addView(ImageView(context).apply {
            iconFor(u.icon)?.let { setImageBitmap(it) }
            layoutParams = LinearLayout.LayoutParams(D.dp(context, 54f), D.dp(context, 54f))
        })
        card.addView(titleText(context, u.title, 16f, C.gold).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = D.dp(context, 10f) })
        card.addView(bodyText(context, u.desc, 12f).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = D.dp(context, 4f) })

        card.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start()
                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    App.sound.play("button")
                    onPick()
                }
                MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            }
            true
        }
        return card
    }

    private fun iconFor(key: String): Bitmap? = when (key) {
        "crystal" -> assets.crystals.firstOrNull()
        "star" -> assets.stars.firstOrNull()
        "powerup" -> assets.powerups.firstOrNull()
        "fruit" -> assets.fruits.firstOrNull()
        "bell" -> assets.bells.firstOrNull()
        else -> assets.powerups.firstOrNull()
    }

    // ---------- result ----------
    private fun showResult(result: GameResult) {
        val root = scrim()
        val p = panel(
            if (result.victory) "VICTORY!" else "DEFEAT",
            if (result.victory) C.gold else C.red
        )
        p.addView(statRow("Score", "${result.score}"))
        p.addView(statRow("Wave reached", "${result.wave} / ${engine.finalWave}"))
        p.addView(statRow("Enemies destroyed", "${result.kills}"))
        p.addView(statRow("Best combo", "x${result.bestCombo}"))
        p.addView(statRow("Coins earned", "+${result.coinsEarned}", C.gold))

        p.addView(button(if (result.victory) "Play Again" else "Try Again", NeonButton.Style.PRIMARY) {
            nav.toGame()
        })
        p.addView(button("Main Menu", NeonButton.Style.GHOST) { nav.toMenu() })
        root.addView(p)
        setOverlay(root)
    }

    private fun statRow(label: String, value: String, valueColor: Int = C.text): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                D.dp(context, 260f), LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = D.dp(context, 6f) }
        }
        row.addView(bodyText(context, label, 14f),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(valueText(context, value, 15f, valueColor))
        return row
    }
}
