package com.vow.app.ui.screens.night

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.vow.app.data.model.NightModeConfig
import com.vow.app.data.repository.VowRepository
import com.vow.app.ui.theme.VowSeal
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightModeScreen(
    repo: VowRepository,
    onBack: () -> Unit
) {
    val config by repo.prefs.getNightConfig().collectAsState(initial = NightModeConfig())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Night Mode") },
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
                .verticalScroll(rememberScrollState())
        ) {
            Icon(
                Icons.Default.Bedtime,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterHorizontally),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "After 10 PM, your brain is least able to resist impulses. " +
                    "Night mode removes choices instead of asking for willpower.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Enable toggle
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Night Mode", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Essentials only during night hours",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    com.vow.app.ui.components.HapticSwitch(
                        checked = config.enabled,
                        onCheckedChange = {
                            scope.launch {
                                repo.prefs.saveNightConfig(config.copy(enabled = it))
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Time settings
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Schedule", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Start")
                        Text(
                            "${config.startHour.toString().padStart(2, '0')}:${config.startMinute.toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("End")
                        Text(
                            "${config.endHour.toString().padStart(2, '0')}:${config.endMinute.toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Wind-down")
                        Text(
                            "${config.windDownMinutes} min before",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // What's allowed
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Essentials (always allowed)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Phone & Dialer", style = MaterialTheme.typography.bodyMedium)
                    Text("• Messages", style = MaterialTheme.typography.bodyMedium)
                    Text("• Maps", style = MaterialTheme.typography.bodyMedium)
                    Text("• Alarm / Clock", style = MaterialTheme.typography.bodyMedium)
                    Text("• Apps you mark as \"Night OK\" in App Rules", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // How it works
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("How it works", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "• Wind-down: screen dims gradually 30 min before\n" +
                            "• Night mode: only essential apps open\n" +
                            "• Non-essential apps show a calm \"closed until morning\" screen\n" +
                            "• No scolding — the door is simply not available",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
