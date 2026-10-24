@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.vow.app.ui.screens.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vow.app.data.model.AppRule
import com.vow.app.data.repository.VowRepository
import kotlinx.coroutines.launch

@Composable
fun AppRulesScreen(
    repo: VowRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appRules by repo.prefs.getAppRules().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val feedback = com.vow.app.ui.components.rememberEditFeedback()

    val installedApps by produceState(initialValue = emptyList<InstalledApp>()) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            getInstalledApps(context).sortedBy { it.appName.lowercase() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("App Rules") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Text(
                    "Set daily time limits or block apps entirely. Stricter changes apply now; looser ones take 24 hours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Show configured rules first
            if (appRules.isNotEmpty()) {
                item {
                    Text("Configured", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                }
                items(appRules, key = { it.packageName }) { rule ->
 androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.animateItemPlacement()) {
                    AppRuleCard(
                        rule = rule,
                        onUpdate = { updated ->
                            scope.launch { feedback(repo.updateAppRule(updated.packageName, updated)) }
                        },
                        onRemove = {
                            scope.launch { feedback(repo.updateAppRule(rule.packageName, null)) }
                        }
                    )
                }
}
            }

            // Show available apps
            val configuredPkgs = appRules.map { it.packageName }.toSet()
            val available = installedApps.filter { it.packageName !in configuredPkgs }

            item {
                Text("Add app", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            }

            items(available, key = { it.packageName }) { app ->
 androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.animateItemPlacement()) {
                OutlinedCard(
                    onClick = {
                        scope.launch {
                            val newRule = AppRule(
                                packageName = app.packageName,
                                appName = app.appName,
                                dailyLimitMinutes = 60
                            )
                            feedback(repo.updateAppRule(newRule.packageName, newRule))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        app.appName,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
}
        }
    }
}

@Composable
private fun AppRuleCard(
    rule: AppRule,
    onUpdate: (AppRule) -> Unit,
    onRemove: () -> Unit
) {
    var showLimitDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rule.appName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                // Block toggle
                FilterChip(
                    selected = rule.blocked,
                    onClick = { onUpdate(rule.copy(blocked = !rule.blocked)) },
                    label = { Text(if (rule.blocked) "Blocked" else "Block") }
                )
            }

            if (!rule.blocked) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Limit: ${rule.dailyLimitMinutes ?: "none"} min/day",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = { showLimitDialog = true }) {
                        Text("Change")
                    }
                    Spacer(modifier = Modifier.weight(1f))

                    // Night mode allowlist
                    FilterChip(
                        selected = rule.allowedInNightMode,
                        onClick = { onUpdate(rule.copy(allowedInNightMode = !rule.allowedInNightMode)) },
                        label = { Text("Night OK") }
                    )
                }
            }

            TextButton(
                onClick = onRemove,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Remove rule")
            }
        }
    }

    if (showLimitDialog) {
        var limitText by remember { mutableStateOf(rule.dailyLimitMinutes?.toString() ?: "60") }
        AlertDialog(
            onDismissRequest = { showLimitDialog = false },
            title = { Text("Daily limit for ${rule.appName}") },
            text = {
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it.filter { c -> c.isDigit() } },
                    label = { Text("Minutes per day") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val mins = limitText.toIntOrNull()
                    onUpdate(rule.copy(dailyLimitMinutes = mins))
                    showLimitDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showLimitDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private data class InstalledApp(val packageName: String, val appName: String)

private fun getInstalledApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    return packages
        .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
        .filter { it.packageName != context.packageName }
        .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString()) }
}
