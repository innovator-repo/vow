package com.vow.app.ui.screens.home

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vow.app.service.vpn.PrivateDns
import com.vow.app.ui.components.*
import com.vow.app.ui.theme.VowAmber
import com.vow.app.ui.theme.VowTeal

/**
 * Shown when Android's VPN permission prompt can't be opened — some phones ship without it or
 * have it disabled. Offers re-enabling that prompt, or Private DNS as a standalone alternative.
 */
@Composable
fun ContentFilterHelpDialog(onRetry: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var chosen by remember { mutableStateOf(PrivateDns.providers.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text("This phone can't show the VPN prompt") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Android's VPN permission prompt is missing or switched off, so Vow's own filter " +
                        "can't be turned on. Adding a VPN profile in Settings will not help — those are " +
                        "for company VPN servers.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))

                Text("Option 1 — re-enable the prompt", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Find the system app named VPN and enable it, then come back and try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    QuietButton("Open app info", color = VowTeal, onClick = { SystemScreens.openVpnDialogsAppInfo(context) })
                    QuietButton("Try again", color = VowAmber, onClick = { haptics.click(); onRetry() })
                }

                Spacer(Modifier.height(16.dp))
                Text("Option 2 — use Private DNS instead", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Android can filter adult sites by itself, with no VPN. Copy one of these hostnames " +
                        "into Settings → Private DNS. Vow then holds that setting in place: changing it " +
                        "back needs the usual cooling-off period.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                PrivateDns.providers.forEach { provider ->
                    VowCard(
                        modifier = Modifier.padding(vertical = 4.dp),
                        onClick = { chosen = provider },
                        containerColor = if (provider == chosen) VowTeal.copy(alpha = 0.10f)
                        else MaterialTheme.colorScheme.background
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = provider == chosen, onClick = { haptics.tick(); chosen = provider })
                            Column(Modifier.weight(1f)) {
                                Text(provider.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    provider.hostname,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = VowTeal
                                )
                                Text(
                                    provider.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row {
                    QuietButton("Copy hostname", color = VowTeal, onClick = {
                        haptics.confirm()
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("Private DNS", chosen.hostname))
                        Toast.makeText(context, "Copied — paste it into Private DNS", Toast.LENGTH_LONG).show()
                    })
                    QuietButton("Open Private DNS", color = VowTeal, onClick = { SystemScreens.openPrivateDns(context) })
                }
            }
        },
        icon = { IconBubble(Icons.Default.Dns, VowAmber, size = 44.dp) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
