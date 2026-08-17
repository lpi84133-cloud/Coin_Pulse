package com.coinpulse.coinpulsegame.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.coinpulse.coinpulsegame.R
import com.coinpulse.coinpulsegame.data.GameStore

/** Lightweight SFX player backed by SoundPool. Music is not shipped (no asset). */
class SfxBank(private val context: Context, private val store: GameStore) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = HashMap<String, Int>()
    private val loaded = HashSet<Int>()

    private val resMap = linkedMapOf(
        "button" to R.raw.sfx_button,
        "pulse" to R.raw.sfx_pulse,
        "chain" to R.raw.sfx_chain,
        "coin" to R.raw.sfx_coin,
        "crystal" to R.raw.sfx_crystal,
        "reward" to R.raw.sfx_reward,
        "victory" to R.raw.sfx_victory,
        "defeat" to R.raw.sfx_defeat,
        "menu" to R.raw.sfx_menu_open,
        "upgrade" to R.raw.sfx_upgrade,
    )

    /** Total number of sounds, for loading progress accounting. */
    val soundKeys: List<String> get() = resMap.keys.toList()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded.add(sampleId)
        }
    }

    /** Load a single sound (called during the honest loading phase). */
    fun loadOne(key: String) {
        val res = resMap[key] ?: return
        if (!ids.containsKey(key)) {
            ids[key] = pool.load(context, res, 1)
        }
    }

    fun play(key: String, volume: Float = 1f, rate: Float = 1f) {
        if (!store.soundOn) return
        val id = ids[key] ?: return
        pool.play(id, volume, volume, 1, 0, rate)
    }

    fun release() = pool.release()
}
