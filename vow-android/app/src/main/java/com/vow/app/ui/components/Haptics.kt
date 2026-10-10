package com.vow.app.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

enum class BreathCue { INHALE, HOLD, EXHALE }

class Haptics(private val view: View) {

    private val vibrator: Vibrator? by lazy {
        val ctx = view.context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    fun click() = perform(HapticFeedbackConstants.CONTEXT_CLICK)

    fun toggle(on: Boolean) = perform(
        when {
            Build.VERSION.SDK_INT >= 34 ->
                if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
            on -> HapticFeedbackConstants.CONTEXT_CLICK
            else -> HapticFeedbackConstants.CLOCK_TICK
        }
    )

    fun confirm() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.LONG_PRESS
    )

    fun reject() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
        else HapticFeedbackConstants.LONG_PRESS
    )

    fun heavy() = perform(HapticFeedbackConstants.LONG_PRESS)

    /** Inhale swells, exhale falls away, hold is a soft tap. */
    fun breath(cue: BreathCue) {
        val v = vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && v != null) {
            val primitive = when (cue) {
                BreathCue.INHALE -> VibrationEffect.Composition.PRIMITIVE_SLOW_RISE
                BreathCue.HOLD -> VibrationEffect.Composition.PRIMITIVE_TICK
                BreathCue.EXHALE -> VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
            }
            if (v.areAllPrimitivesSupported(primitive)) {
                v.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(primitive, if (cue == BreathCue.HOLD) 0.4f else 0.7f)
                        .compose()
                )
                return
            }
        }
        if (cue == BreathCue.HOLD) tick() else click()
    }

    private fun perform(constant: Int) {
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
