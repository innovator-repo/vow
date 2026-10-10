package com.vow.app.ui.screens.home

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import com.vow.app.service.admin.VowDeviceAdminReceiver
import com.vow.app.service.vpn.PrivateDns
import com.vow.app.ui.components.QuietButton
import com.vow.app.ui.components.SystemScreens
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
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
    var vpnConsented by remember { mutableStateOf(runCatching { VpnService.prepare(context) }.getOrNull() == null) }
    var privateDns by remember { mutableStateOf(PrivateDns.isSet(context)) }
    val repo = remember { com.vow.app.data.repository.VowRepository(context) }
    LaunchedEffect(privateDns) {
        if (privateDns) PrivateDns.hostname(context)?.let { repo.lockPrivateDns(it) }
    }
    var filterHelp by remember { mutableStateOf(false) }
    val vpn = vpnConsented || privateDns
    var admin by remember { mutableStateOf(isAdminActive(context)) }
    val prefs = remember { com.vow.app.data.prefs.VowPreferences(context) }
    val restrictions by prefs.getRestrictions().collectAsState(initial = emptyList())
    val wantsAdmin = restrictions.any { it.type == com.vow.app.data.model.RestrictionType.UNINSTALL && it.enabled }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                usage = hasUsageAccess(context)
                accessibility = isAccessibilityEnabled(context)
                vpnConsented = runCatching { VpnService.prepare(context) }.getOrNull() == null
                privateDns = PrivateDns.isSet(context)
                admin = isAdminActive(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(usage) {
        if (usage) runCatching { context.startForegroundService(Intent(context, UsageMonitorService::class.java)) }
    }
    LaunchedEffect(vpnConsented) {
        if (vpnConsented) runCatching { context.startForegroundService(Intent(context, VowVpnService::class.java)) }
    }

    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vpnConsented = runCatching { VpnService.prepare(context) }.getOrNull() == null
    }

    var adminFailed by remember { mutableStateOf(false) }
    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        admin = isAdminActive(context)
        adminFailed = !admin
    }

    fun requestAdmin() {
        val intent = SystemScreens.deviceAdminIntent(context, Disclosure.ADMIN.body)
        if (!SystemScreens.canResolve(context, intent)) {
            adminFailed = true
            SystemScreens.openDeviceAdminSettings(context)
            return
        }
        SystemScreens.launchForResult(
            intent,
            launch = { adminLauncher.launch(it) },
            onUnavailable = {
                adminFailed = true
                SystemScreens.openDeviceAdminSettings(context)
            }
        )
    }

    fun requestFilter() {
        val consent = runCatching { VpnService.prepare(context) }.getOrNull()
        if (consent == null) {
            vpnConsented = true
            return
        }
        SystemScreens.launchForResult(
            consent,
            launch = { vpnLauncher.launch(it) },
            onUnavailable = { filterHelp = true }
        )
    }

    if (filterHelp) {
        ContentFilterHelpDialog(
            onRetry = { filterHelp = false; requestFilter() },
            onDismiss = {
                filterHelp = false
                privateDns = PrivateDns.isSet(context)
            }
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    var disclosure by remember { mutableStateOf<Disclosure?>(null) }
    disclosure?.let { d ->
        DisclosureDialog(d, onDismiss = { disclosure = null }, onAgree = {
            disclosure = null
            when (d) {
                Disclosure.USAGE -> SystemScreens.openUsageAccess(context)
                Disclosure.ACCESSIBILITY -> SystemScreens.openAccessibility(context)
                Disclosure.VPN -> requestFilter()
                Disclosure.ADMIN -> requestAdmin()
            }
        })
    }

    if (usage && accessibility && vpn && (admin || !wantsAdmin)) return

    com.vow.app.ui.components.VowCard(
        modifier = Modifier.padding(bottom = 16.dp),
        containerColor = com.vow.app.ui.theme.VowAmber.copy(alpha = 0.08f),
        borderBrush = androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(com.vow.app.ui.theme.VowAmber.copy(alpha = 0.45f), com.vow.app.ui.theme.VowAmber.copy(alpha = 0.08f))
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Finish setup", style = MaterialTheme.typography.titleMedium)
            Text(
                "Vow only applies to your personal profile. A work profile is never touched.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            ChecklistRow("Usage access", "Tracks time and tells personal apps apart from work apps", usage) {
                disclosure = Disclosure.USAGE
            }
            ChecklistRow("Accessibility", "Blocks apps past their limit, Night Mode, Shorts and Reels", accessibility) {
                disclosure = Disclosure.ACCESSIBILITY
            }
            ChecklistRow(
                "Content filter",
                if (privateDns) "Private DNS is set — Vow keeps it from being changed"
                else "Blocks adult sites on this profile's traffic",
                vpn
            ) { disclosure = Disclosure.VPN }
            if (wantsAdmin) {
                ChecklistRow("Uninstall protection", "Stops Vow being removed on impulse", admin) {
                    disclosure = Disclosure.ADMIN
                }
                if (adminFailed && !admin) {
                    Text(
                        "This phone didn't open the device admin screen. Vow still blocks its own " +
                            "uninstall page while Accessibility is on. To add the system-level lock, open " +
                            "Settings → Security → Other security settings → Device admin apps and turn on Vow.",
                        style = MaterialTheme.typography.bodySmall,
                        color = com.vow.app.ui.theme.VowAmber,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    QuietButton(
                        "Open device admin settings",
                        onClick = { SystemScreens.openDeviceAdminSettings(context) },
                        color = com.vow.app.ui.theme.VowAmber
                    )
                }
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
            val haptics = com.vow.app.ui.components.rememberHaptics()
            FilledTonalButton(onClick = { haptics.click(); onGrant() }) { Text("Allow") }
        }
    }
}

private fun isAdminActive(context: Context): Boolean =
    context.getSystemService(DevicePolicyManager::class.java)
        .isAdminActive(VowDeviceAdminReceiver.getComponentName(context))

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

private enum class Disclosure(val title: String, val body: String, val step: String) {
    USAGE(
        "Allow usage access",
        "Vow reads which apps you open and for how long, so it can enforce the daily limits and " +
            "YouTube allowance you set, and so it can tell your personal apps apart from a work profile.\n\n" +
            "This data stays on your phone. It is never uploaded, shared or sold.",
        "On the next screen, find Vow and turn on \u201CPermit usage access\u201D."
    ),
    ACCESSIBILITY(
        "Allow accessibility",
        "Vow uses the Accessibility API to see which app is on screen and, inside apps like YouTube, " +
            "Instagram and your browser, whether you are in Shorts, Reels or on a page with a blocked word. " +
            "It then closes that screen or shows a block screen.\n\n" +
            "It only acts on apps in your personal profile. It does not read messages, passwords or " +
            "typed text, and nothing it sees leaves your phone.",
        "On the next screen, open Vow and turn it on. If it is greyed out, go to Settings \u2192 Apps \u2192 Vow \u2192 \u22EE \u2192 Allow restricted settings."
    ),
    VPN(
        "Turn on the content filter",
        "Vow creates a local VPN that only looks at the names of websites your phone looks up, " +
            "and answers \u201Cnot found\u201D for adult sites and your blocked keywords.\n\n" +
            "It runs entirely on your phone. Your browsing is not sent to Vow or anyone else, and " +
            "work-profile traffic is not affected.",
        "Android will ask you to confirm the connection request."
    ),
    ADMIN(
        "Turn on uninstall protection",
        "Making Vow a device admin means it can't be uninstalled until you switch this off again.\n\n" +
            "Android makes every device admin declare a permission, so Vow declares the mildest one " +
            "(locking the screen) and never uses it. It cannot wipe your phone or read anything on it.",
        "On the next screen, tap Activate."
    )
}

@Composable
private fun DisclosureDialog(d: Disclosure, onDismiss: () -> Unit, onAgree: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text(d.title) },
        text = {
            Column {
                Text(d.body, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(d.step, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onAgree) { Text("Agree and continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}
