package com.swan1127.repland.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Native CJK-capable sans serif: no downloadable fonts or additional font assets.
private fun type(
    size: Int,
    line: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: Float = 0f,
) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

internal val ReplandTypography = Typography(
    displayLarge = type(48, 56, FontWeight.SemiBold, -.8f),
    displayMedium = type(40, 48, FontWeight.SemiBold, -.6f),
    displaySmall = type(32, 40, FontWeight.SemiBold, -.5f),
    headlineLarge = type(32, 40, FontWeight.SemiBold, -.5f),
    headlineMedium = type(28, 36, FontWeight.SemiBold, -.4f),
    headlineSmall = type(24, 32, FontWeight.SemiBold, -.3f),
    titleLarge = type(20, 28, FontWeight.SemiBold, -.2f),
    titleMedium = type(16, 24, FontWeight.SemiBold),
    titleSmall = type(14, 20, FontWeight.SemiBold),
    bodyLarge = type(16, 24),
    bodyMedium = type(15, 23),
    bodySmall = type(12, 18),
    labelLarge = type(14, 20, FontWeight.Medium),
    labelMedium = type(12, 18, FontWeight.Medium),
    labelSmall = type(11, 16, FontWeight.Medium),
)
