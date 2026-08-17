package com.coinpulse.coinpulsegame.relaynet

import com.coinpulse.coinpulsegame.bylaw.Agent
import com.coinpulse.coinpulsegame.bylaw.Echo
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The shell's whole HTTP surface: one POST to the config endpoint, one GET for
 * the direct attribution lookup, and one download for a notification image.
 * That is little enough that the platform client does the job, and it keeps a
 * third-party networking library — with its own recognisable default
 * User-Agent and its own footprint in the binary — out of the build.
 *
 * Every caller is expected to be on an IO dispatcher already; nothing here
 * switches threads on its own.
 */
internal object Wire {

    /** A reply that arrived. Its absence (null) means nobody answered at all. */
    data class Reply(val status: Int, val text: String)

    fun postJson(endpoint: String, payload: String, timeoutMs: Int): Reply? =
        speak(endpoint, timeoutMs) { link ->
            link.requestMethod = "POST"
            link.doOutput = true
            link.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            link.setRequestProperty("Accept", "application/json")
            link.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        }

    fun get(endpoint: String, headers: Map<String, String>, timeoutMs: Int): Reply? =
        speak(endpoint, timeoutMs) { link ->
            link.requestMethod = "GET"
            headers.forEach { (name, value) -> link.setRequestProperty(name, value) }
        }

    fun download(endpoint: String, timeoutMs: Int, limitBytes: Int): ByteArray? = try {
        val link = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", Agent.line)
        }
        try {
            if (link.responseCode !in 200..299) null
            else link.inputStream.use { drainBytes(it, limitBytes) }
        } finally {
            link.disconnect()
        }
    } catch (failure: Exception) {
        Echo.odd(TAG, "download failed: ${failure.javaClass.simpleName}")
        null
    }

    private inline fun speak(
        endpoint: String,
        timeoutMs: Int,
        prepare: (HttpURLConnection) -> Unit
    ): Reply? = try {
        val link = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", Agent.line)
        }
        try {
            prepare(link)
            val status = link.responseCode
            // A 4xx or 5xx puts the body on the error stream, and that body is
            // still an answer: the endpoint saying no is exactly what a 404 is.
            val stream: InputStream? =
                if (status in 200..299) link.inputStream else link.errorStream
            val text = stream?.use { String(drainBytes(it, TEXT_LIMIT), Charsets.UTF_8) }.orEmpty()
            Reply(status, text)
        } finally {
            link.disconnect()
        }
    } catch (failure: Exception) {
        Echo.odd(TAG, "request never landed: ${failure.javaClass.simpleName}")
        null
    }

    private fun drainBytes(source: InputStream, limit: Int): ByteArray {
        val sink = ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = source.read(chunk)
            if (read <= 0) break
            total += read
            if (total > limit) break
            sink.write(chunk, 0, read)
        }
        return sink.toByteArray()
    }

    private const val TAG = "Wire"
    private const val TEXT_LIMIT = 512 * 1024
}
