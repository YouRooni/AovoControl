package dev.rooni.aovo.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** What the interaction means, rather than what waveform to play. */
enum class Haptic {
    /** Smallest possible blip: one step of a slider or picker. */
    Tick,

    /** A plain press on a tile, row or button. */
    Press,

    /** A switch or tile turning on. */
    ToggleOn,

    /** A switch or tile turning off. */
    ToggleOff,

    /** Something committed: a value written, a profile applied. */
    Confirm,

    /** Something refused: an invalid value, a blocked action. */
    Reject,

    /** A weightier state change, like swapping riding mode. */
    Heavy,
}

class Haptics(context: Context) {

    /** Mirrors the user preference; flipped from composition as the setting changes. */
    var enabled: Boolean = true

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    private val hasAmplitudeControl: Boolean =
        runCatching { vibrator?.hasAmplitudeControl() == true }.getOrDefault(false)

    /** True on hardware that can render composition primitives, i.e. a decent LRA. */
    private val primitives: Set<Int> by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@lazy emptySet()
        val wanted = intArrayOf(
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_TICK,
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
            VibrationEffect.Composition.PRIMITIVE_THUD,
            VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
            VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
        )
        val supported = runCatching { vibrator?.arePrimitivesSupported(*wanted) }.getOrNull()
            ?: return@lazy emptySet()
        wanted.filterIndexed { index, _ -> supported.getOrElse(index) { false } }.toSet()
    }

    fun perform(haptic: Haptic) {
        if (!enabled) return
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        runCatching {
            val effect = composition(haptic) ?: predefined(haptic) ?: fallback(haptic)
            play(device, effect)
        }
    }

    private fun play(device: Vibrator, effect: VibrationEffect) {
        device.vibrate(effect)
    }

    /** Textured rendition for actuators that support primitives. */
    private fun composition(haptic: Haptic): VibrationEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || primitives.isEmpty()) return null
        fun has(id: Int) = id in primitives
        val click = VibrationEffect.Composition.PRIMITIVE_CLICK
        val tick = VibrationEffect.Composition.PRIMITIVE_TICK
        val lowTick = VibrationEffect.Composition.PRIMITIVE_LOW_TICK
        val thud = VibrationEffect.Composition.PRIMITIVE_THUD
        val rise = VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
        val fall = VibrationEffect.Composition.PRIMITIVE_QUICK_FALL

        val composition = VibrationEffect.startComposition()
        when (haptic) {
            Haptic.Tick -> when {
                has(lowTick) -> composition.addPrimitive(lowTick, 0.4f)
                has(tick) -> composition.addPrimitive(tick, 0.35f)
                else -> return null
            }

            Haptic.Press -> when {
                has(click) -> composition.addPrimitive(click, 0.55f)
                has(tick) -> composition.addPrimitive(tick, 0.7f)
                else -> return null
            }

            Haptic.ToggleOn -> when {
                has(rise) && has(click) -> composition
                    .addPrimitive(rise, 0.4f)
                    .addPrimitive(click, 0.7f, 40)

                has(click) -> composition.addPrimitive(click, 0.7f)
                else -> return null
            }

            Haptic.ToggleOff -> when {
                has(fall) && has(tick) -> composition
                    .addPrimitive(tick, 0.5f)
                    .addPrimitive(fall, 0.35f, 30)

                has(tick) -> composition.addPrimitive(tick, 0.6f)
                else -> return null
            }

            Haptic.Confirm -> when {
                has(click) && has(rise) -> composition
                    .addPrimitive(click, 0.6f)
                    .addPrimitive(rise, 0.5f, 60)

                has(click) -> composition.addPrimitive(click, 0.8f)
                else -> return null
            }

            Haptic.Reject -> when {
                has(thud) -> composition
                    .addPrimitive(thud, 0.7f)
                    .addPrimitive(thud, 0.5f, 90)

                has(click) -> composition
                    .addPrimitive(click, 0.8f)
                    .addPrimitive(click, 0.6f, 90)

                else -> return null
            }

            Haptic.Heavy -> when {
                has(thud) && has(click) -> composition
                    .addPrimitive(click, 0.9f)
                    .addPrimitive(thud, 0.6f, 40)

                has(click) -> composition.addPrimitive(click, 1f)
                else -> return null
            }
        }
        return composition.compose()
    }

    /** Stock system effects; still noticeably better than a raw buzz. */
    private fun predefined(haptic: Haptic): VibrationEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val id = when (haptic) {
            Haptic.Tick -> VibrationEffect.EFFECT_TICK
            Haptic.Press, Haptic.ToggleOff -> VibrationEffect.EFFECT_CLICK
            Haptic.ToggleOn, Haptic.Confirm -> VibrationEffect.EFFECT_HEAVY_CLICK
            Haptic.Heavy -> VibrationEffect.EFFECT_HEAVY_CLICK
            Haptic.Reject -> VibrationEffect.EFFECT_DOUBLE_CLICK
        }
        return runCatching { VibrationEffect.createPredefined(id) }.getOrNull()
    }

    /** Plain rotating-mass motors: duration and, if possible, amplitude. */
    private fun fallback(haptic: Haptic): VibrationEffect {
        val (millis, amplitude) = when (haptic) {
            Haptic.Tick -> 8L to 60
            Haptic.Press -> 14L to 110
            Haptic.ToggleOn -> 18L to 150
            Haptic.ToggleOff -> 12L to 90
            Haptic.Confirm -> 24L to 170
            Haptic.Heavy -> 28L to 200
            Haptic.Reject -> 40L to 180
        }
        return if (hasAmplitudeControl) {
            VibrationEffect.createOneShot(millis, amplitude)
        } else {
            VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }
}

val LocalHaptics: ProvidableCompositionLocal<Haptics?> = compositionLocalOf { null }

/** One instance per activity, kept in sync with the user preference. */
@Composable
fun rememberHaptics(enabled: Boolean): Haptics {
    val context = LocalContext.current
    val haptics = remember(context) { Haptics(context.applicationContext) }
    haptics.enabled = enabled
    return haptics
}

/** Fires [haptic] unless haptics are switched off or unavailable. */
@Composable
fun hapticAction(haptic: Haptic, action: () -> Unit): () -> Unit {
    val haptics = LocalHaptics.current
    return {
        haptics?.perform(haptic)
        action()
    }
}
