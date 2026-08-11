package com.coinpulse.coinpulsegame.strongbox

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.coinpulse.coinpulsegame.charter.Echo
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * A small encrypted key-value store for the handful of strings that should not
 * sit in a readable preferences file: the page this install was given and the
 * messaging token.
 *
 * The key never leaves the platform keystore — it is generated there, used
 * there, and cannot be exported even on a rooted device — so what lands on disk
 * is a nonce followed by AES-GCM ciphertext and nothing that can be read
 * without the hardware that wrote it.
 *
 * When the keystore is unavailable, which happens on a small number of devices
 * with a broken provider, [usable] stays false and the caller keeps the values
 * in memory for the life of the process. Falling back to writing them in clear
 * would defeat the point of having this file at all.
 */
internal class SealedStore(context: Context, fileName: String, private val alias: String) {

    private val shelf: SharedPreferences =
        context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)

    private val key: SecretKey? = obtainKey()

    val usable: Boolean get() = key != null

    fun read(name: String): String? {
        val secret = key ?: return null
        val stored = shelf.getString(name, null) ?: return null
        return runCatching {
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            val nonceSize = blob[0].toInt()
            require(nonceSize in 1..NONCE_LIMIT && blob.size > nonceSize + 1)

            val nonce = blob.copyOfRange(1, 1 + nonceSize)
            val body = blob.copyOfRange(1 + nonceSize, blob.size)

            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(TAG_BITS, nonce))
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.onFailure {
            // A value that will not open again is a value that is gone: a
            // reinstalled keystore or a restored backup leaves exactly this.
            Echo.odd(TAG, "a stored value would not open — dropping it")
            shelf.edit().remove(name).apply()
        }.getOrNull()
    }

    fun write(name: String, value: String?) {
        val secret = key ?: return
        if (value == null) {
            shelf.edit().remove(name).apply()
            return
        }
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, secret)
            val nonce = cipher.iv
            val body = cipher.doFinal(value.toByteArray(Charsets.UTF_8))

            val blob = ByteArray(1 + nonce.size + body.size)
            blob[0] = nonce.size.toByte()
            System.arraycopy(nonce, 0, blob, 1, nonce.size)
            System.arraycopy(body, 0, blob, 1 + nonce.size, body.size)

            shelf.edit().putString(name, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
        }.onFailure { Echo.odd(TAG, "a value would not seal: ${it.javaClass.simpleName}") }
    }

    private fun obtainKey(): SecretKey? = runCatching {
        val keystore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keystore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: mintKey()
    }.onFailure {
        Echo.odd(TAG, "the platform keystore is not usable here", it)
    }.getOrNull()

    private fun mintKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // Deliberately not tied to the screen lock: the shell has to be
                // able to read the page it was given while the device is locked,
                // which is exactly when a push arrives.
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "SealedStore"
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val NONCE_LIMIT = 32
    }
}
