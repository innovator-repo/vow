@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.vow.app.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vow.app.data.model.CoolingOffRequest
import com.vow.app.data.model.PendingChange
import com.vow.app.data.repository.LooseningResult
import com.vow.app.data.repository.VowRepository
import com.vow.app.ui.components.*
import com.vow.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoolingOffScreen(
    repo: VowRepository,
    onBack: () -> Unit
) {
    val requests by repo.prefs.getCoolingOffRequests().collectAsState(initial = emptyList())
    val changes by repo.prefs.getPendingChanges().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            repo.applyMaturedChanges()
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    val pending = requests.filter { !it.completed && !it.cancelled && !repo.isExpired(it) }
    val waiting = changes.filter { !it.applied && !it.cancelled }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Waiting") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        if (pending.isEmpty() && waiting.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconBubble(Icons.Default.CheckCircle, VowGreen, size = 64.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("Nothing is waiting", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "All your protections are in place.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)
        ) {
            if (!repo.isClockTrusted()) {
                item {
                    Text(
                        "Turn on automatic date & time in Android settings. Waits can't finish while the clock is set by hand.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = vowWarm,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            if (pending.isNotEmpty()) {
                item { SectionLabel("Turning off a protection") }
                items(pending, key = { it.id }) { request ->
 androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.animateItemPlacement()) {
                    RequestCard(request, now, dateFormat,
                        onComplete = {
                            scope.launch {
                                val msg = when (repo.completeLoosening(request.id)) {
                                    LooseningResult.DONE -> null
                                    LooseningResult.NOT_YET -> "Not ready yet."
                                    LooseningResult.OUTSIDE_HOURS -> "This can only be done between 8 AM and 8 PM."
                                    LooseningResult.EXPIRED -> "This request expired. Start a new one if you still want it."
                                    LooseningResult.CLOCK_UNTRUSTED -> "Turn on automatic date & time first."
                                    LooseningResult.NOT_FOUND -> "This request is no longer valid."
                                }
                                if (msg == null) haptics.confirm() else {
                                    haptics.reject()
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onCancel = { haptics.confirm(); scope.launch { repo.cancelLoosening(request.id) } }
                    )
                }
}
            }
            if (waiting.isNotEmpty()) {
                item { SectionLabel("Settings changes") }
                items(waiting, key = { it.id }) { change ->
 androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.animateItemPlacement()) {
                    ChangeCard(change, now, dateFormat) {
                        haptics.confirm(); scope.launch { repo.cancelPendingChange(change.id) }
                    }
                }
}
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun remaining(ms: Long): String {
    val mins = (ms / 60_000L).coerceAtLeast(0)
    return if (mins >= 60) "${mins / 60}h ${mins % 60}m left" else "${mins}m left"
}

@Composable
private fun RequestCard(
    request: CoolingOffRequest,
    now: Long,
    dateFormat: SimpleDateFormat,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val mature = now >= request.maturesAt
    val inDaytime = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in 8..19
    VowCard(modifier = Modifier.padding(vertical = 5.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Default.HourglassTop, vowWarm, size = 40.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        request.restrictionType.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (mature) "Ready since ${dateFormat.format(Date(request.maturesAt))}"
                        else "Ready ${dateFormat.format(Date(request.maturesAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!mature) Pill(remaining(request.maturesAt - now), vowWarm)
            }
            if (mature && !inDaytime) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Ready, but it can only be completed between 8 AM and 8 PM.",
                    style = MaterialTheme.typography.bodySmall, color = vowWarm
                )
            }
            Spacer(Modifier.height(12.dp))
            GradientButton("Keep it on", onClick = onCancel, modifier = Modifier.fillMaxWidth())
            if (mature && inDaytime) {
                QuietButton("Turn it off", onClick = onComplete, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ChangeCard(change: PendingChange, now: Long, dateFormat: SimpleDateFormat, onCancel: () -> Unit) {
    VowCard(modifier = Modifier.padding(vertical = 5.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Default.Tune, VowSky, size = 40.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(change.description, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Applies ${dateFormat.format(Date(change.maturesAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Pill(remaining(change.maturesAt - now), VowSky)
            }
            Spacer(Modifier.height(8.dp))
            QuietButton("Cancel this change", onClick = onCancel, color = VowTeal, modifier = Modifier.fillMaxWidth())
        }
    }
}
