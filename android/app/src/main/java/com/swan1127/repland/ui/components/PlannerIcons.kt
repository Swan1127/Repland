package com.swan1127.repland.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small local vector set for planning controls, keeping the app independent of a remote icon pack. */
object PlannerIcons {
    val Voice: ImageVector by lazy {
        ImageVector.Builder("PlannerVoice", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f)
                curveTo(10.35f, 3f, 9f, 4.35f, 9f, 6f)
                verticalLineTo(12f)
                curveTo(9f, 13.65f, 10.35f, 15f, 12f, 15f)
                curveTo(13.65f, 15f, 15f, 13.65f, 15f, 12f)
                verticalLineTo(6f)
                curveTo(15f, 4.35f, 13.65f, 3f, 12f, 3f)
                close()
                moveTo(7f, 11f)
                verticalLineTo(12f)
                curveTo(7f, 14.76f, 9.24f, 17f, 12f, 17f)
                curveTo(14.76f, 17f, 17f, 14.76f, 17f, 12f)
                verticalLineTo(11f)
                horizontalLineTo(19f)
                verticalLineTo(12f)
                curveTo(19f, 15.53f, 16.39f, 18.46f, 13f, 18.93f)
                verticalLineTo(21f)
                horizontalLineTo(11f)
                verticalLineTo(18.93f)
                curveTo(7.61f, 18.46f, 5f, 15.53f, 5f, 12f)
                verticalLineTo(11f)
                close()
            }
        }.build()
    }

    val Calendar: ImageVector by lazy {
        ImageVector.Builder("PlannerCalendar", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(7f, 2f)
                verticalLineTo(4f)
                horizontalLineTo(17f)
                verticalLineTo(2f)
                horizontalLineTo(19f)
                verticalLineTo(4f)
                curveTo(20.1f, 4f, 21f, 4.9f, 21f, 6f)
                verticalLineTo(20f)
                curveTo(21f, 21.1f, 20.1f, 22f, 19f, 22f)
                horizontalLineTo(5f)
                curveTo(3.9f, 22f, 3f, 21.1f, 3f, 20f)
                verticalLineTo(6f)
                curveTo(3f, 4.9f, 3.9f, 4f, 5f, 4f)
                horizontalLineTo(5f)
                verticalLineTo(2f)
                close()
                moveTo(5f, 9f)
                verticalLineTo(20f)
                horizontalLineTo(19f)
                verticalLineTo(9f)
                close()
                moveTo(7f, 12f)
                horizontalLineTo(11f)
                verticalLineTo(14f)
                horizontalLineTo(7f)
                close()
                moveTo(13f, 12f)
                horizontalLineTo(17f)
                verticalLineTo(14f)
                horizontalLineTo(13f)
                close()
                moveTo(7f, 16f)
                horizontalLineTo(11f)
                verticalLineTo(18f)
                horizontalLineTo(7f)
                close()
            }
        }.build()
    }

    val Schedule: ImageVector by lazy {
        ImageVector.Builder("PlannerSchedule", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
                curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
                close()
                moveTo(12f, 4f)
                curveTo(16.42f, 4f, 20f, 7.58f, 20f, 12f)
                curveTo(20f, 16.42f, 16.42f, 20f, 12f, 20f)
                curveTo(7.58f, 20f, 4f, 16.42f, 4f, 12f)
                curveTo(4f, 7.58f, 7.58f, 4f, 12f, 4f)
                close()
                moveTo(11f, 6f)
                horizontalLineTo(13f)
                verticalLineTo(11.17f)
                lineTo(16.59f, 13.29f)
                lineTo(15.57f, 15f)
                lineTo(11f, 12.3f)
                close()
            }
        }.build()
    }

    val Send: ImageVector by lazy {
        ImageVector.Builder("PlannerSend", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(2f, 21f)
                lineTo(23f, 12f)
                lineTo(2f, 3f)
                lineTo(2f, 10f)
                lineTo(17f, 12f)
                lineTo(2f, 14f)
                close()
            }
        }.build()
    }
}
