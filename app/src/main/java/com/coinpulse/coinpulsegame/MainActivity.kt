package com.coinpulse.coinpulsegame

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.coinpulse.coinpulsegame.audio.SoundManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity(), Nav {

    override val activity: AppCompatActivity get() = this

    private lateinit var root: FrameLayout
    private var currentGame: GameScreen? = null

    // ---- Avatar launchers ----
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) saveAvatarFromUri(uri) }

    private var cameraTargetUri: Uri? = null
    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) cameraTargetUri?.let { saveAvatarFromUri(it) } }

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) launchCamera() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Init shared singletons
        App.store = GameStore(applicationContext)
        App.sound = SoundManager(applicationContext, App.store)
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
        val i = Intent(this, WebViewActivity::class.java)
        i.putExtra(WebViewActivity.EXTRA_LOCAL, fileName)
        i.putExtra(WebViewActivity.EXTRA_ONLINE, onlineUrl)
        startActivity(i)
    }

    override fun pickAvatarFromGallery() {
        pickImage.launch(androidx.activity.result.PickVisualMediaRequest(
            ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    override fun captureAvatarFromCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val dir = File(cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        cameraTargetUri = uri
        takePicture.launch(uri)
    }

    private fun saveAvatarFromUri(uri: Uri) {
        lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) {
                try {
                    val src = contentResolver.openInputStream(uri).use { input ->
                        BitmapFactory.decodeStream(input)
                    } ?: return@withContext null
                    val size = 256
                    val scaled = Bitmap.createScaledBitmap(src, size, size, true)
                    val dir = File(filesDir, "avatar").apply { mkdirs() }
                    val out = File(dir, "avatar.jpg")
                    out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    out.absolutePath
                } catch (e: Exception) {
                    null
                }
            }
            if (path != null) {
                App.store.avatarPath = path
                toProfile()
            }
        }
    }
}
