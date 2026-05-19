package com.honbu.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HonbuColors = darkColorScheme(
    primary             = KendoRed,
    onPrimary           = Color.White,
    secondary           = KendoGold,
    onSecondary         = KendoDark,
    tertiary            = KendoGold,
    background          = KendoDark,
    surface             = KendoSurface,
    surfaceVariant      = KendoSurfaceVariant,
    // Neutral tint disables the primary-coloured tonal-elevation overlay that
    // M3 normally applies. Without this, elevated surfaces get a red tint
    // (because primary = KendoRed), which makes red text on top look lighter
    // wherever elevation animations or LazyColumn item layer caching kicks in.
    surfaceTint         = Color.Transparent,
    onBackground        = TextPrimary,
    onSurface           = TextPrimary,
    onSurfaceVariant    = TextSecondary,
    outline             = Color(0xFF444444),
    error               = KendoRed,
)

@Composable
fun HonbuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HonbuColors,
        typography  = HonbuTypography,
        content     = content
    )
}
