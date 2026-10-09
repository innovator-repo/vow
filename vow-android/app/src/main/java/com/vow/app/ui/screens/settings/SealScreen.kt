package com.vow.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vow.app.data.model.SealState
import com.vow.app.data.repository.VowRepository
import com.vow.app.ui.theme.VowSeal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SealScreen(
    repo: VowRepository,
    restrictionId: String,
    onBack: () -> Unit
) {
    val restrictions by repo.prefs.getRestrictions().collectAsState(initial = emptyList())
    val restriction = restrictions.find { it.id == restrictionId }
    val scope = rememberCoroutineScope()
    var confirmText by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    val confirmSentence = "I seal this forever"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lifetime Seal") },
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = VowSeal
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (restriction == null) {
                Text("Restriction not found")
                return@Scaffold
            }

            when (restriction.sealState) {
                SealState.UNSEALED -> {
                    Text(
                        "Seal this protection forever",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Sealing makes this restriction permanent.\n\n" +
                            "After sealing, there will be no way to disable this " +
                            "protection from within Vow. The settings entry will " +
                            "simply show a sealed state with no controls.\n\n" +
                            "This is a two-step process:\n" +
                            "1. Request the seal now\n" +
                            "2. Confirm after 24 hours by typing a sentence\n\n" +
                            "This delay exists because your calm self should make " +
                            "this decision, not an impulse.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { showConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = VowSeal)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Begin sealing process")
                    }
                }

                SealState.PENDING_SEAL -> {
                    val elapsed = System.currentTimeMillis() - (restriction.sealRequestedAt ?: 0)
                    val hoursLeft = ((24 * 60 * 60 * 1000L - elapsed) / (60 * 60 * 1000L)).coerceAtLeast(0)

                    Text(
                        "Seal in progress",
                        style = MaterialTheme.typography.headlineMedium,
                        color = VowSeal
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (hoursLeft > 0) {
                        Text(
                            "You need to wait ${hoursLeft}h more before you can confirm.\n\n" +
                                "This wait ensures your decision is deliberate, " +
                                "not impulsive.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            "The waiting period is over.\n\n" +
                                "Type the sentence below exactly to confirm the seal:",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "\"$confirmSentence\"",
                            style = MaterialTheme.typography.titleLarge,
                            color = VowSeal
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = confirmText,
                            onValueChange = { confirmText = it },
                            label = { Text("Type the sentence") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    repo.confirmSeal(restrictionId)
                                    onBack()
                                }
                            },
                            enabled = confirmText.equals(confirmSentence, ignoreCase = true),
                            colors = ButtonDefaults.buttonColors(containerColor = VowSeal)
                        ) {
                            Text("Confirm seal")
                        }
                    }
                }

                SealState.SEALED -> {
                    Text(
                        "🔒 Sealed",
                        style = MaterialTheme.typography.headlineLarge,
                        color = VowSeal
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "This protection is permanent.\nThere is no undo.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Start sealing?") },
            text = {
                Text("After 24 hours you will be asked to type a confirmation sentence. " +
                    "Once confirmed, this protection becomes permanent and cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repo.requestSeal(restrictionId)
                            showConfirmDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VowSeal)
                ) {
                    Text("Start sealing")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
