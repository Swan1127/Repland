package com.swan1127.repland.data.ai

import com.swan1127.repland.data.ai.AiHttpOutcome
import com.swan1127.repland.data.ai.AiHttpTransport
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * HTTPS transport shared by both build types. Callers check provider configuration and
 * explicit AI consent before submitting user data.
 *
 * `HttpURLConnection` rather than OkHttp/Retrofit: the project has neither dependency, and
 * adding one would mean a download for a single call shape.
 */
class UrlConnectionAiHttpTransport(
    private val connectTimeoutMillis: Int = 15_000,
    /**
     * Per call, not per turn. This used to be 90s, which meant a stalled provider could
     * hold the typing bubble for a minute and a half while `GroupChatEngine`'s own turn
     * budget waited on it. A single answer that takes longer than this is reported as
     * `TIMEOUT`, which the UI shows; silence is not a state anyone should sit through.
     */
    private val readTimeoutMillis: Int = 30_000,
) : AiHttpTransport {

    override suspend fun post(url: String, apiKey: String, jsonBody: String): AiHttpOutcome =
        withContext(Dispatchers.IO) { exchange(url, apiKey, "POST", jsonBody) }

    override suspend fun get(url: String, apiKey: String): AiHttpOutcome =
        withContext(Dispatchers.IO) { exchange(url, apiKey, "GET", null) }

    private fun exchange(url: String, apiKey: String, method: String, body: String?): AiHttpOutcome {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            // An error status carries its explanation on errorStream; reading inputStream
            // there would throw and lose the provider's own message.
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            AiHttpOutcome.Responded(status, stream.readTextOrEmpty())
        } catch (timeout: SocketTimeoutException) {
            AiHttpOutcome.TimedOut
        } catch (io: IOException) {
            // DNS failure, TLS rejection, connection reset — no HTTP exchange completed.
            AiHttpOutcome.Unreachable
        } finally {
            connection?.disconnect()
        }
    }

    private fun InputStream?.readTextOrEmpty(): String =
        this?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
}
