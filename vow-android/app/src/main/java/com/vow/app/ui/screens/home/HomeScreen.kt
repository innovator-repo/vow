package com.vow.app.ui.screens.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vow.app.data.model.RestrictionType
import com.vow.app.data.model.SealState
import com.vow.app.data.repository.VowRepository
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import com.vow.app.ui.components.*
import com.vow.app.ui.theme.*
import java.util.Calendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repo: VowRepository,
    onNavigateToRestrictions: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToNightMode: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToAppRules: () -> Unit,
    onNavigateToCoolingOff: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val restrictions by repo.prefs.getRestrictions().collectAsState(initial = emptyList())
    val stats by repo.prefs.getStats().collectAsState(initial = com.vow.app.data.model.VowStats())
    val youtubeAllowance by repo.prefs.getYouTubeAllowance().collectAsState(
        initial = com.vow.app.data.model.YouTubeAllowance()
    )
    val coolingOff by repo.prefs.getCoolingOffRequests().collectAsState(initial = emptyList())

    val activeRestrictions = restrictions.count { it.enabled }
    val sealedRestrictions = restrictions.count { it.sealState == SealState.SEALED }
    val pendingCoolingOff = coolingOff.count { !it.completed && !it.cancelled }

    val haptics = rememberHaptics()
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Rest well"
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .auroraGlow()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(greeting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Vow", style = MaterialTheme.typography.displaySmall)
                }
                if (pendingCoolingOff > 0) {
                    BadgedBox(badge = { Badge(containerColor = VowAmber) { Text("$pendingCoolingOff", color = Ink) } }) {
                        IconButton(onClick = { haptics.click(); onNavigateToCoolingOff() }) {
                            Icon(Icons.Default.HourglassTop, "Pending requests", tint = VowAmber)
                        }
                    }
                }
            }

            StaggeredItem(0) { SetupChecklistCard() }

            StaggeredItem(1) {
                ReclaimedTimeRing(
                    reclaimedMinutes = stats.totalReclaimedMinutes,
                    modifier = Modifier.size(232.dp).padding(vertical = 8.dp)
                )
            }

            StaggeredItem(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    Pill("$activeRestrictions active", VowTeal)
                    if (sealedRestrictions > 0) Pill("$sealedRestrictions sealed", VowSeal)
                    if (pendingCoolingOff > 0) Pill("$pendingCoolingOff cooling", VowAmber)
                }
            }

            if (restrictions.any { it.type == RestrictionType.YOUTUBE_LIMIT && it.enabled }) {
                Spacer(modifier = Modifier.height(12.dp))
                StaggeredItem(3) {
                    YouTubeAllowanceCard(
                        usedMinutes = youtubeAllowance.usedTodayMinutes,
                        allowanceMinutes = youtubeAllowance.currentAllowanceMinutes
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            SectionLabel("Your protections", Modifier.fillMaxWidth())

            val cards = listOf(
                HomeCardData("Restrictions", "$activeRestrictions protections active", Icons.Default.Shield, VowTeal, onNavigateToRestrictions),
                HomeCardData("Profiles", "Work, Study, Bedtime", Icons.Default.Tune, VowSky, onNavigateToProfiles),
                HomeCardData("Night Mode", "Essentials only after 10 PM", Icons.Outlined.DarkMode, VowSeal, onNavigateToNightMode),
                HomeCardData("App Rules", "Time limits and blocking", Icons.Default.Apps, VowAmber, onNavigateToAppRules),
                HomeCardData(
                    "Your Progress",
                    "${stats.totalReclaimedMinutes / 60}h ${stats.totalReclaimedMinutes % 60}m reclaimed",
                    Icons.Default.TrendingUp, VowGreen, onNavigateToStats
                )
            )
            cards.forEachIndexed { i, card ->
                StaggeredItem(4 + i) { HomeCard(card) }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private data class HomeCardData(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val onClick: () -> Unit
)

@Composable
private fun ReclaimedTimeRing(reclaimedMinutes: Long, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val target = (reclaimedMinutes.toFloat() / (reclaimedMinutes + 60).toFloat()).coerceIn(0.02f, 0.95f)

    val ringProgress = remember { Animatable(0f) }
    LaunchedEffect(target) { ringProgress.animateTo(target, tween(1600, easing = EaseOutCubic)) }

    val shownMinutes by animateIntAsState(
        targetValue = reclaimedMinutes.toInt(),
        animationSpec = tween(1600, easing = EaseOutCubic),
        label = "minutes"
    )

    val glow = rememberInfiniteTransition(label = "glow")
    val glowAlpha by glow.animateFloat(
        initialValue = 0.10f, targetValue = 0.22f,
        animationSpec = infiniteRepeatable(tween(2600, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            val strokeWidth = 14.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            drawCircle(
                brush = Brush.radialGradient(listOf(VowTeal.copy(alpha = glowAlpha), Color.Transparent)),
                radius = size.minDimension * 0.62f
            )
            drawArc(
                color = trackColor, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            rotate(-90f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(VowTeal, VowSky, VowSeal, VowTeal)),
                    startAngle = 0f, sweepAngle = 360f * ringProgress.value, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val h = shownMinutes / 60
            val m = shownMinutes % 60
            Text(
                if (h > 0) "${h}h ${m}m" else "${m}m",
                style = MaterialTheme.typography.displayMedium
            )
            Text(
                "TIME RECLAIMED",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun YouTubeAllowanceCard(usedMinutes: Int, allowanceMinutes: Int) {
    val progress = if (allowanceMinutes > 0) usedMinutes.toFloat() / allowanceMinutes else 0f
    val color = when {
        progress >= 1f -> VowRose
        progress >= 0.8f -> VowAmber
        else -> VowTeal
    }
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(1200, easing = EaseOutCubic), label = "yt")

    VowCard {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Default.PlayCircle, VowRose, size = 36.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("YouTube today", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${(allowanceMinutes - usedMinutes).coerceAtLeast(0)} min left",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("${usedMinutes}/${allowanceMinutes}m", style = MaterialTheme.typography.titleMedium, color = color)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().height(8.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
            ) {
                Box(
                    Modifier.fillMaxWidth(animated).fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(VowTeal, color)), CircleShape)
                )
            }
        }
    }
}

@Composable
private fun HomeCard(card: HomeCardData) {
    VowCard(onClick = card.onClick, modifier = Modifier.padding(vertical = 5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBubble(card.icon, card.accent)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(card.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(card.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
