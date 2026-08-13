package com.coinpulse.coinpulsegame.data

import android.content.Context
import android.content.SharedPreferences

/** Permanent, meta-progression upgrades bought with coins. */
enum class Upgrade(
    val title: String,
    val desc: String,
    val maxLevel: Int,
    val baseCost: Int,
) {
    MAX_ENERGY("Max Energy", "Coin survives more hits", 6, 120),
    PULSE_RADIUS("Pulse Radius", "Wider energy wave", 6, 100),
    PULSE_POWER("Pulse Power", "More pulse damage", 6, 150),
    RECHARGE("Recharge Speed", "Faster charge", 6, 110),
    MAGNET("Magnet Range", "Pull resources farther", 5, 90),
    MOVE_SPEED("Move Speed", "Coin moves faster", 5, 90);

    fun costFor(level: Int): Int = (baseCost * (1.0 + level * 0.9)).toInt()
}

data class SkinDef(val id: String, val name: String, val asset: String, val cost: Int)
data class ArenaDef(val id: String, val name: String, val bg: String, val cost: Int, val difficulty: Float)

class GameStore(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("coin_pulse", Context.MODE_PRIVATE)

    // ---- Meta currency ----
    var coins: Int
        get() = sp.getInt("coins", 0)
        set(v) = sp.edit().putInt("coins", v).apply()

    // ---- Settings ----
    var soundOn: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) = sp.edit().putBoolean("sound", v).apply()
    var musicOn: Boolean
        get() = sp.getBoolean("music", true)
        set(v) = sp.edit().putBoolean("music", v).apply()
    var vibrationOn: Boolean
        get() = sp.getBoolean("vibration", true)
        set(v) = sp.edit().putBoolean("vibration", v).apply()

    // ---- Stats ----
    var bestScore: Int
        get() = sp.getInt("best_score", 0); set(v) = sp.edit().putInt("best_score", v).apply()
    var totalRuns: Int
        get() = sp.getInt("runs", 0); set(v) = sp.edit().putInt("runs", v).apply()
    var totalKills: Int
        get() = sp.getInt("kills", 0); set(v) = sp.edit().putInt("kills", v).apply()
    var bestWave: Int
        get() = sp.getInt("best_wave", 0); set(v) = sp.edit().putInt("best_wave", v).apply()
    var bestSurvivalMs: Long
        get() = sp.getLong("best_surv", 0); set(v) = sp.edit().putLong("best_surv", v).apply()
    var bestCombo: Int
        get() = sp.getInt("best_combo", 0); set(v) = sp.edit().putInt("best_combo", v).apply()

    /** Whether the in-game control tutorial has been shown. */
    var tutorialSeen: Boolean
        get() = sp.getBoolean("tutorial", false)
        set(v) = sp.edit().putBoolean("tutorial", v).apply()

    // ---- Player name ----
    var playerName: String
        get() = sp.getString("name", "Pulse Runner") ?: "Pulse Runner"
        set(v) = sp.edit().putString("name", v).apply()

    // ---- Upgrades ----
    fun upgradeLevel(u: Upgrade): Int = sp.getInt("up_${u.name}", 0)
    fun setUpgradeLevel(u: Upgrade, level: Int) = sp.edit().putInt("up_${u.name}", level).apply()

    // ---- Skins ----
    val skins = listOf(
        SkinDef("default", "Pulse Coin", "sprites/player_coin.png", 0),
        SkinDef("star", "Star Coin", "sprites/coin/0.png", 300),
        SkinDef("leaf", "Leaf Coin", "sprites/coin/1.png", 600),
        SkinDef("moon", "Moon Coin", "sprites/coin/2.png", 900),
    )
    var selectedSkin: String
        get() = sp.getString("skin", "default") ?: "default"
        set(v) = sp.edit().putString("skin", v).apply()
    fun isSkinUnlocked(id: String): Boolean =
        id == "default" || sp.getBoolean("skin_$id", false)
    fun unlockSkin(id: String) = sp.edit().putBoolean("skin_$id", true).apply()
    fun skinAsset(): String = skins.firstOrNull { it.id == selectedSkin }?.asset
        ?: "sprites/player_coin.png"

    // ---- Arenas ----
    val arenas = listOf(
        ArenaDef("main", "Energy Plains", "bg/arena_main.jpg", 0, 1.0f),
        ArenaDef("deep", "Crystal Depths", "bg/arena_deep.jpg", 500, 1.35f),
        ArenaDef("final", "Final Arena", "bg/arena_final.jpg", 1200, 1.8f),
    )
    var selectedArena: String
        get() = sp.getString("arena", "main") ?: "main"
        set(v) = sp.edit().putString("arena", v).apply()
    fun isArenaUnlocked(id: String): Boolean =
        id == "main" || sp.getBoolean("arena_$id", false)
    fun unlockArena(id: String) = sp.edit().putBoolean("arena_$id", true).apply()
    fun arena(): ArenaDef = arenas.firstOrNull { it.id == selectedArena } ?: arenas[0]

    /** Record end-of-run results and bank coins. */
    fun recordRun(score: Int, kills: Int, wave: Int, survivalMs: Long, coinsEarned: Int, combo: Int = 0) {
        coins += coinsEarned
        totalRuns += 1
        totalKills += kills
        if (score > bestScore) bestScore = score
        if (wave > bestWave) bestWave = wave
        if (survivalMs > bestSurvivalMs) bestSurvivalMs = survivalMs
        if (combo > bestCombo) bestCombo = combo
    }
}
