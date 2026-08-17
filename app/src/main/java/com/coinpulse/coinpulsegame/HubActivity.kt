package com.coinpulse.coinpulsegame

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.coinpulse.coinpulsegame.audio.SfxBank
import com.coinpulse.coinpulsegame.data.GameStore
import com.coinpulse.coinpulsegame.game.Assets
import com.coinpulse.coinpulsegame.screens.ArenaScreen
import com.coinpulse.coinpulsegame.screens.CollectionScreen
import com.coinpulse.coinpulsegame.screens.GameScreen
import com.coinpulse.coinpulsegame.screens.LoadingScreen
import com.coinpulse.coinpulsegame.screens.MenuScreen
import com.coinpulse.coinpulsegame.screens.ProfileScreen
import com.coinpulse.coinpulsegame.screens.SettingsScreen
import com.coinpulse.coinpulsegame.screens.UpgradesScreen
import com.coinpulse.coinpulsegame.ui.D

class HubActivity : AppCompatActivity(), Nav {

    override val activity: AppCompatActivity get() = this

    private lateinit var root: FrameLayout
    private var currentGame: GameScreen? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Init shared singletons
        App.store = GameStore(applicationContext)
        App.sound = SfxBank(applicationContext, App.store)
        App.assets = Assets(applicationContext, App.sound)

        root = FrameLayout(this)
        root.setBackgroundColor(0xFF0E0B24.toInt())
        setContentView(root)
        D.immersive(root)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val showingMenu = root.getChildAt(0) is MenuScreen
                val showingLoading = root.getChildAt(0) is LoadingScreen
                if (showingMenu || showingLoading) finish() else toMenu()
            }
        })

        if (App.assetsReady) {
            toMenu()
        } else {
            val loading = LoadingScreen(this)
            show(loading)
            loading.start(lifecycleScope) { toMenu() }
        }
    }

    private fun show(view: View) {
        currentGame?.let { if (it !== view) it.stop() }
        currentGame = view as? GameScreen
        root.removeAllViews()
        root.addView(view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        D.immersive(root)
    }

    override fun onResume() {
        super.onResume()
        D.immersive(root)
    }

    // ---------- Nav ----------
    override fun toMenu() = show(MenuScreen(this, this))
    override fun toGame() = show(GameScreen(this, this))
    override fun toUpgrades() = show(UpgradesScreen(this, this))
    override fun toCollection() = show(CollectionScreen(this, this))
    override fun toArenas() = show(ArenaScreen(this, this))
    override fun toSettings() = show(SettingsScreen(this, this))
    override fun toProfile() = show(ProfileScreen(this, this))

    override fun openWeb(fileName: String, onlineUrl: String) {
        val i = Intent(this, DocsActivity::class.java)
        i.putExtra(DocsActivity.EXTRA_LOCAL, fileName)
        i.putExtra(DocsActivity.EXTRA_ONLINE, onlineUrl)
        startActivity(i)
    }
}
