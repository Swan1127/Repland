package com.swan1127.repland.data.ai

import com.swan1127.repland.domain.model.AiAdvisorFailureReason
import com.swan1127.repland.domain.model.AiProviderSecret
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class ProviderHttpFailure(val reason: AiAdvisorFailureReason) : Exception()

/** One bounded HTTPS request; no redirect, logging, retries, or database capability. */
internal class CompatibleChatTransport(
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    suspend fun complete(provider: AiProviderSecret, instruction: String, payload: JSONObject): String = withContext(Dispatchers.IO) {
        val uri = URI(provider.baseUrl)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null)
        val bytes = JSONObject().put("model", provider.model).put("temperature", 0.1)
            .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", instruction))
                .put(JSONObject().put("role", "user").put("content", payload.toString())))
            .toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 131_072) { "Request exceeds bounded context." }
        val connection = open(URL(provider.baseUrl.trimEnd('/') + "/chat/completions"))
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { connection.disconnect() }
            try {
                connection.instanceFollowRedirects = false
                connection.requestMethod = "POST"
                connection.connectTimeout = 12_000
                connection.readTimeout = 45_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
                if (!continuation.isActive) return@suspendCancellableCoroutine
                connection.outputStream.use { it.write(bytes) }
                val code = connection.responseCode
                if (code !in 200..299) throw ProviderHttpFailure(when (code) {
                    401, 403 -> AiAdvisorFailureReason.AUTHENTICATION_FAILURE
                    429 -> AiAdvisorFailureReason.RATE_LIMITED
                    in 500..599 -> AiAdvisorFailureReason.REMOTE_FAILURE
                    else -> AiAdvisorFailureReason.TRANSPORT_FAILURE
                })
                val body = connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > 262_144) throw ProviderHttpFailure(AiAdvisorFailureReason.INVALID_RESPONSE)
                        output.write(buffer, 0, count)
                    }
                    output.toString("UTF-8")
                }
                val content = JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").get("content")
                val text = when (content) {
                    is String -> content
                    is JSONArray -> (0 until content.length()).joinToString("") { content.getJSONObject(it).getString("text") }
                    else -> throw org.json.JSONException("Missing completion text")
                }
                if (continuation.isActive) continuation.resume(text)
            } catch (failure: Exception) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            } finally { connection.disconnect() }
        }
    }
}
