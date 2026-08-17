package com.coinpulse.coinpulsegame.dispatchbox

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.coinpulse.coinpulsegame.BuildConfig
import com.coinpulse.coinpulsegame.R
import com.coinpulse.coinpulsegame.bylaw.Echo
import com.coinpulse.coinpulsegame.bylaw.HostGate
import com.coinpulse.coinpulsegame.MainActivity
import com.coinpulse.coinpulsegame.relaynet.Wire
import com.coinpulse.coinpulsegame.coffer.Locker
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Incoming messages.
 *
 * Which code runs when a notification is tapped depends on the payload, not on
 * anything written here. A data-only message always arrives at
 * [onMessageReceived]; a message that carries a `notification` block is drawn
 * by the Firebase SDK itself whenever the app is not in the foreground, and
 * this service is never called at all. That second shape is the one every real
 * user hits, and its tap opens the plain launcher intent with the data payload
 * attached as ordinary string extras — which is why [MainActivity] reads both
 * shapes rather than only its own.
 *
 * The rules for a URL, in order:
 *   * a URL the host list refuses never becomes a link, only text;
 *   * with the shell alive it goes straight there and no notification is
 *     posted — the user asked for the page, not for a card in the shade;
 *   * otherwise it is parked for the next cold start, consumed once;
 *   * an install that settled on the game keeps the game. It gets the text and
 *     the tap opens the launcher, which will keep it in the game. Flipping a
 *     native install into a WebView after the fact is a store-review problem,
 *     not a feature.
 */
class PushGate : FirebaseMessagingService() {

    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        worker.cancel()
        super.onDestroy()
    }

    /**
     * The registration token, which the SDK now marks as legacy in favour of
     * installation-id targeting. It stays because the backend that decides
     * where this install goes is asked with `push_token`, and whoever sends the
     * campaigns addresses tokens: moving to the new shape is a change on both
     * sides at once, not a change here.
     */
    @Deprecated("Kept while the backend targets registration tokens")
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Locker(applicationContext).messagingToken = token
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val payload = message.data
        val drawn = message.notification
        val title = payload["title"] ?: drawn?.title ?: return
        val body = payload["body"] ?: drawn?.body ?: return
        val picture = payload["image"] ?: drawn?.imageUrl?.toString().orEmpty()

        val named = (payload["url"] ?: payload["link"]).orEmpty().trim()
        val usable = named.isNotEmpty() && HostGate.permits(named)
        if (named.isNotEmpty() && !usable) {
            Echo.odd(TAG, "the URL in this push is not on the host list — text only")
        }

        val locker = Locker(applicationContext)
        val lane = locker.lane

        if (usable && lane == Locker.Lane.STREAM && LiveLink.listener != null) {
            val taken = runCatching { LiveLink.offer(named) }.getOrDefault(false)
            if (taken) return
        }

        val parked = if (usable && lane != Locker.Lane.GAME) named else ""
        if (parked.isNotEmpty()) locker.parkedPush = parked

        worker.launch { post(title, body, parked, picture) }
    }

    private suspend fun post(title: String, body: String, url: String, picture: String) {
        AlertLane.open(applicationContext)

        val tap = Intent(applicationContext, MainActivity::class.java).apply {
            // No CLEAR_TOP: it would destroy the very shell this URL may be
            // handed to a moment later.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_CAME_FROM_PUSH, true)
            if (url.isNotEmpty()) putExtra(MainActivity.EXTRA_PUSH_TARGET, url)
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            freshRequestCode(),
            tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val card = NotificationCompat.Builder(applicationContext, BuildConfig.ALERT_CHANNEL)
            .setSmallIcon(R.drawable.pulse_alert_mark)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val art = if (picture.isBlank()) null else fetchArt(picture)
        if (art != null) {
            card.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(art)
                    .bigLargeIcon(null as Bitmap?)
            )
        } else {
            card.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        withContext(Dispatchers.Main) {
            runCatching {
                NotificationManagerCompat.from(applicationContext).notify(nextId(), card.build())
            }.onFailure { Echo.odd(TAG, "notification not posted: ${it.javaClass.simpleName}") }
        }
    }

    private suspend fun fetchArt(url: String): Bitmap? = withContext(Dispatchers.IO) {
        val bytes = Wire.download(url, ART_TIMEOUT_MS, ART_LIMIT_BYTES) ?: return@withContext null
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
    }

    /** Distinct per notification, so two arrivals do not share a PendingIntent. */
    private fun freshRequestCode(): Int = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()

    private fun nextId(): Int = counter.getAndIncrement()

    private companion object {
        const val TAG = "PushGate"
        const val ART_TIMEOUT_MS = 8_000
        const val ART_LIMIT_BYTES = 3 * 1024 * 1024
        val counter = AtomicInteger(4_100)
    }
}
