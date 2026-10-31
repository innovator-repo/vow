package com.vow.app.ui.screens.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vow.app.data.model.EcoCounters
import com.vow.app.data.model.Profile
import com.vow.app.designsystem.LumeMotion
import com.vow.app.focus.FocusLogic
import com.vow.app.focus.FocusSnapshot
import com.vow.app.focus.FocusSnapshotLoader
import com.vow.app.ui.components.*
import com.vow.app.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repo: VowRepository,
    openControls: Boolean,
    onNavigateToStats: () -> Unit,
    onNavigateToRestrictions: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToCoolingOff: () -> Unit,
    onNavigateToNightMode: () -> Unit,
    onNavigateToAppRules: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val restrictions by repo.prefs.getRestrictions().collectAsState(initial = emptyList())
    val profiles by repo.prefs.getProfiles().collectAsState(initial = emptyList())
    val appRules by repo.prefs.getAppRules().collectAsState(initial = emptyList())
    val youtubeAllowance by repo.prefs.getYouTubeAllowance().collectAsState(
        initial = com.vow.app.data.model.YouTubeAllowance()
    )
    val coolingOff by repo.prefs.getCoolingOffRequests().collectAsState(initial = emptyList())
    val eco by repo.prefs.getEcoCounters(repo.todayKey()).collectAsState(initial = EcoCounters(repo.todayKey()))
    val night by produceState(false) { value = try { repo.isNightModeActive() } catch (_: Exception) { false } }
    val snapshot by produceState(FocusSnapshot(), profiles) {
        while (true) {
            value = FocusSnapshotLoader.load(context)
            kotlinx.coroutines.delay(60_000)
        }
    }

    val activeRules = restrictions.count { it.enabled }
    val pendingCoolingOff = coolingOff.count { !it.completed && !it.cancelled }
    val focusOn = FocusLogic.focusOn(profiles)
    val profileName = FocusLogic.profileName(profiles).let { if (focusOn) it else "" }
    val haptics = rememberHaptics()
    val blockedApps = appRules.filter { it.blocked }.take(3)

    val contextWord = when {
        night -> "NIGHT MODE ON"
        focusOn -> profileName.uppercase()
        else -> "FOCUS OFF"
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(Modifier.fillMaxSize().auroraGlow().padding(padding)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(vowAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.background, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "VOW · $contextWord",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (pendingCoolingOff > 0) {
                        BadgedBox(badge = { Badge(containerColor = vowWarm) { Text("$pendingCoolingOff", color = Ink) } }) {
                            IconButton(onClick = { haptics.click(); onNavigateToCoolingOff() }) {
                                Icon(Icons.Default.HourglassTop, "Pending requests", tint = vowWarm)
                            }
                        }
                    }
                    IconButton(onClick = { haptics.click(); onNavigateToSettings() }) {
                        Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(bottom = PEEK_HEIGHT + 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CrashReportCard()
                    SetupChecklistCard()
                    Spacer(Modifier.height(12.dp))
                    StaggeredItem(0) {
                        ReclaimedTimeRing(
                            reclaimedMinutes = snapshot.reclaimedMinutes,
                            isToday = snapshot.isToday,
                            modifier = Modifier.size(200.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        FocusLogic.heroTitle(focusOn, activeRules),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        FocusLogic.heroSubtitle(profileName, activeRules),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (restrictions.any { it.type == RestrictionType.YOUTUBE_LIMIT && it.enabled }) {
                        Spacer(Modifier.height(16.dp))
                        YouTubeAllowanceCard(
                            usedMinutes = youtubeAllowance.usedTodayMinutes,
                            allowanceMinutes = youtubeAllowance.currentAllowanceMinutes
                        )
                    }
                }
            }

            ControlsDrawer(
                startOpen = openControls,
                summary = "$activeRules rules on" + if (eco.shortVideoBlocks > 0) " · ${eco.shortVideoBlocks} blocks today" else "",
                onStats = onNavigateToStats,
                onRules = onNavigateToRestrictions
            ) {
                // Profile switches: turning one on tightens; turning one off happens here, behind the app lock.
                if (profiles.isEmpty()) {
                    DrawerRow("No profiles yet", "Create Work, Study or Bedtime", onClick = onNavigateToProfiles)
                } else {
                    profiles.forEach { p ->
                        DrawerRow(
                            p.name,
                            profileDetail(p),
                            onClick = onNavigateToProfiles
                        ) {
                            HapticSwitch(checked = p.isActive, onCheckedChange = { on ->
                                scope.launch {
                                    repo.prefs.saveProfiles(FocusLogic.setProfile(repo.prefs.getProfiles().first(), p.id, on))
                                }
                            })
                        }
                    }
                }
                DrawerRow(
                    "Cooling-off",
                    if (pendingCoolingOff > 0) "$pendingCoolingOff waiting" else "Loosening waits before it applies",
                    onClick = onNavigateToCoolingOff
                )
                DrawerRow("Night mode", "Essentials only at night", onClick = onNavigateToNightMode)
                DrawerRow("App rules", "Time limits and blocking", onClick = onNavigateToAppRules)

                Text(
                    "TODAY'S TOP BLOCKED",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp)
                )
                DrawerRow("Short videos", "${eco.shortVideoBlocks} blocks today")
                blockedApps.forEach { DrawerRow(it.appName, "Blocked") }
            }
        }
    }
}

private fun profileDetail(p: Profile): String = buildList {
    if (p.blockedApps.isNotEmpty()) add("${p.blockedApps.size} apps")
    if (p.blockShortVideos) add("short videos")
    if (p.blockAdultSites) add("adult filter")
}.joinToString(", ").ifEmpty { "No extra blocks" }

private val PEEK_HEIGHT = 170.dp

/** Bottom sheet with a grab handle: closed it shows the title, summary and chips; open it shows the full list. */
@Composable
private fun ControlsDrawer(
    startOpen: Boolean,
    summary: String,
    onStats: () -> Unit,
    onRules: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val open = remember { Animatable(if (startOpen) 1f else 0f) }
    LaunchedEffect(startOpen) { if (startOpen) open.animateTo(1f, LumeMotion.spatial()) }
    BackHandler(enabled = open.value > 0.5f) { scope.launch { open.animateTo(0f, LumeMotion.spatial()) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetHeight = maxHeight - 72.dp
        val travelPx = with(density) { (sheetHeight - PEEK_HEIGHT).toPx() }
        fun settle(to: Float) = scope.launch { open.animateTo(to, LumeMotion.spatial()); haptics.tick() }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeight)
                .graphicsLayer { translationY = travelPx * (1f - open.value) }
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            scope.launch { open.snapTo((open.value - delta / travelPx).coerceIn(0f, 1f)) }
                        },
                        onDragStopped = { velocity ->
                            val to = when {
                                velocity < -700f -> 1f
                                velocity > 700f -> 0f
                                else -> if (open.value > 0.5f) 1f else 0f
                            }
                            settle(to)
                        }
                    )
                    .padding(horizontal = 20.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth().height(48.dp)
                        .clickable { settle(if (open.value > 0.5f) 0f else 1f) }
                        .semantics { contentDescription = "Controls drawer" },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.width(40.dp).height(4.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.padding(top = 8.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShortcutChip("Stats", Icons.Default.BarChart, onStats)
                    ShortcutChip("Rules", Icons.Default.Checklist, onRules)
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                content = content
            )
        }
    }
}

@Composable
private fun ShortcutChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable { haptics.click(); onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = vowAccent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DrawerRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.invoke()
        }
        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 0.5.dp)
    }
}

@Composable
private fun ReclaimedTimeRing(reclaimedMinutes: Long, isToday: Boolean, modifier: Modifier = Modifier) {
    val accent = vowAccent
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val target = FocusLogic.ringFraction(reclaimedMinutes)

    val ringProgress = remember { Animatable(0f) }
    LaunchedEffect(target) { ringProgress.animateTo(target, tween(1600, easing = EaseOutCubic)) }

    val glow = rememberInfiniteTransition(label = "glow")
    val glowAlpha by glow.animateFloat(
        initialValue = 0.10f, targetValue = 0.22f,
        animationSpec = infiniteRepeatable(tween(2600, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            val strokeWidth = 14.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            drawCircle(
                brush = Brush.radialGradient(listOf(accent.copy(alpha = glowAlpha), Color.Transparent)),
                radius = size.minDimension * 0.62f
            )
            drawArc(
                color = trackColor, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = accent, startAngle = -90f, sweepAngle = 360f * ringProgress.value, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(FocusLogic.formatDuration(reclaimedMinutes), style = MaterialTheme.typography.displayMedium)
            Text(
                if (isToday) "reclaimed today" else "reclaimed in total",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun YouTubeAllowanceCard(usedMinutes: Int, allowanceMinutes: Int) {
    val progress = if (allowanceMinutes > 0) usedMinutes.toFloat() / allowanceMinutes else 0f
    val color = when {
        progress >= 1f -> vowAlert
        progress >= 0.8f -> vowWarm
        else -> vowAccent
    }
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(1200, easing = EaseOutCubic), label = "yt")

    VowCard {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Default.PlayCircle, VowRose, size = 36.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("YouTube today", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${(allowanceMinutes - usedMinutes).coerceAtLeast(0)} min left",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("${usedMinutes}/${allowanceMinutes}m", style = MaterialTheme.typography.titleMedium, color = color)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().height(8.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
            ) {
                Box(
                    Modifier.fillMaxWidth(animated).fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(vowAccent, color)), CircleShape)
                )
            }
        }
    }
}
