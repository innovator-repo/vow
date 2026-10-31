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
import androidx.compose.ui.unit.sp
import com.vow.app.data.model.*
import com.vow.app.data.repository.VowRepository
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import com.vow.app.ui.components.*
import com.vow.app.ui.theme.*
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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

            restrictions.forEachIndexed { index, restriction ->
                StaggeredItem(index) { RestrictionCard(
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
                    onPlanException = { note -> scope.launch { repo.declareException(restriction.id, note) } },
                    onCancelException = { scope.launch { repo.cancelException(restriction.id) } },
                    onKeywords = if (restriction.type == RestrictionType.KEYWORD_BLOCK) onNavigateToKeywords else null
                ) }
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
    onPlanException: (String) -> Unit,
    onCancelException: () -> Unit,
    onKeywords: (() -> Unit)?
) {
    val now = System.currentTimeMillis()
    val exceptionActive = ExceptionPolicy.activeState(restriction, now) == ExceptionState.PLANNED
    val exceptionPending = ExceptionPolicy.isPending(restriction, now)
    var planning by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    val isSealed = restriction.sealState == SealState.SEALED
    val isPendingSeal = restriction.sealState == SealState.PENDING_SEAL

    val haptics = rememberHaptics()
    val accent = if (isSealed) VowSeal else vowAccent
    if (planning) {
        PlanExceptionDialog(
            note = note, onNote = { note = it },
            onDismiss = { planning = false },
            onConfirm = { onPlanException(note); planning = false; note = "" }
        )
    }
    VowCard(
        modifier = Modifier.padding(vertical = 5.dp),
        containerColor = when {
            isSealed -> VowSeal.copy(alpha = 0.10f)
            restriction.enabled -> vowAccent.copy(alpha = 0.06f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        borderBrush = if (isSealed) Brush.linearGradient(listOf(VowSeal.copy(alpha = 0.7f), VowSealDeep.copy(alpha = 0.2f))) else null
    ) {
        Column(modifier = Modifier.padding(16.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(restrictionIcon(restriction.type), if (restriction.enabled || isSealed) accent else MaterialTheme.colorScheme.onSurfaceVariant, size = 40.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(restrictionLabel(restriction.type), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        restrictionDescription(restriction.type),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isSealed) {
                    Icon(Icons.Default.Lock, contentDescription = "Sealed", tint = VowSeal, modifier = Modifier.size(20.dp))
                } else {
                    HapticSwitch(checked = restriction.enabled, onCheckedChange = onToggle)
                }
            }

            if (isSealed || isPendingSeal) {
                Spacer(modifier = Modifier.height(10.dp))
                Pill(if (isSealed) "Sealed forever" else "Sealing \u2014 confirm in 24h", if (isSealed) VowSeal else vowWarm)
            }

            if (exceptionActive || exceptionPending) {
                Spacer(modifier = Modifier.height(10.dp))
                Pill(if (exceptionActive) "Planned exception today" else "Exception starts in 24h", VowSeal)
                if (restriction.exceptionNote.isNotBlank()) {
                    Text(
                        restriction.exceptionNote,
                        style = ReasonStyle.copy(fontSize = 15.sp, lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                TextButton(onClick = { haptics.click(); onCancelException() }) { Text("Withdraw exception") }
            } else if (restriction.enabled && !isSealed) {
                TextButton(onClick = { haptics.click(); planning = true }) { Text("Plan an exception") }
            }

            if (restriction.enabled && !isSealed && !isPendingSeal) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { haptics.click(); onSeal() },
                        border = BorderStroke(1.dp, VowSeal.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VowSeal)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Seal forever")
                    }
                    if (onKeywords != null) {
                        OutlinedButton(onClick = { haptics.click(); onKeywords() }) {
                            Text("Edit keywords")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanExceptionDialog(note: String, onNote: (String) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Plan an exception") },
        text = {
            Column {
                Text(
                    "Decide now, while you're calm. Before 08:00 it starts right away; any other time it starts in 24 hours " +
                        "and covers that one day. The protection itself stays on.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = note, onValueChange = onNote, label = { Text("Why, in one sentence") })
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = note.isNotBlank()) { Text("Plan it") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}

@Composable
private fun AddRestrictionSection(repo: VowRepository, existing: List<Restriction>) {
    val scope = rememberCoroutineScope()
    val allTypes = RestrictionType.entries
    val missingTypes = allTypes.filter { type -> existing.none { it.type == type } }

    if (missingTypes.isEmpty()) return

    val haptics = rememberHaptics()
    SectionLabel("Add protection")

    for (type in missingTypes) {
        VowCard(
            onClick = {
                haptics.confirm()
                scope.launch { repo.enableRestriction(type, "", "") }
            },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBubble(Icons.Default.Add, vowAccent, size = 32.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(restrictionLabel(type), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("Add", style = MaterialTheme.typography.labelLarge, color = vowAccent)
            }
        }
    }
}

private fun restrictionIcon(type: RestrictionType) = when (type) {
    RestrictionType.DNS -> Icons.Default.Dns
    RestrictionType.DATE_TIME -> Icons.Default.Schedule
    RestrictionType.UNINSTALL -> Icons.Default.DeleteForever
    RestrictionType.SHORT_VIDEO -> Icons.Default.VideoLibrary
    RestrictionType.ADULT_SITES -> Icons.Default.Shield
    RestrictionType.KEYWORD_BLOCK -> Icons.Default.Abc
    RestrictionType.NIGHT_MODE -> Icons.Default.Bedtime
    RestrictionType.APP_LIMIT -> Icons.Default.HourglassBottom
    RestrictionType.YOUTUBE_LIMIT -> Icons.Default.PlayCircle
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
