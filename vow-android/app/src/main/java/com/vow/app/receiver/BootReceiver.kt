package com.vow.app.receiver

import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Process
import android.util.Log
import com.vow.app.service.usage.UsageMonitorService
import com.vow.app.service.vpn.VowVpnService

/** Restarts protection after a reboot or an app update, but only what the user has consented to. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        try {
            if (VpnService.prepare(context) == null) {
                context.startForegroundService(Intent(context, VowVpnService::class.java))
            }
            if (hasUsageAccess(context)) {
                context.startForegroundService(Intent(context, UsageMonitorService::class.java))
            }
        } catch (e: Exception) {
            Log.w("VowBoot", "Could not restart protection", e)
        }
    }

    private fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }
}
