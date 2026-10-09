package com.vow.app.ui.screens.bypass

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vow.app.data.repository.VowRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BreathingScreen(
    repo: VowRepository,
    restrictionId: String,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var secondsRemaining by remember { mutableIntStateOf(90) }
    var phase by remember { mutableStateOf("Breathe in") }
    var phaseSeconds by remember { mutableIntStateOf(0) }
    var isComplete by remember { mutableStateOf(false) }

    // Breathing animation
    val breathProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Breathing cycle: 4s in, 4s hold, 4s out
        while (secondsRemaining > 0) {
            // Breathe in
            phase = "Breathe in"
            breathProgress.animateTo(1f, animationSpec = tween(4000, easing = EaseInOutCubic))

            if (secondsRemaining <= 0) break

            // Hold
            phase = "Hold"
            delay(4000)

            if (secondsRemaining <= 0) break

            // Breathe out
            phase = "Breathe out"
            breathProgress.animateTo(0f, animationSpec = tween(4000, easing = EaseInOutCubic))
        }
    }

    // Timer
    LaunchedEffect(Unit) {
        while (secondsRemaining > 0) {
            delay(1000)
            secondsRemaining--
            phaseSeconds++
        }
        isComplete = true
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            phase,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Breathing circle
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 4.dp.toPx()
                val radius = (size.minDimension / 2) * (0.3f + 0.7f * breathProgress.value)

                // Outer track
                drawCircle(
                    color = trackColor,
                    radius = size.minDimension / 2,
                    style = Stroke(width = strokeWidth)
                )

                // Breathing circle
                drawCircle(
                    color = primaryColor.copy(alpha = 0.2f + 0.3f * breathProgress.value),
                    radius = radius
                )

                drawCircle(
                    color = primaryColor,
                    radius = radius,
                    style = Stroke(width = strokeWidth)
                )
            }

            Text(
                "${secondsRemaining}s",
                style = MaterialTheme.typography.headlineLarge
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (!isComplete) {
            Text(
                "This cannot be skipped.\nThe urge will pass.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                "The exercise is complete.",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Do you still want to loosen this protection?\n" +
                    "A 24-hour cooling-off period will begin.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    scope.launch {
                        repo.requestLoosening(restrictionId)
                        onComplete()
                    }
                }
            ) {
                Text("Yes, start cooling-off period")
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onBack) {
                Text("No, keep it active")
            }
        }
    }
}
