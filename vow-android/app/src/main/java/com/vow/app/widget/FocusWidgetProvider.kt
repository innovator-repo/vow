package com.vow.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.vow.app.R
import com.vow.app.focus.FocusActivity
import com.vow.app.focus.FocusLogic
import com.vow.app.focus.FocusSnapshot
import com.vow.app.focus.FocusSnapshotLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Focus switch widget: on/off for the active profile and the time reclaimed today.
 * A tap sends vow://focus/toggle. That turns focus on without unlocking; turning it off opens the
 * app, so the app lock and its rules still apply. Works with no data (shows a neutral state).
 */
class FocusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                push(context, manager, ids, FocusSnapshotLoader.load(context))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, FocusWidgetProvider::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch { push(app, manager, ids, FocusSnapshotLoader.load(app)) }
        }

        private fun push(context: Context, manager: AppWidgetManager, ids: IntArray, snap: FocusSnapshot) {
            val views = build(context, snap)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        fun toggleIntent(context: Context): PendingIntent {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vow://focus/toggle"))
                .setClass(context, FocusActivity::class.java)
            return PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun build(context: Context, snap: FocusSnapshot): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_focus)
            val name = snap.profileName.ifBlank { "Focus" }
            views.setTextViewText(R.id.vow_widget_profile, name)
            views.setTextViewText(
                R.id.vow_widget_reclaimed,
                FocusLogic.formatDuration(snap.reclaimedMinutes) + if (snap.isToday) " reclaimed today" else " reclaimed"
            )
            views.setTextViewText(R.id.vow_widget_hint, if (snap.focusOn) "On · tap to open controls" else "Off · tap to start")
            views.setImageViewResource(R.id.vow_widget_switch, if (snap.focusOn) R.drawable.vow_switch_on else R.drawable.vow_switch_off)
            views.setOnClickPendingIntent(R.id.vow_widget_root, toggleIntent(context))
            return views
        }
    }
}
