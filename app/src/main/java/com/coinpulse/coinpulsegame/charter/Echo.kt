package com.coinpulse.coinpulsegame.charter

import android.util.Log
import com.coinpulse.coinpulsegame.BuildConfig

/**
 * The only place the shell writes to logcat. The guard sits on a compile-time
 * constant, so in a minified release build every call site collapses to nothing
 * and no launch decision, request body or URL can leak from a user's device.
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
