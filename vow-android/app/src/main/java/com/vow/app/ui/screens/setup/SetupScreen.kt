package com.vow.app.ui.screens.setup

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.vow.app.ui.components.*
import com.vow.app.ui.theme.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vow.app.data.model.RestrictionType
import com.vow.app.data.repository.VowRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    repo: VowRepository,
    onSetupComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var currentStep by remember { mutableIntStateOf(0) }
    var reason by remember { mutableStateOf("") }
    var ifThenPlan by remember { mutableStateOf("") }

    val steps = listOf("Welcome", "Your Why", "Your Plan", "Choose Protections")

    // Selected restrictions
    var blockDns by remember { mutableStateOf(false) }
    var blockDateTime by remember { mutableStateOf(false) }
    var blockUninstall by remember { mutableStateOf(true) }
    var blockShortVideos by remember { mutableStateOf(true) }
    var blockAdultSites by remember { mutableStateOf(true) }
    var blockKeywords by remember { mutableStateOf(false) }
    var enableNightMode by remember { mutableStateOf(true) }
    var enableYouTubeLimit by remember { mutableStateOf(true) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress
            val stepProgress by animateFloatAsState(
                (currentStep + 1f) / steps.size,
                spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
                label = "stepProgress"
            )
            Box(
                Modifier.fillMaxWidth().padding(bottom = 32.dp).height(6.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
            ) {
                Box(Modifier.fillMaxWidth(stepProgress).fillMaxHeight().background(AuroraBrush, CircleShape))
            }

            AnimatedContent(targetState = currentStep, label = "setup_step") { step ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (step) {
                        0 -> WelcomeStep()
                        1 -> ReasonStep(reason) { reason = it }
                        2 -> PlanStep(ifThenPlan) { ifThenPlan = it }
                        3 -> ProtectionsStep(
                            blockDns, blockDateTime, blockUninstall, blockShortVideos,
                            blockAdultSites, blockKeywords, enableNightMode, enableYouTubeLimit,
                            onDns = { blockDns = it },
                            onDateTime = { blockDateTime = it },
                            onUninstall = { blockUninstall = it },
                            onShortVideos = { blockShortVideos = it },
                            onAdultSites = { blockAdultSites = it },
                            onKeywords = { blockKeywords = it },
                            onNightMode = { enableNightMode = it },
                            onYouTubeLimit = { enableYouTubeLimit = it }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentStep > 0) {
                    QuietButton("Back", onClick = { currentStep-- })
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                GradientButton(
                    text = if (currentStep < steps.size - 1) "Continue" else "Start Vow",
                    onClick = {
                        if (currentStep < steps.size - 1) {
                            currentStep++
                        } else {
                            scope.launch {
                                // Save selected restrictions
                                val types = mutableListOf<RestrictionType>()
                                if (blockDns) types.add(RestrictionType.DNS)
                                if (blockDateTime) types.add(RestrictionType.DATE_TIME)
                                if (blockUninstall) types.add(RestrictionType.UNINSTALL)
                                if (blockShortVideos) types.add(RestrictionType.SHORT_VIDEO)
                                if (blockAdultSites) types.add(RestrictionType.ADULT_SITES)
                                if (blockKeywords) types.add(RestrictionType.KEYWORD_BLOCK)
                                if (enableNightMode) types.add(RestrictionType.NIGHT_MODE)
                                if (enableYouTubeLimit) types.add(RestrictionType.YOUTUBE_LIMIT)

                                for (type in types) {
                                    repo.enableRestriction(type, reason, ifThenPlan)
                                }

                                repo.prefs.setSetupComplete(true)
                                onSetupComplete()
                            }
                        }
                    },
                    enabled = when (currentStep) {
                        1 -> reason.length >= 10
                        2 -> ifThenPlan.length >= 10
                        else -> true
                    }
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val pulse = rememberInfiniteTransition(label = "welcome")
        val glow by pulse.animateFloat(
            0.15f, 0.35f, infiniteRepeatable(tween(2400, easing = EaseInOutSine), RepeatMode.Reverse), label = "glow"
        )
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(VowTeal.copy(alpha = glow), Color.Transparent)), CircleShape))
            Box(Modifier.size(84.dp).background(AuroraBrush, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Ink, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Vow",
            style = MaterialTheme.typography.displayMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "A commitment to yourself",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "Vow helps you keep promises to yourself about how you use your phone.\n\n" +
                "You set the rules when you're thinking clearly. " +
                "Vow makes sure those rules hold when you're not.\n\n" +
                "Everything stays on your device. No accounts, no cloud, no one watching.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun ReasonStep(reason: String, onReasonChange: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Why are you doing this?",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Write it in your own words. No one else will see this.\n" +
                "When the urge hits, Vow will show these words back to you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = reason,
            onValueChange = onReasonChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            label = { Text("Your reason") },
            placeholder = { Text("e.g., I want to be present with my family instead of scrolling...") },
            maxLines = 6,
            supportingText = { Text("${reason.length} characters (min 10)") }
        )
    }
}

@Composable
private fun PlanStep(plan: String, onPlanChange: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Your if-then plan",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Research shows that specific plans work better than willpower.\n\n" +
                "Complete this sentence:\n\"If I feel the urge to open a blocked app, I will ___\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = plan,
            onValueChange = onPlanChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            label = { Text("If I feel the urge, I will...") },
            placeholder = { Text("e.g., take a 5-minute walk, open my book, call a friend...") },
            maxLines = 6,
            supportingText = { Text("${plan.length} characters (min 10)") }
        )
    }
}

@Composable
private fun ProtectionsStep(
    blockDns: Boolean, blockDateTime: Boolean, blockUninstall: Boolean,
    blockShortVideos: Boolean, blockAdultSites: Boolean, blockKeywords: Boolean,
    enableNightMode: Boolean, enableYouTubeLimit: Boolean,
    onDns: (Boolean) -> Unit, onDateTime: (Boolean) -> Unit,
    onUninstall: (Boolean) -> Unit, onShortVideos: (Boolean) -> Unit,
    onAdultSites: (Boolean) -> Unit, onKeywords: (Boolean) -> Unit,
    onNightMode: (Boolean) -> Unit, onYouTubeLimit: (Boolean) -> Unit
) {
    Column {
        Text(
            "Choose your protections",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "You can always add more later.\nTightening is instant. Loosening takes 24+ hours.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        ProtectionToggle("Block short videos", "Shorts, Reels, TikTok feeds — without blocking the whole app", blockShortVideos, onShortVideos)
        ProtectionToggle("Block adult sites", "Built-in list of 50+ domains + heuristic detection", blockAdultSites, onAdultSites)
        ProtectionToggle("Night mode", "Essentials only after 10 PM, wind-down at 9:30 PM", enableNightMode, onNightMode)
        ProtectionToggle("YouTube limit", "Auto-set and gradually reduce your daily allowance", enableYouTubeLimit, onYouTubeLimit)
        ProtectionToggle("Prevent uninstall", "Makes Vow harder to remove", blockUninstall, onUninstall)
        ProtectionToggle("Lock DNS settings", "Prevent changing private DNS to bypass filters", blockDns, onDns)
        ProtectionToggle("Lock date & time", "Prevent clock changes that could bypass time rules", blockDateTime, onDateTime)
        ProtectionToggle("Keyword blocking", "Block websites containing specific words", blockKeywords, onKeywords)
    }
}

@Composable
private fun ProtectionToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    VowCard(
        modifier = Modifier.padding(vertical = 5.dp),
        onClick = { onCheckedChange(!checked) },
        containerColor = if (checked) VowTeal.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant,
        borderBrush = if (checked) Brush.linearGradient(listOf(VowTeal.copy(alpha = 0.55f), VowSky.copy(alpha = 0.15f))) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            HapticSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
