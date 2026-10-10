package com.vow.app.ui.screens.profiles

import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vow.app.data.model.Profile
import com.vow.app.data.model.ProfileType
import com.vow.app.data.repository.VowRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    repo: VowRepository,
    profileId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val profiles by repo.prefs.getProfiles().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val existingProfile = profiles.find { it.id == profileId }

    var name by remember { mutableStateOf(existingProfile?.name ?: "") }
    var blockShortVideos by remember { mutableStateOf(existingProfile?.blockShortVideos ?: true) }
    var blockAdultSites by remember { mutableStateOf(existingProfile?.blockAdultSites ?: true) }
    var blockedApps by remember { mutableStateOf(existingProfile?.blockedApps ?: emptyList()) }
    var blockedKeywords by remember { mutableStateOf(existingProfile?.blockedKeywords ?: emptyList()) }
    var newKeyword by remember { mutableStateOf("") }

    val installedApps = remember {
        val pm = context.packageManager
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .filter { it.packageName != context.packageName }
            .map { it.packageName to pm.getApplicationLabel(it).toString() }
            .sortedBy { it.second }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingProfile != null) "Edit Profile" else "New Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        scope.launch {
                            val profile = (existingProfile ?: Profile(name = name)).copy(
                                name = name,
                                blockShortVideos = blockShortVideos,
                                blockAdultSites = blockAdultSites,
                                blockedApps = blockedApps,
                                blockedKeywords = blockedKeywords
                            )
                            val updated = if (existingProfile != null) {
                                profiles.map { if (it.id == profileId) profile else it }
                            } else {
                                profiles + profile
                            }
                            repo.prefs.saveProfiles(updated)
                            onBack()
                        }
                    }) {
                        Text("Save")
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profile name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Content Filters", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Block short videos", modifier = Modifier.weight(1f))
                com.vow.app.ui.components.HapticSwitch(checked = blockShortVideos, onCheckedChange = { blockShortVideos = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Block adult sites", modifier = Modifier.weight(1f))
                com.vow.app.ui.components.HapticSwitch(checked = blockAdultSites, onCheckedChange = { blockAdultSites = it })
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Blocked Keywords", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newKeyword,
                    onValueChange = { newKeyword = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Keyword") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        if (newKeyword.isNotBlank()) {
                            blockedKeywords = blockedKeywords + newKeyword.trim()
                            newKeyword = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, "Add")
                }
            }
            for (kw in blockedKeywords) {
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(kw, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { blockedKeywords = blockedKeywords - kw }) {
                        Icon(Icons.Default.Close, "Remove")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Blocked Apps", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            for ((pkg, label) in installedApps.take(50)) {
                val isBlocked = pkg in blockedApps
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Checkbox(
                        checked = isBlocked,
                        onCheckedChange = { checked ->
                            blockedApps = if (checked) blockedApps + pkg else blockedApps - pkg
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
