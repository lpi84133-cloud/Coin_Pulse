package com.coinpulse.coinpulsegame.strongbox

import com.coinpulse.coinpulsegame.BuildConfig

/**
 * Reverses what the build script did to the endpoint, the tracker key and the
 * rest before it wrote them into BuildConfig.
 *
 * Every parameter — the block of key bytes, the ramp step and the bias the
 * chain starts from — is derived per application from the seed, so the same
 * plaintext encodes to a different byte sequence in every build. Nothing here
 * knows what it is decoding, and it is never handed a literal: the only inputs
 * are BuildConfig arrays.
 */
internal object Cloak {

    fun open(hidden: IntArray): String {
        if (hidden.isEmpty()) return ""

        val block = BuildConfig.SEAL_BYTES
        val step = BuildConfig.SEAL_STEP
        val bias = BuildConfig.SEAL_BIAS

        val plain = ByteArray(hidden.size)
        var carry = bias and 0xFF

        for (i in hidden.indices) {
            val cipher = hidden[i] and 0xFF
            plain[i] = (cipher xor keyStreamAt(i, block, step, bias) xor carry).toByte()
            carry = cipher
        }
        return String(plain, Charsets.UTF_8)
    }

    private fun keyStreamAt(index: Int, block: IntArray, step: Int, bias: Int): Int {
        val ramp = ((index * step) + bias + (index / block.size)) and 0xFF
        val turn = index % 5
        val rolled = ((ramp shl turn) or (ramp ushr (8 - turn))) and 0xFF
        return (block[index % block.size] and 0xFF) xor rolled
    }
}
