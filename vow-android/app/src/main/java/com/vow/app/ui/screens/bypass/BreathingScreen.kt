package com.vow.app.ui.screens.bypass

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vow.app.data.repository.VowRepository
import com.vow.app.ui.components.*
import com.vow.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TOTAL_SECONDS = 90

private enum class Phase(val label: String, val color: Color, val cue: BreathCue) {
    IN("Breathe in", VowTeal, BreathCue.INHALE),
    HOLD("Hold", VowSeal, BreathCue.HOLD),
    OUT("Breathe out", VowSky, BreathCue.EXHALE)
}

@Composable
fun BreathingScreen(
    repo: VowRepository,
    restrictionId: String,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var secondsRemaining by remember { mutableIntStateOf(TOTAL_SECONDS) }
    var phase by remember { mutableStateOf(Phase.IN) }
    val isComplete = secondsRemaining <= 0
    val restrictions by repo.prefs.getRestrictions().collectAsState(initial = emptyList())
    val hours = restrictions.find { it.id == restrictionId }
        ?.let { VowRepository.coolingHours(it.type) } ?: 24

    val breath = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        while (secondsRemaining > 0) {
            phase = Phase.IN; haptics.breath(phase.cue)
            breath.animateTo(1f, tween(4000, easing = EaseInOutSine))
            if (secondsRemaining <= 0) break
            phase = Phase.HOLD; haptics.breath(phase.cue)
            delay(4000)
            if (secondsRemaining <= 0) break
            phase = Phase.OUT; haptics.breath(phase.cue)
            breath.animateTo(0f, tween(4000, easing = EaseInOutSine))
        }
    }

    LaunchedEffect(Unit) {
        while (secondsRemaining > 0) {
            delay(1000)
            secondsRemaining--
        }
        haptics.confirm()
    }

    val phaseColor by animateColorAsState(
        if (isComplete) VowGreen else phase.color, tween(900), label = "phaseColor"
    )
    val overall by animateFloatAsState(
        1f - secondsRemaining / TOTAL_SECONDS.toFloat(), tween(1000, easing = LinearEasing), label = "overall"
    )
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(
                Brush.radialGradient(
                    listOf(phaseColor.copy(alpha = 0.16f + 0.10f * breath.value), Color.Transparent)
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedContent(
                targetState = if (isComplete) "Well done" else phase.label,
                transitionSpec = {
                    (fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 3 }) togetherWith fadeOut(tween(300))
                },
                label = "phaseLabel"
            ) { label ->
                Text(label, style = MaterialTheme.typography.displaySmall, color = phaseColor)
            }

            Spacer(modifier = Modifier.height(40.dp))

            Box(modifier = Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val ring = 6.dp.toPx()
                    val arc = Size(size.width - ring, size.height - ring)
                    val tl = Offset(ring / 2, ring / 2)
                    drawArc(trackColor, -90f, 360f, false, tl, arc, style = Stroke(ring, cap = StrokeCap.Round))
                    rotate(-90f) {
                        drawArc(
                            brush = Brush.sweepGradient(listOf(VowTeal, VowSky, VowSeal, VowTeal)),
                            startAngle = 0f, sweepAngle = 360f * overall, useCenter = false,
                            topLeft = tl, size = arc, style = Stroke(ring, cap = StrokeCap.Round)
                        )
                    }

                    val maxR = size.minDimension / 2 - ring * 3
                    val r = maxR * (0.38f + 0.62f * breath.value)
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(phaseColor.copy(alpha = 0.35f), Color.Transparent),
                            radius = r * 1.5f
                        ),
                        radius = r * 1.5f
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(phaseColor.copy(alpha = 0.95f), phaseColor.copy(alpha = 0.45f)),
                            radius = r
                        ),
                        radius = r
                    )
                }
                Text(
                    if (isComplete) "✓" else "$secondsRemaining",
                    style = MaterialTheme.typography.displayMedium,
                    color = Ink
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            AnimatedVisibility(visible = !isComplete, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    "This cannot be skipped.\nThe urge will pass.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            AnimatedVisibility(
                visible = isComplete,
                enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 4 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Do you still want to loosen this protection?\nA $hours-hour cooling-off period will begin.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    GradientButton("Keep it active", onClick = onBack, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    QuietButton("Start the $hours-hour cooling-off", onClick = {
                        scope.launch {
                            repo.requestLoosening(restrictionId)
                            onComplete()
                        }
                    })
                }
            }
        }
    }
}
