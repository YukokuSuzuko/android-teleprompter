/*
 * Copyright (c) 2026 YukokuSuzuko
 */

package io.github.yukokusuzuko.teleprompter.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import io.github.yukokusuzuko.teleprompter.data.AppThemePalette

// Shared semantic colors for OLED teleprompter readability
val PrompterOledBlack = Color(0xFF000000)
val PrompterReadDimmed = Color(0xFF464850)
val PrompterUnreadWhite = Color(0xFFF2F2F7)
val PrompterWhiteThemeReadDimmed = Color(0xFF3A3D45)
val PrompterWhiteThemeUnreadSilver = Color(0xFF8E929C)
val PrompterWhiteThemeActiveBg = Color(0x14FFFFFF)
val PrompterListeningGreen = Color(0xFF4CD964)
val PrompterDangerRed = Color(0xFFFF897D)

val AmberDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFE066),
    onPrimary = Color(0xFF2B2200),
    primaryContainer = Color(0xFF524400),
    onPrimaryContainer = Color(0xFFFFE88E),
    secondary = Color(0xFFD6C99E),
    onSecondary = Color(0xFF393014),
    secondaryContainer = Color(0xFF363842),
    onSecondaryContainer = Color(0xFFE5E7F0),
    tertiary = Color(0xFF80D9B5),
    onTertiary = Color(0xFF003826),
    background = Color(0xFF111318),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF15171D),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFF9A9CA5),
    surfaceContainerLowest = Color(0xFF0B0D11),
    surfaceContainerLow = Color(0xFF191C22),
    surfaceContainer = Color(0xFF1E2128),
    surfaceContainerHigh = Color(0xFF252830),
    surfaceContainerHighest = Color(0xFF313540),
    outline = Color(0xFF5A5D68),
    outlineVariant = Color(0xFF3A3D46),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

val BlueDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF1B4383),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF333B48),
    onSecondaryContainer = Color(0xFFDCE4F2),
    tertiary = Color(0xFFDDBCE0),
    onTertiary = Color(0xFF3F2844),
    background = Color(0xFF10131A),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF141820),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF424754),
    onSurfaceVariant = Color(0xFF9AA0AE),
    surfaceContainerLowest = Color(0xFF0A0D12),
    surfaceContainerLow = Color(0xFF161A22),
    surfaceContainer = Color(0xFF1C212B),
    surfaceContainerHigh = Color(0xFF222733),
    surfaceContainerHighest = Color(0xFF2E3442),
    outline = Color(0xFF585E6D),
    outlineVariant = Color(0xFF383E4B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

val TealDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF80D9B5),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF0F523B),
    onPrimaryContainer = Color(0xFFA6F2D1),
    secondary = Color(0xFFB3CCC0),
    onSecondary = Color(0xFF1E352C),
    secondaryContainer = Color(0xFF2E3D38),
    onSecondaryContainer = Color(0xFFD4EAE1),
    tertiary = Color(0xFFA5CCDF),
    onTertiary = Color(0xFF073544),
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF131917),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF3F4B47),
    onSurfaceVariant = Color(0xFF98A6A1),
    surfaceContainerLowest = Color(0xFF0A0F0E),
    surfaceContainerLow = Color(0xFF141B19),
    surfaceContainer = Color(0xFF1A2220),
    surfaceContainerHigh = Color(0xFF1F2926),
    surfaceContainerHighest = Color(0xFF2A3632),
    outline = Color(0xFF55635E),
    outlineVariant = Color(0xFF36423E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

val LavenderDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF3C3649),
    onSecondaryContainer = Color(0xFFE6DEF5),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    background = Color(0xFF131118),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF17151D),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFA39DAA),
    surfaceContainerLowest = Color(0xFF0E0C12),
    surfaceContainerLow = Color(0xFF191721),
    surfaceContainer = Color(0xFF201D29),
    surfaceContainerHigh = Color(0xFF262330),
    surfaceContainerHighest = Color(0xFF322E3E),
    outline = Color(0xFF5E5968),
    outlineVariant = Color(0xFF3E3A47),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

val CoralDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB59D),
    onPrimary = Color(0xFF5D1800),
    primaryContainer = Color(0xFF7B2D12),
    onPrimaryContainer = Color(0xFFFFDBD0),
    secondary = Color(0xFFE7BDB0),
    onSecondary = Color(0xFF442A21),
    secondaryContainer = Color(0xFF463530),
    onSecondaryContainer = Color(0xFFF7DCD3),
    tertiary = Color(0xFFD8C58D),
    onTertiary = Color(0xFF3B2F05),
    background = Color(0xFF171211),
    onBackground = Color(0xFFF5ECEA),
    surface = Color(0xFF1B1615),
    onSurface = Color(0xFFF5ECEA),
    surfaceVariant = Color(0xFF50433F),
    onSurfaceVariant = Color(0xFFB3A19B),
    surfaceContainerLowest = Color(0xFF110C0B),
    surfaceContainerLow = Color(0xFF201A18),
    surfaceContainer = Color(0xFF27201E),
    surfaceContainerHigh = Color(0xFF2F2724),
    surfaceContainerHighest = Color(0xFF3B322E),
    outline = Color(0xFF6B5B56),
    outlineVariant = Color(0xFF473B37),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

val WhiteDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF121316),
    primaryContainer = Color(0xFF353840),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFC8CBD2),
    onSecondary = Color(0xFF24262B),
    secondaryContainer = Color(0xFF2E3037),
    onSecondaryContainer = Color(0xFFE8EAEF),
    tertiary = Color(0xFFB4B8C2),
    onTertiary = Color(0xFF1F2126),
    background = Color(0xFF101114),
    onBackground = Color(0xFFF5F6FA),
    surface = Color(0xFF141519),
    onSurface = Color(0xFFF5F6FA),
    surfaceVariant = Color(0xFF41444D),
    onSurfaceVariant = Color(0xFF9EA2AC),
    surfaceContainerLowest = Color(0xFF0A0B0D),
    surfaceContainerLow = Color(0xFF17191E),
    surfaceContainer = Color(0xFF1D1F25),
    surfaceContainerHigh = Color(0xFF24272E),
    surfaceContainerHighest = Color(0xFF30333C),
    outline = Color(0xFF5C606B),
    outlineVariant = Color(0xFF393C45),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

fun colorSchemeFor(palette: AppThemePalette): ColorScheme = when (palette) {
    AppThemePalette.AMBER -> AmberDarkColorScheme
    AppThemePalette.BLUE -> BlueDarkColorScheme
    AppThemePalette.TEAL -> TealDarkColorScheme
    AppThemePalette.LAVENDER -> LavenderDarkColorScheme
    AppThemePalette.CORAL -> CoralDarkColorScheme
    AppThemePalette.WHITE -> WhiteDarkColorScheme
}
