package com.vow.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vow.app.data.model.CoolingOffRequest
import com.vow.app.data.repository.VowRepository
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
    val scope = rememberCoroutineScope()
    val pending = requests.filter { !it.completed && !it.cancelled }
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cooling-Off Queue") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (pending.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No pending loosening requests")
                    Text(
                        "All your protections are in place.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                item {
                    Text(
                        "Loosening requests mature after the cooling-off period " +
                            "and can only be completed between 8 AM and 8 PM.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(pending, key = { it.id }) { request ->
                    CoolingOffCard(
                        request = request,
                        dateFormat = dateFormat,
                        onComplete = {
                            scope.launch {
                                repo.completeLoosening(request.id)
                            }
                        },
                        onCancel = {
                            scope.launch {
                                repo.cancelLoosening(request.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CoolingOffCard(
    request: CoolingOffRequest,
    dateFormat: SimpleDateFormat,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val now = System.currentTimeMillis()
    val mature = now >= request.maturesAt
    val hoursLeft = ((request.maturesAt - now) / (60 * 60 * 1000L)).coerceAtLeast(0)

    val cal = Calendar.getInstance()
    val currentHour = cal.get(Calendar.HOUR_OF_DAY)
    val inDaytime = currentHour in 8..19

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                request.restrictionType.name.replace("_", " "),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Requested: ${dateFormat.format(Date(request.requestedAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Matures: ${dateFormat.format(Date(request.maturesAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!mature) {
                Text(
                    "⏳ ${hoursLeft}h remaining",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            } else if (!inDaytime) {
                Text(
                    "Ready, but can only be completed between 8 AM - 8 PM",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mature && inDaytime) {
                    Button(onClick = onComplete) {
                        Text("Complete loosening")
                    }
                }
                OutlinedButton(onClick = onCancel) {
                    Text("Cancel request")
                }
            }
        }
    }
}
