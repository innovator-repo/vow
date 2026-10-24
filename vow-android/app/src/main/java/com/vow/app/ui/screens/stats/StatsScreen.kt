package com.vow.app.ui.screens.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vow.app.data.model.SealState
import com.vow.app.data.model.VowStats
import com.vow.app.data.repository.VowRepository
import com.vow.app.ui.theme.VowGreen
import com.vow.app.ui.theme.VowSeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    repo: VowRepository,
    onBack: () -> Unit
) {
    val stats by repo.prefs.getStats().collectAsState(initial = VowStats())
    val restrictions by repo.prefs.getRestrictions().collectAsState(initial = emptyList())
    val youtubeAllowance by repo.prefs.getYouTubeAllowance().collectAsState(
        initial = com.vow.app.data.model.YouTubeAllowance()
    )

    val context = androidx.compose.ui.platform.LocalContext.current
    // Screen time for today and the six days before it, oldest first, over every app but launchers.
    val dailyUsage by produceState<List<Int>>(emptyList()) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val tracker = com.vow.app.service.blocking.UsageTracker(context)
            (6 downTo 0).map { tracker.screenTimeMinutes(it) }
        }
    }

    val activeCount = restrictions.count { it.enabled }
    val sealedCount = restrictions.count { it.sealState == SealState.SEALED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Progress") },
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Big number
            val shown by androidx.compose.animation.core.animateIntAsState(
                stats.totalReclaimedMinutes.toInt(),
                androidx.compose.animation.core.tween(1400, easing = androidx.compose.animation.core.EaseOutCubic),
                label = "reclaimed"
            )
            Text(
                if (shown >= 60) "${shown / 60}h ${shown % 60}m" else "${shown}m",
                style = MaterialTheme.typography.displayMedium.copy(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(VowGreen, com.vow.app.ui.theme.VowTeal))
                )
            )
            Text(
                "total time reclaimed",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "This is cumulative — a slip never zeros your progress.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCard("Active", "$activeCount", "protections")
                StatCard("Sealed", "$sealedCount", "permanent")
                StatCard("Days", "${stats.daysTracked}", "tracked")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Overall daily usage
            if (dailyUsage.isNotEmpty()) {
                val today = dailyUsage.last()
                val average = dailyUsage.average().toInt()
                val peak = dailyUsage.max().coerceAtLeast(stats.baselineDailyMinutes.toInt()).coerceAtLeast(1)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Screen time, all apps", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Today")
                            Text("${today / 60}h ${today % 60}m")
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("7-day average")
                            Text("${average / 60}h ${average % 60}m")
                        }
                        if (stats.baselineDailyMinutes > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Your baseline")
                                Text("${stats.baselineDailyMinutes / 60}h ${stats.baselineDailyMinutes % 60}m")
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            dailyUsage.forEachIndexed { i, minutes ->
                                val shown by androidx.compose.animation.core.animateFloatAsState(
                                    minutes.toFloat() / peak,
                                    com.vow.app.designsystem.LumeMotion.spatial(),
                                    label = "usage$i"
                                )
                                val day = java.time.LocalDate.now().minusDays((6 - i).toLong())
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.height(80.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                                        Box(
                                            Modifier.fillMaxWidth().height((80f * shown).coerceAtLeast(4f).dp)
                                                .background(VowGreen.copy(alpha = 0.35f + 0.65f * shown), RoundedCornerShape(6.dp))
                                        )
                                    }
                                    Text(
                                        day.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // YouTube stats
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("YouTube", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Baseline")
                        Text("${youtubeAllowance.baselineMinutes} min/day")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Current allowance")
                        Text("${youtubeAllowance.currentAllowanceMinutes} min/day")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Used today")
                        Text("${youtubeAllowance.usedTodayMinutes} min")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Floor")
                        Text("${youtubeAllowance.floorMinutes} min/day")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("How reclaimed time works", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Vow measures your baseline screen time over 7 days, then tracks " +
                            "how much less you use each day. The difference accumulates as " +
                            "reclaimed time.\n\n" +
                            "This is a running total, not a streak. One bad day doesn't wipe " +
                            "out weeks of progress. Research shows all-or-nothing thinking " +
                            "(\"I already broke my streak, might as well give up\") is what " +
                            "turns a slip into a relapse.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, unit: String) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(unit, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
