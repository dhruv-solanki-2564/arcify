package app.arcify.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Colour tokens. The visual design is still open (see CLAUDE.md), so every screen reads colours
 * only through MaterialTheme, and swapping these values reskins the whole app.
 */
object ArcifyTokens {
    val PrimaryLight = Color(0xFF4F4CC9)
    val OnPrimaryLight = Color(0xFFFFFFFF)
    val SecondaryLight = Color(0xFF5E5C71)
    val BackgroundLight = Color(0xFFFBFAFF)
    val SurfaceLight = Color(0xFFFBFAFF)

    val PrimaryDark = Color(0xFFB4B4FF)
    val OnPrimaryDark = Color(0xFF1C1A5C)
    val SecondaryDark = Color(0xFFC7C4DC)
    val BackgroundDark = Color(0xFF131221)
    val SurfaceDark = Color(0xFF131221)
}

private val LightColors = lightColorScheme(
    primary = ArcifyTokens.PrimaryLight,
    onPrimary = ArcifyTokens.OnPrimaryLight,
    secondary = ArcifyTokens.SecondaryLight,
    background = ArcifyTokens.BackgroundLight,
    surface = ArcifyTokens.SurfaceLight,
)

private val DarkColors = darkColorScheme(
    primary = ArcifyTokens.PrimaryDark,
    onPrimary = ArcifyTokens.OnPrimaryDark,
    secondary = ArcifyTokens.SecondaryDark,
    background = ArcifyTokens.BackgroundDark,
    surface = ArcifyTokens.SurfaceDark,
)

@Composable
fun ArcifyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
