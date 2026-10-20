package com.vow.app.designsystem

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

/** Shared Lume haptic vocabulary. Recipes follow the ecosystem spec. */
enum class LumeHaptic { TICK, CLICK, CHECK_COMPLETE, SEAL_LOCK, REVEAL_SWELL, BREATH_WAVE, GENTLE_NUDGE }

/** One step of a BREATH_WAVE: inhale swells, hold is a soft tap, exhale falls away. */
enum class BreathStep { INHALE, HOLD, EXHALE }

internal data class Step(val primitive: Int, val scale: Float, val delayMs: Int = 0)

/**
 * Plays [LumeHaptic]s as VibrationEffect compositions where the actuator supports every primitive,
 * falling back to predefined effects, then to short waveforms. Respects the system touch-feedback setting.
 */
class LumeHaptics(context: Context) {
    private val app = context.applicationContext
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        app.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun play(h: LumeHaptic) = vibrate(effectFor(h))

    /** BREATH_WAVE choreography: call at the start of each phase of the breathing cycle. */
    fun breath(step: BreathStep) = vibrate(breathEffect(step))

    private fun vibrate(build: ((Vibrator) -> VibrationEffect?)) {
        val v = vibrator ?: return
        if (!v.hasVibrator() || !touchFeedbackOn()) return
        build(v)?.let { v.vibrate(it) }
    }

    private fun touchFeedbackOn() = Build.VERSION.SDK_INT >= 33 ||
        Settings.System.getInt(app.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0

    private fun effectFor(h: LumeHaptic): (Vibrator) -> VibrationEffect? = { v ->
        compose(v, recipe(h)) ?: fallback(h)
    }

    private fun breathEffect(s: BreathStep): (Vibrator) -> VibrationEffect? = { v ->
        compose(v, breathRecipe(s)) ?: when (s) {
            BreathStep.INHALE -> wave(longArrayOf(0, 40, 60, 80, 60, 120), intArrayOf(0, 40, 0, 90, 0, 160))
            BreathStep.HOLD -> VibrationEffect.createOneShot(15, 60)
            BreathStep.EXHALE -> wave(longArrayOf(0, 120, 60, 80, 60, 40), intArrayOf(0, 160, 0, 90, 0, 40))
        }
    }

    private fun wave(t: LongArray, a: IntArray) = VibrationEffect.createWaveform(t, a, -1)

    private fun compose(v: Vibrator, steps: List<Step>?): VibrationEffect? {
        if (steps == null || Build.VERSION.SDK_INT < 30) return null
        if (!v.areAllPrimitivesSupported(*steps.map { it.primitive }.toIntArray())) return null
        val c = VibrationEffect.startComposition()
        steps.forEach { c.addPrimitive(it.primitive, it.scale, it.delayMs) }
        return c.compose()
    }

    private fun fallback(h: LumeHaptic): VibrationEffect? = when (h) {
        LumeHaptic.TICK -> predefined(VibrationEffect.EFFECT_TICK, 10, 60)
        LumeHaptic.CLICK -> predefined(VibrationEffect.EFFECT_CLICK, 15, 120)
        LumeHaptic.CHECK_COMPLETE -> predefined(VibrationEffect.EFFECT_HEAVY_CLICK, 25, 200)
        LumeHaptic.SEAL_LOCK -> wave(longArrayOf(0, 20, 30, 40), intArrayOf(0, 120, 0, 255))
        LumeHaptic.REVEAL_SWELL -> wave(longArrayOf(0, 60, 40, 30), intArrayOf(0, 80, 0, 120))
        LumeHaptic.BREATH_WAVE -> wave(longArrayOf(0, 120), intArrayOf(0, 90))
        LumeHaptic.GENTLE_NUDGE -> predefined(VibrationEffect.EFFECT_TICK, 12, 80)
    }

    private fun predefined(effect: Int, ms: Long, amp: Int): VibrationEffect =
        if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(effect)
        else VibrationEffect.createOneShot(ms, amp)

    companion object {

        internal fun recipe(h: LumeHaptic): List<Step>? = if (Build.VERSION.SDK_INT < 30) null else when (h) {
            LumeHaptic.TICK -> listOf(Step(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f))
            LumeHaptic.CLICK -> listOf(Step(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.65f))
            LumeHaptic.CHECK_COMPLETE -> listOf(
                Step(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f),
                Step(VibrationEffect.Composition.PRIMITIVE_TICK, 0.35f, 50)
            )
            LumeHaptic.SEAL_LOCK -> listOf(
                Step(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.55f),
                Step(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 30),
                Step(VibrationEffect.Composition.PRIMITIVE_TICK, 0.40f, 70)
            )
            LumeHaptic.REVEAL_SWELL -> listOf(
                Step(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.60f),
                Step(VibrationEffect.Composition.PRIMITIVE_TICK, 0.50f, 20)
            )
            LumeHaptic.BREATH_WAVE -> listOf(Step(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.70f))
            LumeHaptic.GENTLE_NUDGE -> listOf(Step(VibrationEffect.Composition.PRIMITIVE_TICK, 0.35f))
        }

        internal fun breathRecipe(s: BreathStep): List<Step> = when (s) {
            BreathStep.INHALE -> listOf(Step(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.70f))
            BreathStep.HOLD -> listOf(Step(VibrationEffect.Composition.PRIMITIVE_TICK, 0.40f))
            BreathStep.EXHALE -> listOf(Step(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.70f))
        }
    }
}
