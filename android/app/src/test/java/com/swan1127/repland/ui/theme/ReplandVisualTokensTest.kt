package com.swan1127.repland.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.swan1127.repland.domain.model.TaskCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplandVisualTokensTest {
    @Test fun lightSurfacesUseWarmPaperAndCreamRatherThanPureWhite() {
        val scheme = replandColorScheme(darkTheme = false)
        assertEquals(Color(0xFFF8F6F1), scheme.background)
        assertEquals(Color(0xFFFFFCF6), scheme.surfaceContainerLow)
        assertEquals(Color(0xFF263D43), scheme.onSurface)
    }

    @Test fun darkSurfacesHaveTheirOwnReadableTealPalette() {
        val scheme = replandColorScheme(darkTheme = true)
        assertEquals(Color(0xFF152226), scheme.background)
        assertEquals(Color(0xFF1D3033), scheme.surfaceContainerLow)
        assertTrue(scheme.surfaceContainerLow.luminance() > scheme.background.luminance())
        assertTrue(contrast(scheme.onSurface, scheme.surfaceContainerLow) >= 4.5f)
        assertTrue(contrast(scheme.onSurfaceVariant, scheme.surfaceContainerHigh) >= 4.5f)
    }

    @Test fun primaryActionsRemainPaleMintWithAccessibleText() {
        assertEquals(Color(0xFFDAF2E4), LightVisuals.mainAction)
        assertTrue(contrast(LightVisuals.onMainAction, LightVisuals.mainAction) >= 4.5f)
        assertTrue(contrast(DarkVisuals.onMainAction, DarkVisuals.mainAction) >= 4.5f)
    }

    @Test fun focusAndProfileTextAreReadableInBothModes() {
        listOf(LightVisuals, DarkVisuals).forEach { visuals ->
            assertTrue(contrast(visuals.focusText, visuals.focusSurface) >= 4.5f)
            assertTrue(contrast(visuals.focusMuted, visuals.focusSurface) >= 4.5f)
            assertTrue(contrast(visuals.profileText, visuals.profileSurface) >= 4.5f)
        }
    }

    @Test fun semanticTaskAccentsIncludeTheUnspecifiedCategory() {
        assertEquals(0, taskAccentIndex(TaskCategory.COURSE))
        assertEquals(1, taskAccentIndex(TaskCategory.EXTRACURRICULAR))
        assertEquals(2, taskAccentIndex(TaskCategory.OFFICE))
        assertEquals(3, taskAccentIndex(TaskCategory.LEISURE))
        assertEquals(4, taskAccentIndex(TaskCategory.UNSPECIFIED))
    }

    @Test fun interactionTimingsKeepPressFeedbackShorterThanPageAndDetailChanges() {
        assertTrue(ReplandMotion.Press in 80..150)
        assertTrue(ReplandMotion.Release < ReplandMotion.Page)
        assertTrue(ReplandMotion.Page < ReplandMotion.Detail)
        assertTrue(ReplandMotion.Collapse < ReplandMotion.Expand)
    }

    private fun contrast(first: Color, second: Color): Float {
        val light = maxOf(first.luminance(), second.luminance())
        val dark = minOf(first.luminance(), second.luminance())
        return (light + .05f) / (dark + .05f)
    }
}
