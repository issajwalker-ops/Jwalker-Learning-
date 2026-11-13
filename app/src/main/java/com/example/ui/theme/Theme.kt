package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = CobaltPrimaryDark,
    onPrimary = OnCobaltPrimaryDark,
    primaryContainer = CobaltPrimaryContainerDark,
    onPrimaryContainer = OnCobaltPrimaryContainerDark,
    secondary = GoldSecondaryDark,
    onSecondary = OnGoldSecondaryDark,
    secondaryContainer = GoldSecondaryContainerDark,
    onSecondaryContainer = OnGoldSecondaryContainerDark,
    tertiary = MintTertiaryDark,
    onTertiary = OnMintTertiaryDark,
    tertiaryContainer = MintTertiaryContainerDark,
    onTertiaryContainer = OnMintTertiaryContainerDark,
    background = MidnightBackground,
    onBackground = CrispOnBackgroundDark,
    surface = MidnightSurface,
    onSurface = CrispOnSurfaceDark,
    surfaceVariant = SlateSurfaceVariantDark,
    onSurfaceVariant = MutedOnSurfaceVariantDark,
    outline = SubtleOutlineDark,
    outlineVariant = SubtleOutlineVariantDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

private val LightColorScheme = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = OnIndigoPrimary,
    primaryContainer = IndigoPrimaryContainer,
    onPrimaryContainer = OnIndigoPrimaryContainer,
    secondary = AmberSecondary,
    onSecondary = OnAmberSecondary,
    secondaryContainer = AmberSecondaryContainer,
    onSecondaryContainer = OnAmberSecondaryContainer,
    tertiary = TealTertiary,
    onTertiary = OnTealTertiary,
    tertiaryContainer = TealTertiaryContainer,
    onTertiaryContainer = OnTealTertiaryContainer,
    background = IvoryBackground,
    onBackground = InkOnBackground,
    surface = IvorySurface,
    onSurface = InkOnSurface,
    surfaceVariant = ParchmentSurfaceVariant,
    onSurfaceVariant = SlateOnSurfaceVariant,
    outline = SubtleOutline,
    outlineVariant = SubtleOutlineVariant,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

val GatewayShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LearningGatewayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = GatewayShapes,
        content = content
    )
}
