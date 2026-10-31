package com.vow.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vow.app.ui.components.IconBubble
import com.vow.app.ui.components.VowCard
import com.vow.app.ui.theme.vowAccent

/** Entry point to the screens that used to sit on the old home list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onRestrictions: () -> Unit,
    onProfiles: () -> Unit,
    onNightMode: () -> Unit,
    onAppRules: () -> Unit,
    onCoolingOff: () -> Unit,
    onStats: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Vow") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            SettingsRow("Restrictions", "Rules, seals and keywords", Icons.Default.Shield, onRestrictions)
            SettingsRow("Profiles", "Work, Study, Bedtime", Icons.Default.Tune, onProfiles)
            SettingsRow("Night mode", "Essentials only at night", Icons.Outlined.DarkMode, onNightMode)
            SettingsRow("App rules", "Time limits and blocking", Icons.Default.Apps, onAppRules)
            SettingsRow("Cooling-off", "Loosening waits before it applies", Icons.Default.HourglassTop, onCoolingOff)
            SettingsRow("Stats", "Time reclaimed and trends", Icons.Default.BarChart, onStats)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    VowCard(onClick = onClick, modifier = Modifier.padding(vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon, vowAccent)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
