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
                title = { Text("Loosen Protection") },
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
            verticalArrangement = Arrangement.Center
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
                    tint = com.vow.app.ui.theme.VowSeal
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

            // Show the user's own words
            if (restriction.reason.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "You wrote this on ${dateFormat.format(Date(restriction.createdAt))}:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "\"${restriction.reason}\"",
                            style = MaterialTheme.typography.bodyLarge,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Show if-then plan
            if (restriction.ifThenPlan.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Your plan:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "If I feel the urge → ${restriction.ifThenPlan}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Show what's at stake
            if (stats.totalReclaimedMinutes > 0) {
                Text(
                    "You've reclaimed ${stats.totalReclaimedMinutes / 60}h ${stats.totalReclaimedMinutes % 60}m so far.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    "This starts a 24-hour cooling-off period.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Breathing exercise (friction)
            Text(
                "To proceed, complete a 90-second paced breathing exercise.\n" +
                    "No shortcuts. This gives the urge time to pass.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onStartBreathing) {
                Text("Begin breathing exercise")
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onBack) {
                Text("Never mind, keep it active")
            }
        }
    }
}
