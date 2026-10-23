package com.swan1127.repland.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AiPreferences.hasExplicitConsent] is the single input to the consent gate that
 * [PlanningAgentWorkflow] checks before any provider call, so its exact rule is worth pinning:
 * consent is the presence of a timestamp, and it is independent of the on/off switch.
 */
class AiPreferencesTest {

    @Test
    fun `the defaults carry no consent and the switch is off`() {
        val defaults = AiPreferences()
        assertFalse(defaults.isEnabled)
        assertNull(defaults.consentedAtEpochMillis)
        assertFalse(defaults.hasExplicitConsent)
    }

    @Test
    fun `a consent timestamp means consent`() {
        assertTrue(AiPreferences(isEnabled = true, consentedAtEpochMillis = 1_700_000_000_000L).hasExplicitConsent)
    }

    @Test
    fun `a zero timestamp still counts, because the rule is presence not magnitude`() {
        // A check written as `> 0` would silently treat an epoch-zero consent as absent.
        assertTrue(AiPreferences(isEnabled = true, consentedAtEpochMillis = 0L).hasExplicitConsent)
    }

    @Test
    fun `enabling the switch without a timestamp is not consent`() {
        assertFalse(AiPreferences(isEnabled = true, consentedAtEpochMillis = null).hasExplicitConsent)
    }

    @Test
    fun `a timestamp with the switch off is still consent`() {
        // Consent and enablement are separate facts: the user can withdraw the switch later
        // without the recorded consent disappearing.
        assertTrue(AiPreferences(isEnabled = false, consentedAtEpochMillis = 1L).hasExplicitConsent)
    }

    @Test
    fun `the consent flag tracks the timestamp on a copy`() {
        val original = AiPreferences()
        assertFalse(original.hasExplicitConsent)
        assertEquals(true, original.copy(consentedAtEpochMillis = 5L).hasExplicitConsent)
    }
}
