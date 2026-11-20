/*
 * Copyright (c) 2026 YukokuSuzuko
 */

package io.github.yukokusuzuko.teleprompter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import io.github.yukokusuzuko.teleprompter.data.AppThemePalette

@Composable
fun TeleprompterTheme(
    palette: AppThemePalette = AppThemePalette.AMBER,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = colorSchemeFor(palette),
        typography = TeleprompterTypography,
        content = content
    )
}
