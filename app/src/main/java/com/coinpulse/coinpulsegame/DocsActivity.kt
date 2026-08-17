package com.coinpulse.coinpulsegame

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.coinpulse.coinpulsegame.ui.C
import com.coinpulse.coinpulsegame.ui.D
import com.coinpulse.coinpulsegame.ui.NeonButton
import com.coinpulse.coinpulsegame.ui.titleText

/**
 * Shows Privacy Policy / Support from bundled local HTML. Fully offline: the pages
 * ship in assets, so no INTERNET permission is needed. Black text on white.
 */
class DocsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(C.bgDeep)
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(C.bgViolet)
            setPadding(D.dp(context, 14f), D.dp(context, 10f), D.dp(context, 14f), D.dp(context, 10f))
        }
        bar.addView(NeonButton(this, "Back", NeonButton.Style.GHOST).apply {
            onClick = { finish() }
            layoutParams = LinearLayout.LayoutParams(D.dp(context, 100f), D.dp(context, 42f))
        })
        bar.addView(titleText(this, title(), 17f).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = D.dp(this@DocsActivity, 12f)
                marginEnd = D.dp(this@DocsActivity, 100f)
            })
        root.addView(bar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val wv = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = false
        }
        val container = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        container.addView(wv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(container, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        wv.loadUrl("file:///android_asset/${localFile()}")
    }

    private fun localFile() = intent.getStringExtra(EXTRA_LOCAL) ?: "privacy_policy.html"

    private fun title() =
        if (localFile().startsWith("support")) "Support" else "Privacy Policy"

    companion object {
        const val EXTRA_LOCAL = "local_file"
        const val EXTRA_ONLINE = "online_url"
    }
}
