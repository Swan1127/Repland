package com.swan1127.repland.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics

enum class LandscapeStyle { Dawn, Sage, Dusk }

private data class LandscapeColors(
    val sky: Color,
    val horizon: Color,
    val light: Color,
    val distant: Color,
    val middle: Color,
    val near: Color,
)

private fun landscapeColors(style: LandscapeStyle, dark: Boolean): LandscapeColors = when {
    dark && style == LandscapeStyle.Dusk -> LandscapeColors(
        Color(0xFF343B58), Color(0xFF6A6683), Color(0xFFE4DEEA),
        Color(0xFF6D6E93), Color(0xFF434F70), Color(0xFF253C4B),
    )
    dark && style == LandscapeStyle.Dawn -> LandscapeColors(
        Color(0xFF304750), Color(0xFF82675D), Color(0xFFEBC49D),
        Color(0xFF796D6D), Color(0xFF4F6265), Color(0xFF263E45),
    )
    dark -> LandscapeColors(
        Color(0xFF2D484C), Color(0xFF627D76), Color(0xFFD9DFCB),
        Color(0xFF6A8882), Color(0xFF456660), Color(0xFF253F40),
    )
    style == LandscapeStyle.Dusk -> LandscapeColors(
        Color(0xFFB7B4DF), Color(0xFFF0DCE1), Color(0xFFFFF8EF),
        Color(0xFFAFA4D8), Color(0xFF898BAE), Color(0xFF4D697D),
    )
    style == LandscapeStyle.Dawn -> LandscapeColors(
        Color(0xFFDFE7DF), Color(0xFFF3D6AF), Color(0xFFFFF7DC),
        Color(0xFFD7AF93), Color(0xFFC77D61), Color(0xFF526C6D),
    )
    else -> LandscapeColors(
        Color(0xFFD8E8E5), Color(0xFFF4EDD9), Color(0xFFFFFCED),
        Color(0xFFA9BCB0), Color(0xFF7E9891), Color(0xFF3E6264),
    )
}

@Composable
fun LandscapeArtwork(
    modifier: Modifier = Modifier,
    style: LandscapeStyle = LandscapeStyle.Dawn,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .2f
    val colors = landscapeColors(style, dark)
    Box(
        modifier = modifier
            .clearAndSetSemantics { }
            .drawWithCache {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val skyBrush = Brush.verticalGradient(listOf(colors.sky, colors.horizon))
                val distantRidge = Path().apply {
                    moveTo(0f, canvasHeight * .66f)
                    lineTo(canvasWidth * .12f, canvasHeight * .55f)
                    lineTo(canvasWidth * .26f, canvasHeight * .6f)
                    lineTo(canvasWidth * .51f, canvasHeight * .35f)
                    lineTo(canvasWidth * .62f, canvasHeight * .46f)
                    lineTo(canvasWidth * .72f, canvasHeight * .43f)
                    lineTo(canvasWidth, canvasHeight * .69f)
                    lineTo(canvasWidth, canvasHeight)
                    lineTo(0f, canvasHeight)
                    close()
                }
                val middleRidge = Path().apply {
                    moveTo(0f, canvasHeight * .79f)
                    cubicTo(
                        canvasWidth * .13f, canvasHeight * .61f,
                        canvasWidth * .23f, canvasHeight * .84f,
                        canvasWidth * .4f, canvasHeight * .68f,
                    )
                    cubicTo(
                        canvasWidth * .57f, canvasHeight * .5f,
                        canvasWidth * .69f, canvasHeight * .81f,
                        canvasWidth, canvasHeight * .6f,
                    )
                    lineTo(canvasWidth, canvasHeight)
                    lineTo(0f, canvasHeight)
                    close()
                }
                val nearRidge = Path().apply {
                    moveTo(0f, canvasHeight * .88f)
                    cubicTo(
                        canvasWidth * .22f, canvasHeight * .72f,
                        canvasWidth * .43f, canvasHeight * .95f,
                        canvasWidth * .63f, canvasHeight * .82f,
                    )
                    cubicTo(
                        canvasWidth * .79f, canvasHeight * .71f,
                        canvasWidth * .91f, canvasHeight * .8f,
                        canvasWidth, canvasHeight * .76f,
                    )
                    lineTo(canvasWidth, canvasHeight)
                    lineTo(0f, canvasHeight)
                    close()
                }
                onDrawBehind {
                    drawRect(skyBrush)
                    drawCircle(
                        colors.light.copy(alpha = .14f),
                        radius = size.minDimension * .22f,
                        center = Offset(canvasWidth * .75f, canvasHeight * .25f),
                    )
                    drawCircle(
                        colors.light,
                        radius = size.minDimension * .115f,
                        center = Offset(canvasWidth * .75f, canvasHeight * .25f),
                    )
                    drawOval(
                        colors.light.copy(alpha = .55f),
                        topLeft = Offset(canvasWidth * .08f, canvasHeight * .25f),
                        size = Size(canvasWidth * .24f, canvasHeight * .035f),
                    )
                    drawOval(
                        colors.light.copy(alpha = .4f),
                        topLeft = Offset(canvasWidth * .17f, canvasHeight * .29f),
                        size = Size(canvasWidth * .22f, canvasHeight * .025f),
                    )
                    drawPath(distantRidge, colors.distant)
                    drawPath(middleRidge, colors.middle)
                    drawPath(nearRidge, colors.near)
                }
            },
    )
}
