package com.vow.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import com.vow.app.designsystem.BreathStep
import com.vow.app.designsystem.LumeHaptic
import com.vow.app.designsystem.LumeHaptics
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

enum class BreathCue { INHALE, HOLD, EXHALE }

class Haptics(private val view: View) {

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

    private val lume by lazy { LumeHaptics(view.context) }

    /** BREATH_WAVE: inhale swells, hold is a soft tap, exhale falls away. */
    fun breath(cue: BreathCue) = lume.breath(
        when (cue) {
            BreathCue.INHALE -> BreathStep.INHALE
            BreathCue.HOLD -> BreathStep.HOLD
            BreathCue.EXHALE -> BreathStep.EXHALE
        }
    )

    /** GENTLE_NUDGE: a quiet tap for blocks; never a rejection buzz. */
    fun nudge() = lume.play(LumeHaptic.GENTLE_NUDGE)

    /** SEAL_LOCK: a latch sliding shut. */
    fun seal() = lume.play(LumeHaptic.SEAL_LOCK)

    private fun perform(constant: Int) {
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
