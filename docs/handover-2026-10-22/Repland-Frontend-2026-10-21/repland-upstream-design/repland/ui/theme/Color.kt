package com.swan1127.repland.ui.theme

import androidx.compose.ui.graphics.Color

val CalmAccent = Color(0xFF326B75)
val CalmAccentDark = Color(0xFFA8D9DE)
val WarmPaper = Color(0xFFF8F6F1)
val NightPaper = Color(0xFF152226)

internal object Palette {
    /** The page behind everything. Deliberately a step darker than a card. */
    val Page = WarmPaper

    val Paper = Color(0xFFFFFCF6)

    /** Recessed wells and inactive chips. */
    val PageDeep = Color(0xFFF0EFE8)
    val PageDeepest = Color(0xFFEAECE5)
    val PageHighest = Color(0xFFE2E7DF)

    val Ink = Color(0xFF263D43)
    val InkSoft = Color(0xFF606572)
    val InkFaint = Color(0xFF72807E)

    val NightPage = Color(0xFF152226)
    val NightPaper = Color(0xFF1D3033)
    val NightRaised = Color(0xFF243A3D)
    val NightHigh = Color(0xFF2D4447)
    val NightHighest = Color(0xFF365054)
    val NightInk = Color(0xFFE7EFEB)
    val NightInkSoft = Color(0xFFB8CBC8)
    val NightInkFaint = Color(0xFF95ABA9)

    // -- state ------------------------------------------------------------
    val Error = Color(0xFFB3261E)
    val ErrorLight = Color(0xFFFBE3E1)
    val NightError = Color(0xFFF2B8B5)
    val NightErrorContainer = Color(0xFF5C1A16)

    val Hairline = Color(0xFFE3E6EF)
    val NightHairline = Color(0xFF506767)
}
