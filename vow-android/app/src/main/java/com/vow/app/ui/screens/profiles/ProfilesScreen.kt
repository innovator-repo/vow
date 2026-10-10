package com.vow.app.ui.screens.profiles

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
import com.vow.app.data.model.Profile
import com.vow.app.data.model.ProfileType
import com.vow.app.data.repository.VowRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    repo: VowRepository,
    onEditProfile: (String) -> Unit,
    onBack: () -> Unit
) {
    val profiles by repo.prefs.getProfiles().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profiles") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditProfile("new") }) {
                Icon(Icons.Default.Add, "Add profile")
            }
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
                    "Profiles are focus modes you switch on yourself, like Work or Study. While one is on, " +
                        "its apps, keywords and filters are blocked on top of your other protections.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Default profiles if empty
            if (profiles.isEmpty()) {
                item {
                    Text(
                        "No profiles yet. Tap + to create one, or start with a template:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(
                    listOf(
                        Triple("Work", ProfileType.WORK, "Block social media and entertainment during work hours"),
                        Triple("Study", ProfileType.STUDY, "Block distractions while studying"),
                        Triple("Bedtime", ProfileType.BEDTIME, "Wind down for the night")
                    )
                ) { (name, type, desc) ->
                    OutlinedCard(
                        onClick = {
                            scope.launch {
                                val profile = Profile(
                                    name = name,
                                    type = type,
                                    blockShortVideos = true,
                                    blockAdultSites = true
                                )
                                repo.prefs.saveProfiles(profiles + profile)
                                onEditProfile(profile.id)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when (type) {
                                    ProfileType.WORK -> Icons.Default.Work
                                    ProfileType.STUDY -> Icons.Default.MenuBook
                                    ProfileType.BEDTIME -> Icons.Default.Bedtime
                                    else -> Icons.Default.Tune
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(name, style = MaterialTheme.typography.titleMedium)
                                Text(desc, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                items(profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        onEdit = { onEditProfile(profile.id) },
                        onToggle = { active ->
                            scope.launch {
                                val updated = profiles.map {
                                    if (it.id == profile.id) it.copy(isActive = active) else it
                                }
                                repo.prefs.saveProfiles(updated)
                            }
                        },
                        onDelete = {
                            scope.launch {
                                repo.prefs.saveProfiles(profiles.filter { it.id != profile.id })
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileCard(
    profile: Profile,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onEdit,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (profile.isActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (profile.type) {
                        ProfileType.WORK -> Icons.Default.Work
                        ProfileType.STUDY -> Icons.Default.MenuBook
                        ProfileType.BEDTIME -> Icons.Default.Bedtime
                        ProfileType.CUSTOM -> Icons.Default.Tune
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium)
                    val details = buildList {
                        if (profile.blockedApps.isNotEmpty()) add("${profile.blockedApps.size} apps blocked")
                        if (profile.blockShortVideos) add("No short videos")
                        if (profile.blockAdultSites) add("Adult filter")
                    }
                    if (details.isNotEmpty()) {
                        Text(
                            details.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                com.vow.app.ui.components.HapticSwitch(checked = profile.isActive, onCheckedChange = onToggle)
            }
        }
    }
}
