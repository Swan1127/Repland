package com.swan1127.repland.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The design tokens shared by every accent.
 *
 * The page is a cool paper (#F7F8FC), cards are pure white, and ink is a near-black with a blue
 * cast. Only the brand family is allowed to move with the accent choice; the neutrals are fixed so
 * the page/card depth and the values mirrored outside Compose (`values-night/`, the widget) stay
 * correct whichever accent is selected.
 */
val CalmAccent = Color(0xFF326B75)
val CalmAccentDark = Color(0xFFA8D9DE)
val WarmPaper = Color(0xFFF7F8FC)
val NightPaper = Color(0xFF171921)

internal object Palette {
    /** The page behind everything. Deliberately a step darker than a card. */
    val Page = WarmPaper

    /** Cards. Pure white, so they separate from the page by fill alone. */
    val Paper = Color(0xFFFFFFFF)

    /** Recessed wells and inactive chips. */
    val PageDeep = Color(0xFFF1F2F7)
    val PageDeepest = Color(0xFFEBEDF4)
    val PageHighest = Color(0xFFE3E6F0)

    val Ink = Color(0xFF242831)
    val InkSoft = Color(0xFF606572)
    val InkFaint = Color(0xFF818695)

    val NightPage = Color(0xFF171921)
    val NightPaper = Color(0xFF232730)
    val NightRaised = Color(0xFF292E38)
    val NightHigh = Color(0xFF303642)
    val NightHighest = Color(0xFF39404D)
    val NightInk = Color(0xFFE9EBF1)
    val NightInkSoft = Color(0xFFB8BECA)
    val NightInkFaint = Color(0xFF9097A7)

    // -- state ------------------------------------------------------------
    val Error = Color(0xFFB3261E)
    val ErrorLight = Color(0xFFFBE3E1)
    val NightError = Color(0xFFF2B8B5)
    val NightErrorContainer = Color(0xFF5C1A16)

    val Hairline = Color(0xFFE3E6EF)
    val NightHairline = Color(0xFF404653)
}
