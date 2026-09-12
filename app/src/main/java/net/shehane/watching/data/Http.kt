package net.shehane.watching.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Four GET endpoints and two Drive calls do not justify a networking library and
 * the version matching that comes with one. HttpURLConnection is in the platform,
 * and kotlinx.serialization parses what comes back.
 */
object Http {

    class HttpError(val code: Int, val body: String) :
        Exception("HTTP $code${if (body.isBlank()) "" else ": ${body.take(300)}"}")

    suspend fun getString(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        request("GET", url, headers, null).decodeToString()
    }

    suspend fun getBytes(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): ByteArray = withContext(Dispatchers.IO) {
        request("GET", url, headers, null)
    }

    suspend fun send(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        contentType: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val h = if (contentType != null) headers + ("Content-Type" to contentType) else headers
        request(method, url, h, body).decodeToString()
    }

    private fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
    ): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            if (body != null) {
                doOutput = true
                setFixedLengthStreamingMode(body.size)
            }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val bytes = stream?.let { BufferedInputStream(it).use(::drain) } ?: ByteArray(0)
            if (code !in 200..299) throw HttpError(code, bytes.decodeToString())
            return bytes
        } finally {
            conn.disconnect()
        }
    }

    private fun drain(input: java.io.InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}
