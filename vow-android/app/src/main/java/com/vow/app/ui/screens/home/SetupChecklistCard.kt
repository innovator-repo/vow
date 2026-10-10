package com.vow.app.ui.screens.home

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vow.app.service.accessibility.VowAccessibilityService
import com.vow.app.service.usage.UsageMonitorService
import com.vow.app.service.vpn.VowVpnService
import com.vow.app.ui.theme.VowGreen

@Composable
fun SetupChecklistCard() {
    val context = LocalContext.current
    var usage by remember { mutableStateOf(hasUsageAccess(context)) }
    var accessibility by remember { mutableStateOf(isAccessibilityEnabled(context)) }
    var vpn by remember { mutableStateOf(VpnService.prepare(context) == null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                usage = hasUsageAccess(context)
                accessibility = isAccessibilityEnabled(context)
                vpn = VpnService.prepare(context) == null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(usage) {
        if (usage) context.startForegroundService(Intent(context, UsageMonitorService::class.java))
    }
    LaunchedEffect(vpn) {
        if (vpn) context.startForegroundService(Intent(context, VowVpnService::class.java))
    }

    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vpn = VpnService.prepare(context) == null
    }

    if (usage && accessibility && vpn) return

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Finish setup", style = MaterialTheme.typography.titleMedium)
            Text(
                "Vow only applies to your personal profile. A work profile is never touched.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            ChecklistRow("Usage access", "Tracks time and tells personal apps apart from work apps", usage) {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            ChecklistRow("Accessibility", "Leaves short-video feeds and blocked pages", accessibility) {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            ChecklistRow("Content filter", "Blocks adult sites on this profile's traffic", vpn) {
                VpnService.prepare(context)?.let { vpnLauncher.launch(it) }
            }
        }
    }
}

@Composable
private fun ChecklistRow(title: String, subtitle: String, done: Boolean, onGrant: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (done) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Done", tint = VowGreen)
        } else {
            FilledTonalButton(onClick = onGrant) { Text("Allow") }
        }
    }
}

private fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(AppOpsManager::class.java)
    @Suppress("DEPRECATION")
    return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
        AppOpsManager.MODE_ALLOWED
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val ours = ComponentName(context, VowAccessibilityService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
}
