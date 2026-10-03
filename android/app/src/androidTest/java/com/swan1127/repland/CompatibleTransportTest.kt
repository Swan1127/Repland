package com.swan1127.repland

import com.swan1127.repland.data.ai.*
import com.swan1127.repland.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.net.HttpURLConnection
import java.net.URL
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CompatibleTransportTest {
    private val provider = AiProviderSecret("https://example.invalid/v1", "test-model", "test-only-not-a-real-key")
    private class Fake(val code: Int = 200, val body: InputStream = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}".byteInputStream()) : HttpURLConnection(URL("https://example.invalid")) {
        val sent = java.io.ByteArrayOutputStream()
        var disconnected = false
        var onDisconnect: () -> Unit = {}
        override fun connect() {}
        override fun usingProxy() = false
        override fun disconnect() { disconnected = true; onDisconnect() }
        override fun getOutputStream() = sent
        override fun getInputStream() = body
        override fun getResponseCode() = code
    }
    @Test fun request_is_bounded_https_and_redirects_are_disabled() = runBlocking {
        val fake = Fake()
        var target = ""
        val text = CompatibleChatTransport { target = it.toString(); fake }.complete(provider, "system", JSONObject().put("kind", "test"))
        assertEquals("ok", text)
        assertEquals("https://example.invalid/v1/chat/completions", target)
        assertFalse(fake.instanceFollowRedirects)
        assertEquals("Bearer ${provider.apiKey}", fake.getRequestProperty("Authorization"))
        assertTrue(fake.disconnected)
        assertFalse(fake.sent.toString("UTF-8").contains(provider.apiKey))
    }
    @Test fun unsafe_urls_and_oversized_requests_never_open_a_connection() = runBlocking {
        var calls = 0
        val transport = CompatibleChatTransport { calls++; Fake() }
        listOf("http://example.invalid", "https://user@example.invalid", "https://example.invalid?key=value", "https://example.invalid#fragment").forEach {
            assertTrue(runCatching { transport.complete(provider.copy(baseUrl = it), "system", JSONObject()) }.isFailure)
        }
        assertTrue(runCatching { transport.complete(provider, "x".repeat(140_000), JSONObject()) }.isFailure)
        assertEquals(0, calls)
    }
    @Test fun http_failures_are_classified_without_following_redirects() = runBlocking {
        listOf(401 to AiAdvisorFailureReason.AUTHENTICATION_FAILURE, 429 to AiAdvisorFailureReason.RATE_LIMITED,
            503 to AiAdvisorFailureReason.REMOTE_FAILURE, 302 to AiAdvisorFailureReason.TRANSPORT_FAILURE).forEach { (code, expected) ->
            val failure = runCatching { CompatibleChatTransport { Fake(code) }.complete(provider, "system", JSONObject()) }.exceptionOrNull()
            assertEquals(expected, (failure as ProviderHttpFailure).reason)
        }
    }
    @Test fun oversized_responses_are_rejected() = runBlocking {
        val fake = Fake(body = "x".repeat(262_145).byteInputStream())
        val failure = runCatching { CompatibleChatTransport { fake }.complete(provider, "system", JSONObject()) }.exceptionOrNull()
        assertEquals(AiAdvisorFailureReason.INVALID_RESPONSE, (failure as ProviderHttpFailure).reason)
        assertTrue(fake.disconnected)
    }
    @Test fun cancellation_disconnects_the_active_request() = runBlocking {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val fake = Fake(body = object : InputStream() { override fun read(): Int { entered.countDown(); released.await(5, TimeUnit.SECONDS); return -1 } })
        fake.onDisconnect = { released.countDown() }
        val job = launch(Dispatchers.Default) { CompatibleChatTransport { fake }.complete(provider, "system", JSONObject()) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertTrue(fake.disconnected)
        assertTrue(job.isCancelled)
    }
    @Test fun changing_service_without_new_key_is_rejected_and_revision_is_monotonic() = runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "provider-test-${java.util.UUID.randomUUID()}"
        val repo = com.swan1127.repland.data.security.SecureAiProviderConfigRepository(context, name)
        try {
            repo.save(provider.baseUrl, provider.model, provider.apiKey)
            val before = repo.observe().first()
            assertTrue(runCatching { repo.save("https://different.invalid/v1", "model", "") }.isFailure)
            repo.save(provider.baseUrl, provider.model, "")
            val after = repo.observe().first()
            assertTrue(after.updatedAtEpochMillis!! > before.updatedAtEpochMillis!!)
            assertEquals(provider.baseUrl, repo.readSecret()!!.baseUrl)
        } finally { context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE).edit().clear().commit() }
    }
}
