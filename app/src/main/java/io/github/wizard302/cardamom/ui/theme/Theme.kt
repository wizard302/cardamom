package io.github.wizard302.cardamom.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Static fallback palette for API < 31 (no Material You there).
 * Warm gold accent on near-black surfaces.
 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE8C26E),
    onPrimary = Color(0xFF3F2E00),
    primaryContainer = Color(0xFF5B4300),
    onPrimaryContainer = Color(0xFFFFDF9E),
    secondary = Color(0xFFD5C4A1),
    onSecondary = Color(0xFF392F15),
    tertiary = Color(0xFFA9D0B3),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE7E1D9),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE7E1D9),
    surfaceVariant = Color(0xFF4B4639),
    onSurfaceVariant = Color(0xFFCEC6B4),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF775A0B),
    secondary = Color(0xFF6B5D3F),
    tertiary = Color(0xFF4A6547),
)

@Composable
fun CardamomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
        content = content,
    )
}
