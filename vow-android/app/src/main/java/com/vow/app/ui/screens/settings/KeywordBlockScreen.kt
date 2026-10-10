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
import com.vow.app.data.model.BlockedKeyword
import com.vow.app.data.repository.VowRepository
import com.vow.app.ui.theme.VowSeal
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordBlockScreen(
    repo: VowRepository,
    onBack: () -> Unit
) {
    val keywords by repo.prefs.getBlockedKeywords().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var newKeyword by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blocked Keywords") },
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
        ) {
            Text(
                "Websites containing these keywords in their URL will be blocked.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Add keyword
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newKeyword,
                    onValueChange = { newKeyword = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("New keyword") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        if (newKeyword.isNotBlank()) {
                            scope.launch {
                                val updated = keywords + BlockedKeyword(keyword = newKeyword.trim())
                                repo.prefs.saveBlockedKeywords(updated)
                                newKeyword = ""
                            }
                        }
                    },
                    enabled = newKeyword.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, "Add")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                items(keywords, key = { it.id }) { keyword ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                keyword.keyword,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )

                            if (keyword.sealed) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Sealed",
                                    tint = VowSeal,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                com.vow.app.ui.components.HapticSwitch(
                                    checked = keyword.enabled,
                                    onCheckedChange = { enabled ->
                                        scope.launch {
                                            val updated = keywords.map {
                                                if (it.id == keyword.id) it.copy(enabled = enabled) else it
                                            }
                                            repo.prefs.saveBlockedKeywords(updated)
                                        }
                                    }
                                )
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            repo.prefs.saveBlockedKeywords(keywords.filter { it.id != keyword.id })
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
