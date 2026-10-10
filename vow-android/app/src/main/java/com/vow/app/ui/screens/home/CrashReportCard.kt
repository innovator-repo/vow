package com.vow.app.ui.screens.home

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vow.app.VowApplication
import com.vow.app.ui.components.IconBubble
import com.vow.app.ui.components.QuietButton
import com.vow.app.ui.components.VowCard
import com.vow.app.ui.components.rememberHaptics
import com.vow.app.ui.theme.VowRose

/** Shown after Vow closed unexpectedly, so the error can be copied and shared. */
@Composable
fun CrashReportCard() {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val file = remember { VowApplication.crashFile(context) }
    var report by remember { mutableStateOf(if (file.exists()) file.readText() else null) }
    val text = report ?: return

    VowCard(
        modifier = Modifier.padding(bottom = 16.dp),
        containerColor = VowRose.copy(alpha = 0.08f),
        borderBrush = Brush.linearGradient(listOf(VowRose.copy(alpha = 0.45f), VowRose.copy(alpha = 0.08f)))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Default.BugReport, VowRose, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Vow closed unexpectedly", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text.lineSequence().drop(3).firstOrNull().orEmpty().take(90),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                QuietButton("Copy error", color = VowRose, onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("Vow crash", text))
                    Toast.makeText(context, "Copied — paste it to share", Toast.LENGTH_SHORT).show()
                })
                QuietButton("Dismiss", onClick = {
                    haptics.tick()
                    file.delete()
                    report = null
                })
            }
        }
    }
}
