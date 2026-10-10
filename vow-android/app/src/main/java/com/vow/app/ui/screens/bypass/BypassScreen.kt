package com.vow.app.ui.screens.bypass

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vow.app.data.model.SealState
import com.vow.app.data.repository.VowRepository
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import com.vow.app.ui.components.*
import com.vow.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BypassScreen(
    repo: VowRepository,
    restrictionId: String,
    onStartBreathing: () -> Unit,
    onBack: () -> Unit
) {
    val restrictions by repo.prefs.getRestrictions().collectAsState(initial = emptyList())
    val restriction = restrictions.find { it.id == restrictionId }
    val stats by repo.prefs.getStats().collectAsState(initial = com.vow.app.data.model.VowStats())
    val scope = rememberCoroutineScope()

    val dateFormat = remember { SimpleDateFormat("MMM d 'at' h:mm a", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Loosen protection") },
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (restriction == null) {
                Text("Restriction not found")
                return@Scaffold
            }

            if (restriction.sealState == SealState.SEALED) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = VowSeal
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "This protection is sealed",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    "It cannot be loosened.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Scaffold
            }

            StaggeredItem(0) {
                if (restriction.reason.isNotEmpty()) {
                    VowCard {
                        Row(Modifier.height(IntrinsicSize.Min).padding(20.dp)) {
                            Box(Modifier.width(3.dp).fillMaxHeight().background(AuroraBrush, CircleShape))
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    "You wrote this on ${dateFormat.format(Date(restriction.createdAt))}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "\u201C${restriction.reason}\u201D",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            StaggeredItem(1) {
                if (restriction.ifThenPlan.isNotEmpty()) {
                    VowCard(
                        containerColor = VowAmber.copy(alpha = 0.10f),
                        borderBrush = Brush.linearGradient(listOf(VowAmber.copy(alpha = 0.45f), VowAmber.copy(alpha = 0.1f)))
                    ) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(Icons.Default.Lightbulb, VowAmber, size = 40.dp)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("YOUR PLAN", style = MaterialTheme.typography.labelSmall, color = VowAmber)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(restriction.ifThenPlan, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            StaggeredItem(2) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (stats.totalReclaimedMinutes > 0) {
                        Pill("${stats.totalReclaimedMinutes / 60}h ${stats.totalReclaimedMinutes % 60}m reclaimed so far", VowGreen)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    Text(
                        "Loosening needs a 90-second breathing exercise, then a " +
                            "${VowRepository.coolingHours(restriction.type)}-hour cooling-off. " +
                            "No shortcuts \u2014 this gives the urge time to pass.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            StaggeredItem(3) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    GradientButton(
                        "Keep it active",
                        onClick = onBack,
                        icon = Icons.Default.Shield,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    QuietButton("Begin breathing exercise", onClick = onStartBreathing)
                }
            }
        }
    }
}
