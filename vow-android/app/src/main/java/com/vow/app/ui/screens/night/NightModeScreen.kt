package com.vow.app.ui.screens.night

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vow.app.data.model.NightModeConfig
import com.vow.app.data.repository.VowRepository
import com.vow.app.ui.components.*
import com.vow.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightModeScreen(
    repo: VowRepository,
    onBack: () -> Unit
) {
    val config by repo.prefs.getNightConfig().collectAsState(initial = NightModeConfig())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val feedback = rememberEditFeedback()

    fun propose(new: NightModeConfig) {
        scope.launch { feedback(repo.updateNightConfig(new)) }
    }

    fun pickTime(hour: Int, minute: Int, onPicked: (Int, Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h, m) }, hour, minute, false).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Night Mode") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .auroraGlow(VowSeal)
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StaggeredItem(0) { IconBubble(Icons.Default.Bedtime, VowSeal, size = 72.dp) }
            Spacer(Modifier.height(16.dp))
            Text(
                "Late at night your brain is least able to resist impulses. " +
                    "Night mode removes the choice instead of asking for willpower.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))

            StaggeredItem(1) {
                VowCard {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Night mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Essentials only during night hours",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HapticSwitch(checked = config.enabled, onCheckedChange = { propose(config.copy(enabled = it)) }, accent = VowSeal)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            StaggeredItem(2) {
                VowCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Starting earlier or ending later applies now. Shortening it takes 24 hours.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TimeTile("Starts", config.startHour, config.startMinute, Icons.Default.NightsStay, VowSeal, Modifier.weight(1f)) {
                                pickTime(config.startHour, config.startMinute) { h, m -> propose(config.copy(startHour = h, startMinute = m)) }
                            }
                            TimeTile("Ends", config.endHour, config.endMinute, Icons.Default.WbSunny, vowWarm, Modifier.weight(1f)) {
                                pickTime(config.endHour, config.endMinute) { h, m -> propose(config.copy(endHour = h, endMinute = m)) }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Wind-down reminder", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(15, 30, 45, 60).forEach { mins ->
                                FilterChip(
                                    selected = config.windDownMinutes == mins,
                                    onClick = { propose(config.copy(windDownMinutes = mins)) },
                                    label = { Text("$mins min") }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            StaggeredItem(3) {
                VowCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("Always allowed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Phone, messages, maps, clock and alarms, your keyboard, Settings, and apps you mark “Night OK” in App Rules.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("How it works", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "A gentle reminder arrives ${config.windDownMinutes} minutes before. Once night mode starts, " +
                                "other apps show a calm “closed until morning” screen. No scolding — the door is simply closed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TimeTile(
    label: String,
    hour: Int,
    minute: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    VowCard(onClick = onClick, modifier = modifier, containerColor = accent.copy(alpha = 0.08f)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = accent)
            }
            Spacer(Modifier.height(6.dp))
            Text("%02d:%02d".format(hour, minute), style = MaterialTheme.typography.headlineMedium)
        }
    }
}
