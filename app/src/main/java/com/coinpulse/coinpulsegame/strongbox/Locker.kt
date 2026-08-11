package com.coinpulse.coinpulsegame.strongbox

import android.content.Context
import android.content.SharedPreferences
import com.coinpulse.coinpulsegame.BuildConfig

/**
 * Everything the shell remembers between launches.
 *
 * Flags and timestamps live in ordinary preferences; anything that is a URL or
 * a token lives in the encrypted store. Both file names and every key inside
 * them are derived per application, so two applications built from this tree do
 * not share so much as a file name on disk.
 *
 * If the encrypted store cannot be opened — a missing crypto provider, a
 * keystore the device will not unlock — the URLs are held in memory for the
 * life of the process instead. They are never quietly written to the plain file:
 * an inaccessible keystore is a reason to forget a URL, not to expose it.
 */
internal class Locker(context: Context) {

    enum class Lane { UNSET, STREAM, GAME }

    private val open: SharedPreferences =
        context.applicationContext.getSharedPreferences(BuildConfig.BOX_MAIN, Context.MODE_PRIVATE)

    private val sealed = SealedStore(
        context = context,
        fileName = BuildConfig.BOX_SEALED,
        alias = BuildConfig.BOX_SEALED
    )

    /** Stands in for the sealed store on a device whose keystore will not work. */
    private val heldInMemory = HashMap<String, String?>()

    // ── lane ────────────────────────────────────────────────────────────────

    var lane: Lane
        get() = when (open.getString(BuildConfig.TAG_MODE, null)) {
            LANE_STREAM -> Lane.STREAM
            LANE_GAME -> Lane.GAME
            else -> Lane.UNSET
        }
        set(value) {
            val written = when (value) {
                Lane.STREAM -> LANE_STREAM
                Lane.GAME -> LANE_GAME
                Lane.UNSET -> null
            }
            open.edit().apply {
                if (written == null) remove(BuildConfig.TAG_MODE) else putString(BuildConfig.TAG_MODE, written)
            }.apply()
        }

    // ── destination ─────────────────────────────────────────────────────────

    var target: String?
        get() = guarded(BuildConfig.TAG_TARGET)
        set(value) = guard(BuildConfig.TAG_TARGET, value)

    var targetExpiry: Long
        get() = open.getLong(BuildConfig.TAG_TTL, 0L)
        set(value) = open.edit().putLong(BuildConfig.TAG_TTL, value).apply()

    fun targetUsable(): Boolean {
        val url = target
        if (url.isNullOrBlank()) return false
        val until = targetExpiry
        return until == 0L || System.currentTimeMillis() / 1000L < until
    }

    // ── push URL held for a cold start, read exactly once ───────────────────

    var parkedPush: String?
        get() = guarded(BuildConfig.TAG_PUSH)
        set(value) = guard(BuildConfig.TAG_PUSH, value)

    fun takeParkedPush(): String? = parkedPush.also { if (it != null) parkedPush = null }

    // ── notification prompt ─────────────────────────────────────────────────

    var promptGranted: Boolean
        get() = open.getBoolean(BuildConfig.TAG_ASK_OK, false)
        set(value) = open.edit().putBoolean(BuildConfig.TAG_ASK_OK, value).apply()

    var promptRefusedForGood: Boolean
        get() = open.getBoolean(BuildConfig.TAG_ASK_NO, false)
        set(value) = open.edit().putBoolean(BuildConfig.TAG_ASK_NO, value).apply()

    private var promptSilentUntil: Long
        get() = open.getLong(BuildConfig.TAG_ASK_AT, 0L)
        set(value) = open.edit().putLong(BuildConfig.TAG_ASK_AT, value).apply()

    fun promptIsDue(): Boolean {
        if (promptGranted || promptRefusedForGood) return false
        return System.currentTimeMillis() / 1000L >= promptSilentUntil
    }

    fun snoozePrompt() {
        promptSilentUntil = System.currentTimeMillis() / 1000L + BuildConfig.ASK_AGAIN_SEC
    }

    // ── messaging token ─────────────────────────────────────────────────────

    var messagingToken: String?
        get() = guarded(BuildConfig.TAG_TOKEN)
        set(value) = guard(BuildConfig.TAG_TOKEN, value)

    // ── where the keyboard came to rest, per orientation ────────────────────

    fun keyboardRest(upright: Boolean): Int =
        open.getInt(if (upright) BuildConfig.TAG_IME_UP else BuildConfig.TAG_IME_WIDE, 0)

    fun rememberKeyboardRest(upright: Boolean, height: Int) {
        open.edit()
            .putInt(if (upright) BuildConfig.TAG_IME_UP else BuildConfig.TAG_IME_WIDE, height)
            .apply()
    }

    // ── sealed access ───────────────────────────────────────────────────────

    private fun guarded(key: String): String? =
        if (sealed.usable) sealed.read(key) else heldInMemory[key]

    private fun guard(key: String, value: String?) {
        if (sealed.usable) sealed.write(key, value) else heldInMemory[key] = value
    }

    private companion object {
        // Written values are deliberately not the enum names: an enum renamed
        // later must not silently reset every install in the field.
        const val LANE_STREAM = "s"
        const val LANE_GAME = "g"
    }
}
