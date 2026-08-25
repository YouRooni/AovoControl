package dev.rooni.aovo.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class AppPalette(val key: String, val seed: Color) {
    Default("default", Color(0xFF00A6AB)),
    Emerald("emerald", Color(0xFF10B981)),
    Forest("forest", Color(0xFF3F8F4A)),
    Lime("lime", Color(0xFF84CC16)),
    Amber("amber", Color(0xFFF59E0B)),
    Orange("orange", Color(0xFFF97316)),
    Crimson("crimson", Color(0xFFE11D48)),
    Red("red", Color(0xFFDC2626)),
    Pink("pink", Color(0xFFEC4899)),
    Purple("purple", Color(0xFF8B5CF6)),
    Indigo("indigo", Color(0xFF6366F1)),
    Blue("blue", Color(0xFF3B82F6)),
    Sky("sky", Color(0xFF0EA5E9)),
    Sand("sand", Color(0xFFB08968)),
    Mono("mono", Color(0xFF6B7280)),
    ;

    companion object {
        fun from(key: String): AppPalette = entries.firstOrNull { it.key == key } ?: Default
    }
}

fun paletteScheme(palette: AppPalette, dark: Boolean, amoled: Boolean): ColorScheme {
    if (palette == AppPalette.Default) {
        return if (dark) BrandDark.amoled(amoled) else BrandLight
    }

    val hsl = palette.seed.toHsl()
    val hue = hsl.first
    val chroma = if (palette == AppPalette.Mono) 0.06f else hsl.second.coerceIn(0.35f, 0.85f)

    fun tone(lightness: Float, saturation: Float = chroma) =
        hslColor(hue, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))

    val scheme = if (dark) {
        darkColorScheme(
            primary = tone(0.70f),
            onPrimary = tone(0.14f, chroma * 0.9f),
            primaryContainer = tone(0.28f),
            onPrimaryContainer = tone(0.90f, chroma * 0.6f),
            secondary = tone(0.72f, chroma * 0.45f),
            onSecondary = tone(0.16f, chroma * 0.4f),
            secondaryContainer = tone(0.26f, chroma * 0.4f),
            onSecondaryContainer = tone(0.90f, chroma * 0.35f),
            tertiary = tone(0.72f, chroma * 0.6f),
            tertiaryContainer = tone(0.30f, chroma * 0.5f),
            onTertiaryContainer = tone(0.90f, chroma * 0.4f),
            background = tone(0.07f, chroma * 0.14f),
            onBackground = tone(0.92f, chroma * 0.10f),
            surface = tone(0.07f, chroma * 0.14f),
            onSurface = tone(0.92f, chroma * 0.10f),
            surfaceVariant = tone(0.17f, chroma * 0.16f),
            onSurfaceVariant = tone(0.74f, chroma * 0.12f),
            surfaceContainerLowest = tone(0.05f, chroma * 0.14f),
            surfaceContainerLow = tone(0.09f, chroma * 0.14f),
            surfaceContainer = tone(0.12f, chroma * 0.15f),
            surfaceContainerHigh = tone(0.16f, chroma * 0.15f),
            surfaceContainerHighest = tone(0.20f, chroma * 0.15f),
            outline = tone(0.45f, chroma * 0.18f),
            outlineVariant = tone(0.28f, chroma * 0.16f),
            inverseSurface = tone(0.90f, chroma * 0.10f),
            inverseOnSurface = tone(0.14f, chroma * 0.12f),
        )
    } else {
        lightColorScheme(
            primary = tone(0.40f),
            onPrimary = Color.White,
            primaryContainer = tone(0.88f, chroma * 0.7f),
            onPrimaryContainer = tone(0.16f),
            secondary = tone(0.44f, chroma * 0.45f),
            onSecondary = Color.White,
            secondaryContainer = tone(0.90f, chroma * 0.35f),
            onSecondaryContainer = tone(0.18f, chroma * 0.5f),
            tertiary = tone(0.44f, chroma * 0.6f),
            tertiaryContainer = tone(0.90f, chroma * 0.45f),
            onTertiaryContainer = tone(0.18f, chroma * 0.5f),
            background = tone(0.985f, chroma * 0.10f),
            onBackground = tone(0.12f, chroma * 0.14f),
            surface = tone(0.985f, chroma * 0.10f),
            onSurface = tone(0.12f, chroma * 0.14f),
            surfaceVariant = tone(0.92f, chroma * 0.16f),
            onSurfaceVariant = tone(0.38f, chroma * 0.20f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = tone(0.965f, chroma * 0.10f),
            surfaceContainer = tone(0.945f, chroma * 0.12f),
            surfaceContainerHigh = tone(0.915f, chroma * 0.13f),
            surfaceContainerHighest = tone(0.885f, chroma * 0.14f),
            outline = tone(0.55f, chroma * 0.20f),
            outlineVariant = tone(0.82f, chroma * 0.18f),
            inverseSurface = tone(0.20f, chroma * 0.12f),
            inverseOnSurface = tone(0.95f, chroma * 0.10f),
        )
    }
    return if (dark) scheme.amoled(amoled) else scheme
}

fun ColorScheme.amoled(enabled: Boolean): ColorScheme = if (!enabled) this else copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
)

private fun Color.toHsl(): Triple<Float, Float, Float> {
    val r = red
    val g = green
    val b = blue
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC
    val lightness = (maxC + minC) / 2f
    if (delta == 0f) return Triple(0f, 0f, lightness)
    val saturation = delta / (1f - abs(2f * lightness - 1f))
    val hue = when (maxC) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return Triple((hue + 360f) % 360f, saturation.coerceIn(0f, 1f), lightness)
}

private fun hslColor(hue: Float, saturation: Float, lightness: Float): Color {
    val c = (1f - abs(2f * lightness - 1f)) * saturation
    val x = c * (1f - abs(((hue / 60f) % 2f) - 1f))
    val m = lightness - c / 2f
    val (r, g, b) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}
