package com.vow.app.ipc

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Ecosystem broadcasts. Every send is explicit (setPackage), guarded by the signature permission,
 * and silently does nothing when the peer app is not installed.
 */
object LumeBus {
    const val PERMISSION = "app.lume.permission.ECOSYSTEM_SYNC"

    const val KASANE = "app.kasane"
    const val REN = "app.ren.japanese"
    const val KATACHI = "app.katachi"
    const val DUOLINGO = "com.duolingo"

    const val ACTION_LOG_CHECK = "app.kasane.action.LOG_CHECK"
    const val ACTION_ECO_METRICS = "app.kasane.action.ECO_METRICS"
    const val ACTION_RULE_EVENT = "app.lume.action.RULE_EVENT"

    const val REN_QUICK_URI = "ren://session/quick?source=vow_redirect"
    const val KATACHI_NEXT_URI = "katachi://puzzle/next?source=vow_redirect"

    enum class RuleKind(val wire: String) { SLIP("slip"), KEPT("kept"), EXCEPTION("exception") }

    fun logCheckIntent(item: String, done: Boolean) = Intent(ACTION_LOG_CHECK)
        .setPackage(KASANE).putExtra("item", item).putExtra("done", done)

    fun ecoMetricsIntent(reclaimedMinutes: Long, youtubeUsedMinutes: Int, shortVideoBlockAttempts: Int) =
        Intent(ACTION_ECO_METRICS).setPackage(KASANE).putExtra("source", "vow")
            .putExtra("payload", metricsPayload(reclaimedMinutes, youtubeUsedMinutes, shortVideoBlockAttempts))

    fun ruleEventIntent(rule: String, kind: RuleKind) = Intent(ACTION_RULE_EVENT)
        .setPackage(KASANE).putExtra("rule", rule).putExtra("kind", kind.wire)

    /** Hand-built so it is identical on device and in unit tests (all values are numbers). */
    fun metricsPayload(reclaimedMinutes: Long, youtubeUsedMinutes: Int, shortVideoBlockAttempts: Int) =
        "{\"reclaimedMinutes\":$reclaimedMinutes,\"youtubeUsedMinutes\":$youtubeUsedMinutes," +
            "\"shortVideoBlockAttempts\":$shortVideoBlockAttempts}"

    fun sendLogCheck(context: Context, item: String, done: Boolean) =
        send(context, KASANE, logCheckIntent(item, done))

    fun sendEcoMetrics(context: Context, reclaimedMinutes: Long, youtubeUsedMinutes: Int, shortVideoBlockAttempts: Int) =
        send(context, KASANE, ecoMetricsIntent(reclaimedMinutes, youtubeUsedMinutes, shortVideoBlockAttempts))

    fun sendRuleEvent(context: Context, rule: String, kind: RuleKind) =
        send(context, KASANE, ruleEventIntent(rule, kind))

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false } catch (_: Exception) { false }

    private fun send(context: Context, pkg: String, intent: Intent) {
        if (!isInstalled(context, pkg)) return
        runCatching { context.sendBroadcast(intent, PERMISSION) }
    }
}

/** Duolingo counts as studied after 150s in the foreground, once per day. */
object DuolingoRule {
    const val THRESHOLD_MS = 150_000L
    fun shouldLog(foregroundMs: Long, loggedDay: String, today: String) =
        foregroundMs >= THRESHOLD_MS && loggedDay != today
}

/** Inbound RULE_EVENT (e.g. from Kasane). Recorded only; it never loosens or tightens a restriction. */
object RuleEventParser {
    private val KINDS = setOf("slip", "kept", "exception")

    /** "rule|kind|millis", or null when the extras are missing or not one of the known kinds. */
    fun encode(rule: String?, kind: String?, now: Long): String? {
        if (rule.isNullOrBlank() || rule.length > 64 || '|' in rule || kind !in KINDS) return null
        return "$rule|$kind|$now"
    }
}
