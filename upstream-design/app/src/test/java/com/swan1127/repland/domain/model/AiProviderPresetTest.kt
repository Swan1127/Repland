package com.swan1127.repland.domain.model

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AiProviderPreset] is data, so most of it cannot be wrong in an interesting way — but two
 * properties can, and both fail silently at runtime rather than at compile time:
 *
 * 1. An address that is not a usable request prefix (a missing `/v1`, a typo'd host) looks
 *    perfectly fine in a dropdown and only breaks when the user presses "test connection".
 * 2. [AiProviderPreset.matching] must round-trip. If it does not, the settings screen opens
 *    showing "自定义" for a configuration the user picked from the list.
 *
 * The addresses themselves are pinned against each provider's own documentation as of
 * 2026-10-06; see `docs/竞品对标与打磨计划.md` §4.2 for the sources.
 */
class AiProviderPresetTest {

    @Test
    fun `every configured preset carries a well-formed https address`() {
        assertTrue("no configured presets to check", AiProviderPreset.configured.isNotEmpty())
        AiProviderPreset.configured.forEach { preset ->
            val uri = URI(preset.baseUrl)
            assertEquals("${preset.name} is not https", "https", uri.scheme)
            assertTrue("${preset.name} has no host", !uri.host.isNullOrBlank())
        }
    }

    @Test
    fun `no configured address ends with a slash`() {
        // `SecureAiProviderConfigRepository.normalizeBaseUrl` strips one trailing slash
        // before storing, so a preset written with one would round-trip into a value that
        // no longer matches its own preset. Keeping them slash-free makes that impossible.
        AiProviderPreset.configured.forEach { preset ->
            assertTrue("${preset.name} ends with a slash", !preset.baseUrl.endsWith("/"))
        }
    }

    @Test
    fun `no two presets share an address`() {
        // A copy-paste that leaves two entries pointing at the same host would make
        // `matching` ambiguous and would silently hide one provider from the picker.
        val addresses = AiProviderPreset.configured.map { it.baseUrl }
        assertEquals(addresses.size, addresses.toSet().size)
    }

    @Test
    fun `every display name is non-blank and unique`() {
        // These strings are the only thing the user sees in the picker.
        val names = AiProviderPreset.entries.map { it.displayName }
        assertTrue(names.none { it.isBlank() })
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `custom is the only preset without an address`() {
        assertEquals(listOf(AiProviderPreset.CUSTOM), AiProviderPreset.entries.filter { it.baseUrl.isBlank() })
    }

    @Test
    fun `matching resolves each configured preset back to itself`() {
        AiProviderPreset.configured.forEach { preset ->
            assertEquals(preset, AiProviderPreset.matching(preset.baseUrl))
        }
    }

    @Test
    fun `matching tolerates the trailing slash the repository strips`() {
        // This is the exact round-trip the settings screen performs: the user picks a
        // preset, `save` normalises the address, and the screen re-derives the selection
        // from what was stored. Without this the selection would reset to 自定义 on every
        // save.
        AiProviderPreset.configured.forEach { preset ->
            assertEquals(preset, AiProviderPreset.matching("${preset.baseUrl}/"))
        }
    }

    @Test
    fun `the built-in default config resolves to a real preset, not custom`() {
        // `AiProviderConfig()` defaults its address to `DEFAULT_AGNES_BASE_URL`. If that
        // address stopped matching the AGNES entry, a fresh install would open the
        // settings screen showing 自定义 even though the user never typed anything.
        val default = AiProviderConfig()
        assertEquals(AiProviderPreset.AGNES, AiProviderPreset.matching(default.baseUrl))
    }

    @Test
    fun `matching falls back to custom for the empty string`() {
        assertEquals(AiProviderPreset.CUSTOM, AiProviderPreset.matching(""))
        assertEquals(AiProviderPreset.CUSTOM, AiProviderPreset.matching("   "))
    }

    @Test
    fun `matching falls back to custom for an unrecognised address`() {
        assertEquals(AiProviderPreset.CUSTOM, AiProviderPreset.matching("https://example.invalid/v1"))
    }

    @Test
    fun `matching is exact, so a near miss stays custom`() {
        // A prefix match would silently rewrite `https://api.openai.com` into the OpenAI
        // preset and then append `/chat/completions` to the wrong path. The address is the
        // user's to control; only an exact hit may claim a preset.
        assertNotEquals(AiProviderPreset.OPENAI, AiProviderPreset.matching("https://api.openai.com"))
        assertNotEquals(AiProviderPreset.OPENAI, AiProviderPreset.matching("https://api.openai.com/v2"))
        assertNotEquals(AiProviderPreset.DEEPSEEK, AiProviderPreset.matching("https://api.deepseek.com/v1"))
    }

    @Test
    fun `matching trims surrounding whitespace`() {
        // A pasted address routinely arrives with a trailing newline or space.
        assertEquals(AiProviderPreset.DEEPSEEK, AiProviderPreset.matching("  https://api.deepseek.com  "))
    }
}
