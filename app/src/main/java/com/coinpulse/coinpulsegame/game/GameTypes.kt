package com.coinpulse.coinpulsegame.game

/** Temporary upgrades chosen between waves (roguelite build). */
enum class RunUpgrade(val title: String, val desc: String, val icon: String) {
    POWER("Overload", "+35% pulse damage", "crystal"),
    CHARGE("Quick Charge", "Charge 25% faster", "star"),
    RADIUS("Wide Wave", "+25% pulse radius", "powerup"),
    MOVE("Momentum", "+20% move speed", "powerup"),
    MAGNET("Attractor", "+40% magnet range", "powerup"),
    HEAL("Repair", "Restore 2 energy", "fruit"),
    DOUBLE("Echo Wave", "Every pulse fires twice", "bell"),
}

data class GameResult(
    val victory: Boolean,
    val score: Int,
    val kills: Int,
    val wave: Int,
    val bestCombo: Int,
    val survivalMs: Long,
    val coinsEarned: Int,
)

enum class EnemyKind { DRIFTER, SPRINTER, BRUTE, GUARDIAN }
