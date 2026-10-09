@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vow.app.ui.screens.settings

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
import androidx.compose.ui.unit.dp
import com.vow.app.data.model.*
import com.vow.app.data.repository.VowRepository
import com.vow.app.ui.theme.VowSeal
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestrictionsScreen(
    repo: VowRepository,
    onNavigateToSeal: (String) -> Unit,
    onNavigateToKeywords: () -> Unit,
    onNavigateToBypass: (String) -> Unit,
    onBack: () -> Unit
) {
    val restrictions by repo.prefs.getRestrictions().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Restrictions") },
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
            Text(
                "Tightening is instant. Loosening takes 24+ hours.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            for (restriction in restrictions) {
                RestrictionCard(
                    restriction = restriction,
                    onToggle = { enabled ->
                        scope.launch {
                            if (enabled) {
                                repo.enableRestriction(restriction.type, restriction.reason, restriction.ifThenPlan)
                            } else {
                                // Start cooling-off period
                                onNavigateToBypass(restriction.id)
                            }
                        }
                    },
                    onSeal = { onNavigateToSeal(restriction.id) },
                    onKeywords = if (restriction.type == RestrictionType.KEYWORD_BLOCK) onNavigateToKeywords else null
                )
            }

            // Add new restriction
            Spacer(modifier = Modifier.height(16.dp))
            AddRestrictionSection(repo, restrictions)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RestrictionCard(
    restriction: Restriction,
    onToggle: (Boolean) -> Unit,
    onSeal: () -> Unit,
    onKeywords: (() -> Unit)?
) {
    val isSealed = restriction.sealState == SealState.SEALED
    val isPendingSeal = restriction.sealState == SealState.PENDING_SEAL

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSealed -> VowSeal.copy(alpha = 0.08f)
                restriction.enabled -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            restrictionLabel(restriction.type),
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (isSealed) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Sealed",
                                modifier = Modifier.size(16.dp),
                                tint = VowSeal
                            )
                            Text(
                                " Sealed",
                                style = MaterialTheme.typography.labelSmall,
                                color = VowSeal
                            )
                        }
                        if (isPendingSeal) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "⏳ Sealing in progress",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    Text(
                        restrictionDescription(restriction.type),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!isSealed) {
                    Switch(
                        checked = restriction.enabled,
                        onCheckedChange = onToggle
                    )
                }
            }

            if (restriction.enabled && !isSealed) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onSeal,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VowSeal)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Seal forever")
                    }
                    if (onKeywords != null) {
                        OutlinedButton(onClick = onKeywords) {
                            Text("Edit keywords")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddRestrictionSection(repo: VowRepository, existing: List<Restriction>) {
    val scope = rememberCoroutineScope()
    val allTypes = RestrictionType.entries
    val missingTypes = allTypes.filter { type -> existing.none { it.type == type } }

    if (missingTypes.isEmpty()) return

    Text("Add protection", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))

    for (type in missingTypes) {
        OutlinedCard(
            onClick = {
                scope.launch {
                    repo.enableRestriction(type, "", "")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(restrictionLabel(type), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun restrictionLabel(type: RestrictionType): String = when (type) {
    RestrictionType.DNS -> "DNS Protection"
    RestrictionType.DATE_TIME -> "Date & Time Lock"
    RestrictionType.UNINSTALL -> "Uninstall Protection"
    RestrictionType.SHORT_VIDEO -> "Short Video Block"
    RestrictionType.ADULT_SITES -> "Adult Site Block"
    RestrictionType.KEYWORD_BLOCK -> "Keyword Blocking"
    RestrictionType.NIGHT_MODE -> "Night Mode"
    RestrictionType.APP_LIMIT -> "App Time Limits"
    RestrictionType.YOUTUBE_LIMIT -> "YouTube Limit"
}

private fun restrictionDescription(type: RestrictionType): String = when (type) {
    RestrictionType.DNS -> "Prevents changing private DNS settings"
    RestrictionType.DATE_TIME -> "Prevents changing date and time"
    RestrictionType.UNINSTALL -> "Makes Vow difficult to remove"
    RestrictionType.SHORT_VIDEO -> "Blocks Shorts, Reels, and TikTok feeds"
    RestrictionType.ADULT_SITES -> "Blocks adult websites via DNS filtering"
    RestrictionType.KEYWORD_BLOCK -> "Blocks websites containing specific keywords"
    RestrictionType.NIGHT_MODE -> "Essentials only during night hours"
    RestrictionType.APP_LIMIT -> "Daily time limits for chosen apps"
    RestrictionType.YOUTUBE_LIMIT -> "Auto-managed YouTube daily allowance"
}
