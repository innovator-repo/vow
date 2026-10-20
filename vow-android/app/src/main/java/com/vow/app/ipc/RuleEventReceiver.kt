package com.vow.app.ipc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vow.app.data.prefs.VowPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Receives app.lume.action.RULE_EVENT; the manifest guards it with the ecosystem signature permission. */
class RuleEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LumeBus.ACTION_RULE_EVENT) return
        val value = RuleEventParser.encode(
            intent.getStringExtra("rule"), intent.getStringExtra("kind"), System.currentTimeMillis()
        ) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { VowPreferences(context).setLastRuleEvent(value) } finally { pending.finish() }
        }
    }
}
