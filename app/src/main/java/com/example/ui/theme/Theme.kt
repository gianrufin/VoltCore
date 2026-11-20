package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val VoltCoreDarkColorScheme = darkColorScheme(
    primary = ElectricEmerald,
    onPrimary = OledBlack,
    primaryContainer = EmeraldDim,
    onPrimaryContainer = EmeraldGlow,
    secondary = PrecisionCyan,
    onSecondary = OledBlack,
    secondaryContainer = CyanDim,
    onSecondaryContainer = PrecisionCyan,
    tertiary = CautionAmber,
    onTertiary = OledBlack,
    tertiaryContainer = AmberDim,
    onTertiaryContainer = CautionAmber,
    error = CriticalCoral,
    onError = OledBlack,
    errorContainer = CoralDim,
    onErrorContainer = CriticalCoral,
    background = OledBlack,
    onBackground = PureWhite,
    surface = CarbonSurface,
    onSurface = PureWhite,
    surfaceVariant = CarbonElevated,
    onSurfaceVariant = SlateMuted,
    surfaceContainerLowest = OledBlack,
    surfaceContainerLow = CarbonSurface,
    surfaceContainer = CarbonElevated,
    surfaceContainerHigh = CarbonHover,
    outline = HairlineBorder,
    outlineVariant = HairlineBorder
)

val VoltCoreShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VoltCoreTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VoltCoreDarkColorScheme,
        typography = VoltCoreTypography,
        shapes = VoltCoreShapes,
        content = content
    )
}
