package com.coinpulse.coinpulsegame.dispatchbox

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.coinpulse.coinpulsegame.BuildConfig
import com.coinpulse.coinpulsegame.charter.Echo

/**
 * The one channel this application posts on.
 *
 * It is opened at process start rather than at the first message, because most
 * cards are not drawn by us: a message carrying a `notification` block is drawn
 * by the Firebase SDK, on the channel named in the manifest, and a channel that
 * does not exist yet at that moment is silently swapped for the system's
 * catch-all — a card with default importance, no heads-up, no sound, filed
 * under "Miscellaneous" in the user's settings for the rest of the install.
 */
internal object AlertLane {

    fun open(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (manager.getNotificationChannel(BuildConfig.ALERT_CHANNEL) != null) return

        runCatching {
            manager.createNotificationChannel(
                NotificationChannel(
                    BuildConfig.ALERT_CHANNEL,
                    BuildConfig.ALERT_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    enableLights(true)
                    enableVibration(true)
                    setShowBadge(true)
                }
            )
        }.onFailure { Echo.odd("AlertLane", "channel not created: ${it.javaClass.simpleName}") }
    }
}
