package com.coinpulse.coinpulsegame.pulsegate

import android.app.Application
import com.coinpulse.coinpulsegame.BuildConfig
import com.coinpulse.coinpulsegame.charter.Echo
import com.coinpulse.coinpulsegame.charter.HostGate
import com.coinpulse.coinpulsegame.dispatchbox.AlertLane
import com.coinpulse.coinpulsegame.relaynet.TrackHub
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Process start. Two jobs, and the second one is the reason this class exists
 * at all.
 *
 * The tracker is introduced here and nowhere else. Introducing it registers the
 * activity-lifecycle callbacks the SDK uses to notice the app reached the
 * foreground, and doing that from an activity that is already resumed means the
 * SDK never sees the event: it queues the launch and sends it at the next
 * activity transition, which in this app is the game, roughly half a minute
 * later — long after the launch has decided where to go and written the answer
 * down. Nothing is sent from here; the call that speaks happens in the launcher,
 * after a connection has been confirmed.
 */
class PulseApp : Application() {

    internal lateinit var tracker: TrackHub
        private set

    override fun onCreate() {
        super.onCreate()

        runCatching {
            FirebaseApp.initializeApp(this)
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                if (BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance()
                else PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }.onFailure {
            // Not fatal: messaging is the only Firebase service the shell uses,
            // and the launch decision does not depend on it at all.
            Echo.odd(TAG, "Firebase did not come up", it)
        }

        // Before the first message rather than on it: see AlertLane.
        AlertLane.open(this)

        HostGate.complainIfOpen()

        tracker = TrackHub(this)
        tracker.wireUp()
    }

    private companion object {
        const val TAG = "PulseApp"
    }
}
