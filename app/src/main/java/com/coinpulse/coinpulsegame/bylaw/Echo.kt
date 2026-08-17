package com.coinpulse.coinpulsegame.bylaw

import android.util.Log
import com.coinpulse.coinpulsegame.BuildConfig

/**
 * The only place the shell writes to logcat. The guard sits on a compile-time
 * constant, so nothing reaches logcat from a release build.
 *
 * Stopping the write is not the same as removing the call: the message is an
 * argument, so it would still sit in the shipped DEX where `strings` finds it.
 * What removes both is the `-assumenosideeffects` rule naming these three
 * functions in `proguard-rules.pro`. Add a function here and it needs a line
 * there too, or its messages ship.
 */
internal object Echo {

    fun note(from: String, what: String) {
        if (BuildConfig.DEBUG) Log.i(from, what)
    }

    fun odd(from: String, what: String) {
        if (BuildConfig.DEBUG) Log.w(from, what)
    }

    fun odd(from: String, what: String, cause: Throwable) {
        if (BuildConfig.DEBUG) Log.w(from, what, cause)
    }

    fun bad(from: String, what: String, cause: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        if (cause == null) Log.e(from, what) else Log.e(from, what, cause)
    }
}
