package com.vow.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vow.app.service.usage.UsageMonitorService
import com.vow.app.service.vpn.VowVpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Restart VPN service
            val vpnIntent = Intent(context, VowVpnService::class.java)
            context.startForegroundService(vpnIntent)

            // Restart usage monitor
            val usageIntent = Intent(context, UsageMonitorService::class.java)
            context.startForegroundService(usageIntent)
        }
    }
}
