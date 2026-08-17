package com.coinpulse.coinpulsegame.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.coinpulse.coinpulsegame.audio.SfxBank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads every sprite / background from /assets and preloads all SFX.
 * Reports honest progress: one tick per real decode/preload task.
 */
class Assets(private val context: Context, private val sound: SfxBank) {

    private val am = context.assets
    private val cache = HashMap<String, Bitmap>()

    // Typed collections filled after load()
    val enemiesSmall = ArrayList<Bitmap>()
    val enemiesMedium = ArrayList<Bitmap>()
    val coins = ArrayList<Bitmap>()
    val crystals = ArrayList<Bitmap>()
    val stars = ArrayList<Bitmap>()
    val bells = ArrayList<Bitmap>()
    val fruits = ArrayList<Bitmap>()
    val numbers = ArrayList<Bitmap>()
    val powerups = ArrayList<Bitmap>()
    val backgrounds = HashMap<String, Bitmap>()
    lateinit var guardianElite: Bitmap
    lateinit var portal: Bitmap
    lateinit var logo: Bitmap
    lateinit var playerCoinDefault: Bitmap

    fun get(path: String): Bitmap? = cache[path]

    private fun listPng(dir: String): List<String> =
        (am.list(dir) ?: emptyArray()).filter { it.endsWith(".png") }.sorted().map { "$dir/$it" }

    /** Ordered list of image asset paths to decode. */
    private fun imageTasks(): List<String> {
        val paths = ArrayList<String>()
        listOf(
            "sprites/enemy_small", "sprites/enemy_medium",
            "sprites/coin", "sprites/crystal", "sprites/star",
            "sprites/bell", "sprites/fruit", "sprites/number", "sprites/powerup",
        ).forEach { paths.addAll(listPng(it)) }
        paths.add("sprites/player_coin.png")
        paths.add("sprites/guardian_elite.png")
        paths.add("sprites/portal.png")
        paths.add("sprites/logo.png")
        paths.add("bg/arena_main.jpg")
        paths.add("bg/arena_deep.jpg")
        paths.add("bg/arena_final.jpg")
        return paths
    }

    /** Total number of loading steps (images + sounds). */
    fun totalSteps(): Int = imageTasks().size + sound.soundKeys.size

    private fun decode(path: String): Bitmap {
        am.open(path).use { input ->
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            return BitmapFactory.decodeStream(input, null, opts)
                ?: throw IllegalStateException("decode failed: $path")
        }
    }

    /**
     * Perform the real work off the main thread, invoking [onStep] after each
     * completed task with the number of completed steps so far.
     */
    suspend fun load(onStep: (done: Int, total: Int) -> Unit) = withContext(Dispatchers.IO) {
        val images = imageTasks()
        val total = images.size + sound.soundKeys.size
        var done = 0

        for (path in images) {
            val bmp = decode(path)
            cache[path] = bmp
            done++
            onStep(done, total)
        }

        // sounds
        for (key in sound.soundKeys) {
            sound.loadOne(key)
            done++
            onStep(done, total)
        }

        // Build typed collections
        fun coll(dir: String, into: ArrayList<Bitmap>) =
            listPng(dir).forEach { cache[it]?.let(into::add) }
        coll("sprites/enemy_small", enemiesSmall)
        coll("sprites/enemy_medium", enemiesMedium)
        coll("sprites/coin", coins)
        coll("sprites/crystal", crystals)
        coll("sprites/star", stars)
        coll("sprites/bell", bells)
        coll("sprites/fruit", fruits)
        coll("sprites/number", numbers)
        coll("sprites/powerup", powerups)
        playerCoinDefault = cache["sprites/player_coin.png"]!!
        guardianElite = cache["sprites/guardian_elite.png"]!!
        portal = cache["sprites/portal.png"]!!
        logo = cache["sprites/logo.png"]!!
        backgrounds["bg/arena_main.jpg"] = cache["bg/arena_main.jpg"]!!
        backgrounds["bg/arena_deep.jpg"] = cache["bg/arena_deep.jpg"]!!
        backgrounds["bg/arena_final.jpg"] = cache["bg/arena_final.jpg"]!!
    }

    /** Decode an arbitrary asset on demand (e.g. selected skin). */
    fun loadBitmap(path: String): Bitmap = cache[path] ?: decode(path).also { cache[path] = it }
}
