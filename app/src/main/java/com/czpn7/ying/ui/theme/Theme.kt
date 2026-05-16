package com.czpn7.ying.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = TokyoNightPrimary,
    onPrimary = TokyoNightBackground,
    primaryContainer = TokyoNightPrimaryContainer,
    onPrimaryContainer = ColorCompat.onTokyoPrimaryContainer,
    secondary = TokyoNightSecondary,
    onSecondary = TokyoNightBackground,
    secondaryContainer = TokyoNightSecondaryContainer,
    onSecondaryContainer = ColorCompat.onTokyoSecondaryContainer,
    tertiary = TokyoNightTertiary,
    onTertiary = TokyoNightBackground,
    tertiaryContainer = ColorCompat.tokyoTertiaryContainer,
    onTertiaryContainer = ColorCompat.onTokyoTertiaryContainer,
    background = TokyoNightBackground,
    onBackground = TokyoNightText,
    surface = TokyoNightSurface,
    onSurface = TokyoNightText,
    surfaceVariant = TokyoNightSurfaceVariant,
    onSurfaceVariant = TokyoNightTextMuted,
    outline = TokyoNightOutline,
    outlineVariant = ColorCompat.tokyoOutlineVariant,
    error = TokyoNightTertiary,
    errorContainer = ColorCompat.tokyoErrorContainer,
    onErrorContainer = ColorCompat.onTokyoTertiaryContainer
)

private val LightColorScheme = lightColorScheme(
    primary = YingDayPrimary,
    onPrimary = YingDaySurface,
    primaryContainer = YingDayPrimarySoft,
    onPrimaryContainer = YingDayPrimaryDark,
    secondary = YingDaySecondary,
    onSecondary = YingDaySurface,
    secondaryContainer = YingDaySecondarySoft,
    onSecondaryContainer = ColorCompat.daySecondaryDark,
    tertiary = YingDayTertiary,
    onTertiary = YingDaySurface,
    tertiaryContainer = YingDayTertiarySoft,
    onTertiaryContainer = ColorCompat.dayTertiaryDark,
    background = YingDayBackground,
    onBackground = YingDayText,
    surface = YingDaySurface,
    onSurface = YingDayText,
    surfaceVariant = YingDaySurfaceVariant,
    onSurfaceVariant = YingDayTextMuted,
    outline = YingDayOutline,
    outlineVariant = YingDayOutline,
    error = ColorCompat.error,
    errorContainer = YingDayTertiarySoft,
    onErrorContainer = ColorCompat.dayTertiaryDark
)

private object ColorCompat {
    val daySecondaryDark = androidx.compose.ui.graphics.Color(0xFF252B72)
    val dayTertiaryDark = androidx.compose.ui.graphics.Color(0xFF5D1717)
    val onTokyoPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFEAF1FF)
    val onTokyoSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFE7F7D6)
    val tokyoTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF4A2636)
    val onTokyoTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDCE5)
    val tokyoOutlineVariant = androidx.compose.ui.graphics.Color(0xFF2F3549)
    val tokyoErrorContainer = androidx.compose.ui.graphics.Color(0xFF4A2636)
    val error = androidx.compose.ui.graphics.Color(0xFFBA1A1A)
}

@Composable
fun YingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
