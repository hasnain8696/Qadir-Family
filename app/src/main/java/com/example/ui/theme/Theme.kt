package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val QadirDarkColorScheme = darkColorScheme(
    primary = HeritageGold,
    onPrimary = AdminBgDark,
    primaryContainer = ForestGreen,
    onPrimaryContainer = AdminBgDark,
    secondary = LeafGreen,
    onSecondary = BarkBrown,
    tertiary = TrunkBrown,
    background = AdminBgDark,
    onBackground = BarkBrown,
    surface = AdminSurfaceDark,
    onSurface = BarkBrown,
    surfaceVariant = AdminCardDark,
    onSurfaceVariant = BarkBrown,
    outline = AdminBorderDark
)

private val QadirLightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    primaryContainer = LightLeafGreen,
    onPrimaryContainer = ForestGreenDark,
    secondary = EmeraldGreen,
    onSecondary = Color.White,
    tertiary = TrunkBrown,
    background = CreamBackground,
    onBackground = BarkBrown,
    surface = CreamSurface,
    onSurface = BarkBrown,
    surfaceVariant = CreamCard,
    onSurfaceVariant = BarkBrown,
    outline = HeritageGoldDark.copy(alpha = 0.4f)
)

@Composable
fun QadirFamilyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) QadirDarkColorScheme else QadirLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    QadirFamilyTheme(darkTheme = darkTheme, content = content)
}
