package com.coinpulse.coinpulsegame.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import com.coinpulse.coinpulsegame.Nav
import com.coinpulse.coinpulsegame.ui.C
import com.coinpulse.coinpulsegame.ui.D
import com.coinpulse.coinpulsegame.ui.F
import com.coinpulse.coinpulsegame.ui.NeonButton
import com.coinpulse.coinpulsegame.ui.bodyText
import com.coinpulse.coinpulsegame.ui.cardBg
import com.coinpulse.coinpulsegame.ui.titleText
import com.coinpulse.coinpulsegame.ui.valueText
import java.io.File
import kotlin.math.min

@SuppressLint("ViewConstructor")
class ProfileScreen(context: Context, nav: Nav) : BaseScreen(context, nav, "Profile") {

    init {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        row.addView(avatarCard(), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        row.addView(View(context), LinearLayout.LayoutParams(D.dp(context, 14f), 1))
        row.addView(statsCard(), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.25f))
        content.addView(row)
    }

    private fun avatarCard(): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = cardBg(context, 18f)
            setPadding(D.dp(context, 16f), D.dp(context, 16f), D.dp(context, 16f), D.dp(context, 16f))
        }

        val size = D.dp(context, 108f)
        card.addView(ImageView(context).apply {
            setImageBitmap(circleBitmap(loadAvatar(), size))
            layoutParams = LinearLayout.LayoutParams(size, size)
        })

        val name = EditText(context).apply {
            setText(store.playerName)
            typeface = F.medium
            setTextColor(C.text)
            setHintTextColor(C.textFaint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            textSize = 15f
            gravity = Gravity.CENTER
            background = cardBg(context, 12f, 0xFF191340.toInt(), C.panelBorder)
            setPadding(D.dp(context, 12f), D.dp(context, 9f), D.dp(context, 12f), D.dp(context, 9f))
        }
        name.setOnFocusChangeListener { _, focused ->
            if (!focused) store.playerName = name.text.toString().ifBlank { "Pulse Runner" }
        }
        card.addView(name, LinearLayout.LayoutParams(
            D.dp(context, 178f), LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = D.dp(context, 12f) })

        val btns = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = D.dp(context, 12f) }
        }
        btns.addView(NeonButton(context, "Gallery", NeonButton.Style.SECONDARY).apply {
            onClick = { nav.pickAvatarFromGallery() }
            layoutParams = LinearLayout.LayoutParams(D.dp(context, 104f), D.dp(context, 46f)).apply {
                marginEnd = D.dp(context, 8f)
            }
        })
        btns.addView(NeonButton(context, "Camera", NeonButton.Style.SECONDARY).apply {
            onClick = { nav.captureAvatarFromCamera() }
            layoutParams = LinearLayout.LayoutParams(D.dp(context, 104f), D.dp(context, 46f))
        })
        card.addView(btns)
        return card
    }

    private fun statsCard(): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg(context, 18f)
            setPadding(D.dp(context, 20f), D.dp(context, 16f), D.dp(context, 20f), D.dp(context, 16f))
        }
        card.addView(titleText(context, "Statistics", 18f))
        card.addView(stat("Best Score", "${store.bestScore}"))
        card.addView(stat("Best Wave", "${store.bestWave} / 6"))
        card.addView(stat("Best Combo", "x${store.bestCombo}"))
        card.addView(stat("Best Survival", formatTime(store.bestSurvivalMs)))
        card.addView(stat("Total Runs", "${store.totalRuns}"))
        card.addView(stat("Enemies Destroyed", "${store.totalKills}"))
        card.addView(stat("Coins", "${store.coins}", C.gold))
        return card
    }

    private fun stat(label: String, value: String, color: Int = C.text): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = D.dp(context, 7f) }
        }
        row.addView(bodyText(context, label, 14f),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(valueText(context, value, 15f, color))
        return row
    }

    private fun loadAvatar(): Bitmap {
        store.avatarPath?.let { path ->
            if (File(path).exists()) BitmapFactory.decodeFile(path)?.let { return it }
        }
        return assets.playerCoinDefault
    }

    private fun circleBitmap(src: Bitmap, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val s = min(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - s) / 2, (src.height - s) / 2, s, s)
        val scaled = Bitmap.createScaledBitmap(square, size, size, true)
        paint.shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = D.dpf(context, 3f)
        paint.color = C.gold
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - paint.strokeWidth / 2f, paint)
        return out
    }

    private fun formatTime(ms: Long): String {
        val total = ms / 1000
        return "%d:%02d".format(total / 60, total % 60)
    }
}
