package com.vow.app.ipc

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.vow.app.data.model.ExceptionPolicy
import com.vow.app.data.model.ExceptionState
import com.vow.app.data.model.Restriction
import com.vow.app.data.model.RestrictionType
import com.vow.app.data.prefs.VowPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** content://com.vow.app.provider/status, read-only, one row. Guarded by the ecosystem signature permission. */
class VowStatusProvider : ContentProvider() {

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? {
        val ctx = context ?: return null
        val prefs = VowPreferences(ctx)
        val row = runBlocking {
            statusRow(
                prefs.getRestrictions().first(),
                prefs.getStats().first().totalReclaimedMinutes,
                System.currentTimeMillis()
            )
        }
        return MatrixCursor(COLUMNS).apply { addRow(row.toTypedArray()) }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.com.vow.app.status"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0

    companion object {
        val COLUMNS = arrayOf("reclaimedMinutes", "rulesKept", "rulesTotal", "sleepLock")

        /** A rule counts as kept while it is on, or while a planned exception covers today (no slip penalty). */
        fun statusRow(restrictions: List<Restriction>, reclaimedMinutes: Long, now: Long): List<Any> {
            val kept = restrictions.count {
                it.enabled || ExceptionPolicy.activeState(it, now) == ExceptionState.PLANNED
            }
            val sleep = restrictions.any { it.type == RestrictionType.NIGHT_MODE && it.enabled }
            return listOf(reclaimedMinutes.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), kept, restrictions.size, if (sleep) 1 else 0)
        }
    }
}
