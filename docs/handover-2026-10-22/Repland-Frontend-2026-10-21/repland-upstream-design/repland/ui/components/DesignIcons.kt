package com.swan1127.repland.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** One 24dp / 1.8 stroke family. Tiny native vectors require no bitmap or icon dependency. */
object DesignIcons {
    private fun outline(name: String, draw: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = draw,
            )
        }.build()

    val Camera by lazy {
        outline("DesignCamera") {
            moveTo(8f, 6f); lineTo(9.5f, 3.5f); horizontalLineTo(14.5f); lineTo(16f, 6f)
            horizontalLineTo(20f); verticalLineTo(20f); horizontalLineTo(4f); verticalLineTo(6f); close()
            moveTo(16f, 12.5f); curveTo(16f, 14.7f, 14.2f, 16.5f, 12f, 16.5f)
            curveTo(9.8f, 16.5f, 8f, 14.7f, 8f, 12.5f); curveTo(8f, 10.3f, 9.8f, 8.5f, 12f, 8.5f)
            curveTo(14.2f, 8.5f, 16f, 10.3f, 16f, 12.5f); close()
        }
    }
    val Edit by lazy {
        outline("DesignEdit") {
            moveTo(14f, 5f); lineTo(19f, 10f); moveTo(4f, 20f); lineTo(5f, 15f)
            lineTo(16f, 4f); curveTo(17f, 3f, 18f, 3f, 19f, 4f); lineTo(20f, 5f)
            curveTo(21f, 6f, 21f, 7f, 20f, 8f); lineTo(9f, 19f); close()
        }
    }

    val Calendar by lazy {
        outline("DesignCalendar") {
            moveTo(7f, 3f); verticalLineTo(6f); moveTo(17f, 3f); verticalLineTo(6f)
            moveTo(5f, 5f); horizontalLineTo(19f); curveTo(20.1f, 5f, 21f, 5.9f, 21f, 7f)
            verticalLineTo(19f); curveTo(21f, 20.1f, 20.1f, 21f, 19f, 21f)
            horizontalLineTo(5f); curveTo(3.9f, 21f, 3f, 20.1f, 3f, 19f)
            verticalLineTo(7f); curveTo(3f, 5.9f, 3.9f, 5f, 5f, 5f); close()
            moveTo(3f, 10f); horizontalLineTo(21f)
            moveTo(7f, 14f); horizontalLineTo(10f); moveTo(7f, 17f); horizontalLineTo(13f)
        }
    }
    val Tasks by lazy {
        outline("DesignTasks") {
            moveTo(4f, 6f); lineTo(5.5f, 7.5f); lineTo(8f, 4.5f)
            moveTo(12f, 6f); horizontalLineTo(20f)
            moveTo(4f, 12f); lineTo(5.5f, 13.5f); lineTo(8f, 10.5f)
            moveTo(12f, 12f); horizontalLineTo(20f)
            moveTo(4f, 18f); horizontalLineTo(7f); moveTo(12f, 18f); horizontalLineTo(20f)
        }
    }
    val Assistant by lazy {
        outline("DesignAssistant") {
            moveTo(6f, 4f); horizontalLineTo(18f); curveTo(19.7f, 4f, 21f, 5.3f, 21f, 7f)
            verticalLineTo(15f); curveTo(21f, 16.7f, 19.7f, 18f, 18f, 18f)
            horizontalLineTo(10f); lineTo(5f, 21f); verticalLineTo(17.8f)
            curveTo(3.8f, 17.4f, 3f, 16.3f, 3f, 15f); verticalLineTo(7f)
            curveTo(3f, 5.3f, 4.3f, 4f, 6f, 4f); close()
            moveTo(8f, 9f); horizontalLineTo(16f); moveTo(8f, 13f); horizontalLineTo(13f)
        }
    }
    val Profile by lazy {
        outline("DesignProfile") {
            moveTo(16f, 7f); curveTo(16f, 9.2f, 14.2f, 11f, 12f, 11f)
            curveTo(9.8f, 11f, 8f, 9.2f, 8f, 7f); curveTo(8f, 4.8f, 9.8f, 3f, 12f, 3f)
            curveTo(14.2f, 3f, 16f, 4.8f, 16f, 7f); close()
            moveTo(4f, 21f); verticalLineTo(19f); curveTo(4f, 15.7f, 7.6f, 14f, 12f, 14f)
            curveTo(16.4f, 14f, 20f, 15.7f, 20f, 19f); verticalLineTo(21f); close()
        }
    }
    val ArrowRight by lazy {
        outline("DesignArrowRight") {
            moveTo(5f, 12f); horizontalLineTo(19f)
            moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f)
        }
    }
    val Plus by lazy {
        outline("DesignPlus") {
            moveTo(12f, 5f); verticalLineTo(19f); moveTo(5f, 12f); horizontalLineTo(19f)
        }
    }
    val Clock by lazy {
        outline("DesignClock") {
            moveTo(21f, 12f); curveTo(21f, 17f, 17f, 21f, 12f, 21f)
            curveTo(7f, 21f, 3f, 17f, 3f, 12f); curveTo(3f, 7f, 7f, 3f, 12f, 3f)
            curveTo(17f, 3f, 21f, 7f, 21f, 12f); close()
            moveTo(12f, 7f); verticalLineTo(12f); lineTo(15f, 14f)
        }
    }
}
