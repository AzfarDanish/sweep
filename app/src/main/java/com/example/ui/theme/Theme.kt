package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = SweepWhiteInk,
    onPrimary = SweepBlack,
    primaryContainer = SweepSurfaceHighDark,
    onPrimaryContainer = SweepWhiteInk,
    secondary = SweepMutedDark,
    onSecondary = SweepBlack,
    secondaryContainer = SweepSurfaceDark,
    onSecondaryContainer = SweepWhiteInk,
    background = SweepBlack,
    onBackground = SweepWhiteInk,
    surface = SweepBlack,
    onSurface = SweepWhiteInk,
    surfaceVariant = SweepSurfaceDark,
    onSurfaceVariant = SweepMutedDark,
    surfaceContainerLowest = SweepBlack,
    surfaceContainerLow = SweepSurfaceDark,
    surfaceContainer = SweepSurfaceDark,
    surfaceContainerHigh = SweepSurfaceHighDark,
    outline = SweepMutedDark,
    outlineVariant = SweepDividerDark,
    error = SweepDestructiveDark,
    onError = SweepBlack,
)

private val LightColorScheme = lightColorScheme(
    primary = SweepInk,
    onPrimary = SweepWhite,
    primaryContainer = SweepSurfaceHighLight,
    onPrimaryContainer = SweepInk,
    secondary = SweepMutedLight,
    onSecondary = SweepWhite,
    secondaryContainer = SweepSurfaceLight,
    onSecondaryContainer = SweepInk,
    background = SweepWhite,
    onBackground = SweepInk,
    surface = SweepWhite,
    onSurface = SweepInk,
    surfaceVariant = SweepSurfaceLight,
    onSurfaceVariant = SweepMutedLight,
    surfaceContainerLowest = SweepWhite,
    surfaceContainerLow = SweepSurfaceLight,
    surfaceContainer = SweepSurfaceLight,
    surfaceContainerHigh = SweepSurfaceHighLight,
    outline = SweepMutedLight,
    outlineVariant = SweepDividerLight,
    error = SweepDestructiveLight,
    onError = SweepWhite,
)

private val SweepShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun SweepTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = SweepShapes,
        content = content,
    )
}
