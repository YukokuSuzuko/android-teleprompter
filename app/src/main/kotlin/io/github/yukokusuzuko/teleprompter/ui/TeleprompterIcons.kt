/*
 * Copyright (c) 2026 YukokuSuzuko
 */

package io.github.yukokusuzuko.teleprompter.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight vector icons matching Material Symbols, keeping APK size minimal
 * without pulling in the entire material-icons-extended artifact.
 */
object TeleprompterIcons {

    val ChevronLeft: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChevronLeft",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(15.41f, 7.41f)
                lineTo(14f, 6f)
                lineTo(8f, 12f)
                lineTo(14f, 18f)
                lineTo(15.41f, 16.59f)
                lineTo(10.83f, 12f)
                close()
            }
        }.build()
    }

    val Play: ImageVector by lazy {
        ImageVector.Builder(
            name = "Play",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(8f, 5f)
                verticalLineTo(19f)
                lineTo(19f, 12f)
                close()
            }
        }.build()
    }

    val Pause: ImageVector by lazy {
        ImageVector.Builder(
            name = "Pause",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 5f)
                horizontalLineTo(10f)
                verticalLineTo(19f)
                horizontalLineTo(6f)
                close()
                moveTo(14f, 5f)
                horizontalLineTo(18f)
                verticalLineTo(19f)
                horizontalLineTo(14f)
                close()
            }
        }.build()
    }

    val Rewind: ImageVector by lazy {
        ImageVector.Builder(
            name = "Rewind",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12.5f, 8f)
                curveTo(9.85f, 8f, 7.45f, 8.99f, 5.6f, 10.6f)
                lineTo(2f, 7f)
                verticalLineTo(16f)
                horizontalLineTo(11f)
                lineTo(7.38f, 12.38f)
                curveTo(8.77f, 11.22f, 10.54f, 10.5f, 12.5f, 10.5f)
                curveTo(16.04f, 10.5f, 19.05f, 12.81f, 20.1f, 16f)
                lineTo(22.47f, 15.22f)
                curveTo(21.08f, 11.03f, 17.15f, 8f, 12.5f, 8f)
                close()
            }
        }.build()
    }

    val SkipPrevious: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkipPrevious",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 6f)
                horizontalLineTo(8f)
                verticalLineTo(18f)
                horizontalLineTo(6f)
                close()
                moveTo(9.5f, 12f)
                lineTo(18f, 18f)
                verticalLineTo(6f)
                close()
            }
        }.build()
    }

    val RotateScreen: ImageVector by lazy {
        ImageVector.Builder(
            name = "RotateScreen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(16f, 3f)
                horizontalLineTo(8f)
                curveTo(6.9f, 3f, 6f, 3.9f, 6f, 5f)
                verticalLineTo(19f)
                curveTo(6f, 20.1f, 6.9f, 21f, 8f, 21f)
                horizontalLineTo(16f)
                curveTo(17.1f, 21f, 18f, 20.1f, 18f, 19f)
                verticalLineTo(5f)
                curveTo(18f, 3.9f, 17.1f, 3f, 16f, 3f)
                close()
                moveTo(10f, 18f)
                horizontalLineTo(14f)
            }
        }.build()
    }

    val Tune: ImageVector by lazy {
        ImageVector.Builder(
            name = "Tune",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(3f, 17f)
                verticalLineTo(19f)
                horizontalLineTo(9f)
                verticalLineTo(17f)
                close()
                moveTo(3f, 5f)
                verticalLineTo(7f)
                horizontalLineTo(13f)
                verticalLineTo(5f)
                close()
                moveTo(13f, 21f)
                verticalLineTo(19f)
                horizontalLineTo(21f)
                verticalLineTo(17f)
                horizontalLineTo(13f)
                verticalLineTo(15f)
                horizontalLineTo(11f)
                verticalLineTo(21f)
                close()
                moveTo(7f, 9f)
                verticalLineTo(11f)
                horizontalLineTo(3f)
                verticalLineTo(13f)
                horizontalLineTo(7f)
                verticalLineTo(15f)
                horizontalLineTo(9f)
                verticalLineTo(9f)
                close()
                moveTo(21f, 13f)
                verticalLineTo(11f)
                horizontalLineTo(11f)
                verticalLineTo(13f)
                close()
                moveTo(15f, 9f)
                horizontalLineTo(17f)
                verticalLineTo(7f)
                horizontalLineTo(21f)
                verticalLineTo(5f)
                horizontalLineTo(17f)
                verticalLineTo(3f)
                horizontalLineTo(15f)
                close()
            }
        }.build()
    }

    val VisibilityOff: ImageVector by lazy {
        ImageVector.Builder(
            name = "VisibilityOff",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 3f)
                lineTo(21f, 21f)
                moveTo(10.6f, 10.6f)
                curveTo(10.2f, 11f, 10f, 11.5f, 10f, 12f)
                curveTo(10f, 13.1f, 10.9f, 14f, 12f, 14f)
                curveTo(12.5f, 14f, 13f, 13.8f, 13.4f, 13.4f)
                moveTo(6.2f, 6.7f)
                curveTo(4.4f, 7.9f, 3f, 9.8f, 2.2f, 12f)
                curveTo(3.7f, 16f, 7.5f, 18.8f, 12f, 18.8f)
                curveTo(13.9f, 18.8f, 15.7f, 18.3f, 17.2f, 17.4f)
                moveTo(9.9f, 5.3f)
                curveTo(10.6f, 5.1f, 11.3f, 5f, 12f, 5f)
                curveTo(16.5f, 5f, 20.3f, 7.8f, 21.8f, 12f)
                curveTo(21.3f, 13.4f, 20.5f, 14.7f, 19.5f, 15.8f)
            }
        }.build()
    }

    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "Check",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(9f, 16.17f)
                lineTo(4.83f, 12f)
                lineTo(3.41f, 13.41f)
                lineTo(9f, 19f)
                lineTo(21f, 7f)
                lineTo(19.59f, 5.59f)
                close()
            }
        }.build()
    }

    val Visibility: ImageVector by lazy {
        ImageVector.Builder(
            name = "Visibility",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(2.2f, 12f)
                curveTo(3.8f, 7.8f, 7.6f, 5f, 12f, 5f)
                curveTo(16.4f, 5f, 20.2f, 7.8f, 21.8f, 12f)
                curveTo(20.2f, 16.2f, 16.4f, 19f, 12f, 19f)
                curveTo(7.6f, 19f, 3.8f, 16.2f, 2.2f, 12f)
                close()
                moveTo(12f, 9.2f)
                curveTo(13.55f, 9.2f, 14.8f, 10.45f, 14.8f, 12f)
                curveTo(14.8f, 13.55f, 13.55f, 14.8f, 12f, 14.8f)
                curveTo(10.45f, 14.8f, 9.2f, 13.55f, 9.2f, 12f)
                curveTo(9.2f, 10.45f, 10.45f, 9.2f, 12f, 9.2f)
                close()
            }
        }.build()
    }

    val Lock: ImageVector by lazy {
        ImageVector.Builder(
            name = "Lock",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7f, 11f)
                verticalLineTo(7f)
                curveTo(7f, 4.24f, 9.24f, 2f, 12f, 2f)
                curveTo(14.76f, 2f, 17f, 4.24f, 17f, 7f)
                verticalLineTo(11f)
                moveTo(6f, 11f)
                horizontalLineTo(18f)
                curveTo(19.1f, 11f, 20f, 11.9f, 20f, 13f)
                verticalLineTo(19f)
                curveTo(20f, 20.1f, 19.1f, 21f, 18f, 21f)
                horizontalLineTo(6f)
                curveTo(4.9f, 21f, 4f, 20.1f, 4f, 19f)
                verticalLineTo(13f)
                curveTo(4f, 11.9f, 4.9f, 11f, 6f, 11f)
                close()
            }
        }.build()
    }

    val Library: ImageVector by lazy {
        ImageVector.Builder(
            name = "Library",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(4f, 6f)
                horizontalLineTo(2f)
                verticalLineTo(20f)
                curveTo(2f, 21.1f, 2.9f, 22f, 4f, 22f)
                horizontalLineTo(18f)
                verticalLineTo(20f)
                horizontalLineTo(4f)
                verticalLineTo(6f)
                close()
                moveTo(20f, 2f)
                horizontalLineTo(8f)
                curveTo(6.9f, 2f, 6f, 2.9f, 6f, 4f)
                verticalLineTo(16f)
                curveTo(6f, 17.1f, 6.9f, 18f, 8f, 18f)
                horizontalLineTo(20f)
                curveTo(21.1f, 18f, 22f, 17.1f, 22f, 16f)
                verticalLineTo(4f)
                curveTo(22f, 2.9f, 21.1f, 2f, 20f, 2f)
                close()
                moveTo(19f, 11f)
                horizontalLineTo(9f)
                verticalLineTo(9f)
                horizontalLineTo(19f)
                verticalLineTo(11f)
                close()
                moveTo(15f, 15f)
                horizontalLineTo(9f)
                verticalLineTo(13f)
                horizontalLineTo(15f)
                verticalLineTo(15f)
                close()
                moveTo(19f, 7f)
                horizontalLineTo(9f)
                verticalLineTo(5f)
                horizontalLineTo(19f)
                verticalLineTo(7f)
                close()
            }
        }.build()
    }

    val Add: ImageVector by lazy {
        ImageVector.Builder(
            name = "Add",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(19f, 13f)
                horizontalLineTo(13f)
                verticalLineTo(19f)
                horizontalLineTo(11f)
                verticalLineTo(13f)
                horizontalLineTo(5f)
                verticalLineTo(11f)
                horizontalLineTo(11f)
                verticalLineTo(5f)
                horizontalLineTo(13f)
                verticalLineTo(11f)
                horizontalLineTo(19f)
                verticalLineTo(13f)
                close()
            }
        }.build()
    }

    val Search: ImageVector by lazy {
        ImageVector.Builder(
            name = "Search",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10.5f, 4f)
                curveTo(14.09f, 4f, 17f, 6.91f, 17f, 10.5f)
                curveTo(17f, 14.09f, 14.09f, 17f, 10.5f, 17f)
                curveTo(6.91f, 17f, 4f, 14.09f, 4f, 10.5f)
                curveTo(4f, 6.91f, 6.91f, 4f, 10.5f, 4f)
                close()
                moveTo(15.5f, 15.5f)
                lineTo(20.5f, 20.5f)
            }
        }.build()
    }

    val Close: ImageVector by lazy {
        ImageVector.Builder(
            name = "Close",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 6f)
                lineTo(18f, 18f)
                moveTo(18f, 6f)
                lineTo(6f, 18f)
            }
        }.build()
    }

    val Pin: ImageVector by lazy {
        ImageVector.Builder(
            name = "Pin",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(16f, 12f)
                verticalLineTo(4f)
                horizontalLineTo(17f)
                verticalLineTo(2f)
                horizontalLineTo(7f)
                verticalLineTo(4f)
                horizontalLineTo(8f)
                verticalLineTo(12f)
                lineTo(6f, 14f)
                verticalLineTo(16f)
                horizontalLineTo(11.2f)
                verticalLineTo(22f)
                horizontalLineTo(12.8f)
                verticalLineTo(16f)
                horizontalLineTo(18f)
                verticalLineTo(14f)
                lineTo(16f, 12f)
                close()
            }
        }.build()
    }

    val Edit: ImageVector by lazy {
        ImageVector.Builder(
            name = "Edit",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(3f, 17.25f)
                verticalLineTo(21f)
                horizontalLineTo(6.75f)
                lineTo(17.81f, 9.94f)
                lineTo(14.06f, 6.19f)
                lineTo(3f, 17.25f)
                close()
                moveTo(20.71f, 7.04f)
                curveTo(21.1f, 6.65f, 21.1f, 6.02f, 20.71f, 5.63f)
                lineTo(18.37f, 3.29f)
                curveTo(17.98f, 2.9f, 17.35f, 2.9f, 16.96f, 3.29f)
                lineTo(15.13f, 5.12f)
                lineTo(18.88f, 8.87f)
                lineTo(20.71f, 7.04f)
                close()
            }
        }.build()
    }

    val Delete: ImageVector by lazy {
        ImageVector.Builder(
            name = "Delete",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 19f)
                curveTo(6f, 20.1f, 6.9f, 21f, 8f, 21f)
                horizontalLineTo(16f)
                curveTo(17.1f, 21f, 18f, 20.1f, 18f, 19f)
                verticalLineTo(7f)
                horizontalLineTo(6f)
                verticalLineTo(19f)
                close()
                moveTo(19f, 4f)
                horizontalLineTo(15.5f)
                lineTo(14.5f, 3f)
                horizontalLineTo(9.5f)
                lineTo(8.5f, 4f)
                horizontalLineTo(5f)
                verticalLineTo(6f)
                horizontalLineTo(19f)
                verticalLineTo(4f)
                close()
            }
        }.build()
    }
}
