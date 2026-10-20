package com.vow.app.ipc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vow.app.data.repository.VowRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Receives app.lume.action.SLEEP_LOCK_SYNC; guarded by the ecosystem signature permission in the manifest. */
class SleepSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LumeBus.ACTION_SLEEP_SYNC) return
        val x = intent.extras ?: return
        if (!x.containsKey("on")) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = VowRepository(context)
                val merged = SleepSync.merge(
                    repo.prefs.getNightConfig().first(), x.getBoolean("on"),
                    x.getInt("startHour", -1), x.getInt("startMinute", -1),
                    x.getInt("endHour", -1), x.getInt("endMinute", -1)
                ) ?: return@launch
                repo.applyPeerNightConfig(merged)
            } finally { pending.finish() }
        }
    }
}
