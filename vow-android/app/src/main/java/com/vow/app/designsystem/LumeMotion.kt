package com.vow.app.designsystem

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.animateFloatAsState

object LumeMotion {
    /** Movement, scale, sheets. */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.75f, stiffness = 380f)

    /** Colour, opacity, progress fills: no overshoot. */
    fun <T> effects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1.0f, stiffness = 1500f)

    const val BREATH_MS = 4000
    val BreathEasing: Easing = EaseInOutSine
    const val PRESSED_SCALE = 0.97f
}

/** Scales to 0.97 on touch with a TICK, then rebounds with a little overshoot. Does not consume the touch. */
fun Modifier.pressable(): Modifier = composed {
    val context = LocalContext.current
    val haptics = remember(context) { LumeHaptics(context) }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) LumeMotion.PRESSED_SCALE else 1f,
        LumeMotion.spatial(),
        label = "pressable"
    )
    this
        .scale(scale)
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pressed = true
                haptics.play(LumeHaptic.TICK)
                waitForUpOrCancellation(PointerEventPass.Final)
                pressed = false
            }
        }
}
