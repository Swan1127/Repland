package com.swan1127.repland

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.domain.model.*
import com.swan1127.repland.ui.AiProviderSettingsDialog
import com.swan1127.repland.ui.ai.AiProviderConnectionTest
import com.swan1127.repland.ui.theme.ReplandTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiProviderSettingsUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun deepseek_preset_edits_metadata_only_until_explicit_save() {
        var saved: Triple<String, String, String>? = null
        rule.setContent {
            ReplandTheme { AiProviderSettingsDialog(AiProviderConfig(hasApiKey = true), null,
                AiProviderConnectionTest.Idle, {}, { url, model, key -> saved = Triple(url, model, key) }, {}, {}) }
        }
        rule.onNodeWithTag("ai-provider-key").performScrollTo().performTextInput("invalid-test-only")
        rule.onNodeWithTag("ai-provider-deepseek").performScrollTo().performClick()
        assertNull(saved)
        rule.onNodeWithTag("ai-provider-save").performClick()
        assertEquals(Triple("https://api.deepseek.com", "deepseek-flash", ""), saved)
    }

    @Test fun large_text_dark_mode_keeps_fields_errors_and_actions_reachable() {
        var tested = false
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                ReplandTheme(darkTheme = true) { AiProviderSettingsDialog(AiProviderConfig(hasApiKey = true),
                    "地址变化需重新输入密钥", AiProviderConnectionTest.Idle, {}, { _, _, _ -> }, {}, { tested = true }) }
            }
        }
        rule.onNodeWithTag("ai-provider-endpoint").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("ai-provider-model").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("ai-provider-key").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("地址变化需重新输入密钥").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("ai-provider-test").assertIsDisplayed().performClick()
        assertTrue(tested)
    }
}
