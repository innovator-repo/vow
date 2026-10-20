package com.vow.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vow.app.data.model.SealState
import com.vow.app.data.repository.VowRepository
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.vow.app.ui.components.HoldToConfirmButton
import com.vow.app.ui.components.rememberHaptics
import com.vow.app.ui.theme.SealBrush
import com.vow.app.ui.theme.VowSeal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SealScreen(
    repo: VowRepository,
    restrictionId: String,
    onBack: () -> Unit
) {
    val restrictions by repo.prefs.getRestrictions().collectAsState(initial = emptyList())
    val restriction = restrictions.find { it.id == restrictionId }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var confirmText by remember { mutableStateOf("") }
    val confirmSentence = "I seal this forever"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lifetime Seal") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SealOrb()

            Spacer(modifier = Modifier.height(24.dp))

            if (restriction == null) {
                Text("Restriction not found")
                return@Scaffold
            }

            when (restriction.sealState) {
                SealState.UNSEALED -> {
                    Text(
                        "Seal this protection forever",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Sealing makes this restriction permanent.\n\n" +
                            "After sealing, there will be no way to disable this " +
                            "protection from within Vow. The settings entry will " +
                            "simply show a sealed state with no controls.\n\n" +
                            "This is a two-step process:\n" +
                            "1. Request the seal now\n" +
                            "2. Confirm after 24 hours by typing a sentence\n\n" +
                            "This delay exists because your calm self should make " +
                            "this decision, not an impulse.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    HoldToConfirmButton(
                        text = "Hold to begin sealing",
                        icon = Icons.Default.Lock,
                        brush = SealBrush,
                        onConfirmed = { haptics.seal(); scope.launch { repo.requestSeal(restrictionId) } }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Nothing is permanent yet \u2014 you confirm again in 24 hours.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SealState.PENDING_SEAL -> {
                    val elapsed = System.currentTimeMillis() - (restriction.sealRequestedAt ?: 0)
                    val msLeft = (24 * 60 * 60 * 1000L - elapsed).coerceAtLeast(0)
                    val left = (msLeft + 59_999) / 60_000
                    val leftText = if (left >= 60) "${left / 60}h ${left % 60}m" else "${left}m"

                    Text(
                        "Seal in progress",
                        style = MaterialTheme.typography.headlineMedium,
                        color = VowSeal
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (msLeft > 0) {
                        Text(
                            "You need to wait $leftText more before you can confirm.\n\n" +
                                "This wait ensures your decision is deliberate, " +
                                "not impulsive.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            "The waiting period is over.\n\n" +
                                "Type the sentence below exactly to confirm the seal:",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "\"$confirmSentence\"",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = com.vow.app.designsystem.Newsreader),
                            color = VowSeal
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = confirmText,
                            onValueChange = { confirmText = it },
                            label = { Text("Type the sentence") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        HoldToConfirmButton(
                            text = "Hold to seal forever",
                            icon = Icons.Default.Lock,
                            brush = SealBrush,
                            holdMillis = 3000,
                            enabled = confirmText.trim().equals(confirmSentence, ignoreCase = true),
                            onConfirmed = {
                                haptics.seal()
                                scope.launch {
                                    repo.confirmSeal(restrictionId)
                                    onBack()
                                }
                            }
                        )
                    }
                }

                SealState.SEALED -> {
                    Text(
                        "Sealed",
                        style = MaterialTheme.typography.headlineLarge,
                        color = VowSeal
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "This protection is permanent.\nThere is no undo.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SealOrb() {
    val pulse = rememberInfiniteTransition(label = "sealPulse")
    val glow by pulse.animateFloat(
        0.18f, 0.38f, infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse), label = "glow"
    )
    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(VowSeal.copy(alpha = glow), Color.Transparent)), CircleShape
            )
        )
        Box(
            Modifier.size(76.dp).background(SealBrush, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
    }
}
