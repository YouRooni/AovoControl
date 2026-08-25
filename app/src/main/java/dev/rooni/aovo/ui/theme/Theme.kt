package dev.rooni.aovo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.rooni.aovo.data.ThemeMode

// speed gauge reads clearly against both surfaces.
internal val BrandLight = lightColorScheme(
    primary = Color(0xFF00696C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF6FF6FA),
    onPrimaryContainer = Color(0xFF002021),
    secondary = Color(0xFF4A6363),
    secondaryContainer = Color(0xFFCCE8E7),
    tertiary = Color(0xFF4B607C),
    tertiaryContainer = Color(0xFFD3E4FF),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    surface = Color(0xFFFAFDFC),
    surfaceContainer = Color(0xFFEFF2F1),
    surfaceContainerHigh = Color(0xFFE9ECEB),
    surfaceContainerHighest = Color(0xFFE3E6E5),
)

internal val BrandDark = darkColorScheme(
    primary = Color(0xFF4CD9DE),
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F52),
    onPrimaryContainer = Color(0xFF6FF6FA),
    secondary = Color(0xFFB0CCCB),
    secondaryContainer = Color(0xFF324B4B),
    tertiary = Color(0xFFB3C8E8),
    tertiaryContainer = Color(0xFF334863),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    surface = Color(0xFF0E1414),
    surfaceContainer = Color(0xFF1A2121),
    surfaceContainerHigh = Color(0xFF252B2B),
    surfaceContainerHighest = Color(0xFF303636),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AovoTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    paletteKey: String = AppPalette.Default.key,
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val wallpaper =
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (dark) wallpaper.amoled(amoled) else wallpaper
        }

        else -> paletteScheme(AppPalette.from(paletteKey), dark, amoled)
    }

    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
