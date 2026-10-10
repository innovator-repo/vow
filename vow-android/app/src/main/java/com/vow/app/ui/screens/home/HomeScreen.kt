package com.vow.app.ui.screens.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import com.vow.app.ui.theme.VowGreen
import com.vow.app.ui.theme.VowSeal
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Vow", style = MaterialTheme.typography.headlineMedium)
                },
                actions = {
                    if (pendingCoolingOff > 0) {
                        BadgedBox(badge = { Badge { Text("$pendingCoolingOff") } }) {
                            IconButton(onClick = onNavigateToCoolingOff) {
                                Icon(Icons.Default.Pending, "Pending requests")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            SetupChecklistCard()

            // Reclaimed time ring
            ReclaimedTimeRing(
                reclaimedMinutes = stats.totalReclaimedMinutes,
                modifier = Modifier.size(180.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Status summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatusChip("$activeRestrictions active", VowGreen)
                if (sealedRestrictions > 0) {
                    StatusChip("$sealedRestrictions sealed", VowSeal)
                }
            }

            // YouTube allowance
            if (restrictions.any { it.type == RestrictionType.YOUTUBE_LIMIT && it.enabled }) {
                Spacer(modifier = Modifier.height(16.dp))
                YouTubeAllowanceCard(
                    usedMinutes = youtubeAllowance.usedTodayMinutes,
                    allowanceMinutes = youtubeAllowance.currentAllowanceMinutes
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Navigation cards
            HomeCard(
                title = "Restrictions",
                subtitle = "$activeRestrictions protections active",
                icon = Icons.Default.Shield,
                onClick = onNavigateToRestrictions
            )

            HomeCard(
                title = "Profiles",
                subtitle = "Work, Study, Bedtime",
                icon = Icons.Default.Tune,
                onClick = onNavigateToProfiles
            )

            HomeCard(
                title = "Night Mode",
                subtitle = "Essentials only after 10 PM",
                icon = Icons.Outlined.DarkMode,
                onClick = onNavigateToNightMode
            )

            HomeCard(
                title = "App Rules",
                subtitle = "Time limits and blocking",
                icon = Icons.Default.Apps,
                onClick = onNavigateToAppRules
            )

            HomeCard(
                title = "Your Progress",
                subtitle = "${stats.totalReclaimedMinutes / 60}h ${stats.totalReclaimedMinutes % 60}m reclaimed",
                icon = Icons.Default.TrendingUp,
                onClick = onNavigateToStats
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ReclaimedTimeRing(reclaimedMinutes: Long, modifier: Modifier = Modifier) {
    val hours = reclaimedMinutes / 60
    val mins = reclaimedMinutes % 60
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)

    // Animate the ring
    val animatedProgress by animateFloatAsState(
        targetValue = (reclaimedMinutes.toFloat() / (reclaimedMinutes + 60).toFloat()).coerceIn(0f, 0.95f),
        animationSpec = tween(1500, easing = EaseOutCubic),
        label = "ring"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val strokeWidth = 12.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            // Track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress
            drawArc(
                color = primaryColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (hours > 0) "${hours}h ${mins}m" else "${mins}m",
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                "reclaimed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
private fun YouTubeAllowanceCard(usedMinutes: Int, allowanceMinutes: Int) {
    val progress = if (allowanceMinutes > 0) usedMinutes.toFloat() / allowanceMinutes else 0f
    val color = when {
        progress >= 1f -> MaterialTheme.colorScheme.error
        progress >= 0.8f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = color)
                Spacer(modifier = Modifier.width(8.dp))
                Text("YouTube today", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "${usedMinutes}m / ${allowanceMinutes}m",
                    style = MaterialTheme.typography.bodyMedium,
                    color = color
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
                color = color,
                trackColor = color.copy(alpha = 0.15f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
