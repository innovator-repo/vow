package com.vow.app.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.vow.app.data.model.ExceptionPolicy
import com.vow.app.data.model.RestrictionType
import com.vow.app.data.prefs.VowPreferences
import com.vow.app.data.repository.VowRepository
import com.vow.app.service.admin.VowDeviceAdminReceiver
import com.vow.app.service.blocking.BlockPolicy
import com.vow.app.ui.block.BlockActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class VowAccessibilityService : AccessibilityService() {

    /** Copied out synchronously: before Android 13 the event object is recycled once the callback returns. */
    private data class Ev(val type: Int, val pkg: String, val cls: String?)

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var prefs: VowPreferences
    private lateinit var profileGuard: ProfileGuard
    private lateinit var blockPolicy: BlockPolicy
    @Volatile private var currentApp: String? = null
    private var lastBlock: Pair<String, Long>? = null

    private val shortVideoApps = mapOf(
        "com.google.android.youtube" to listOf("Shorts", "shorts_pivot_item"),
        "com.instagram.android" to listOf("Reels", "reels_tray"),
        "com.facebook.katana" to listOf("Reels"),
        "com.snapchat.android" to listOf("Spotlight")
    )

    private val browserPackages = setOf(
        "com.android.chrome", "org.mozilla.firefox", "com.opera.browser",
        "com.brave.browser", "com.microsoft.emmx", "com.sec.android.app.sbrowser"
    )

    private val settingsPackage = "com.android.settings"
    private val installerPackages = setOf(
        "com.google.android.packageinstaller", "com.android.packageinstaller",
        "com.samsung.android.packageinstaller"
    )

    override fun onCreate() {
        super.onCreate()
        prefs = VowPreferences(this)
        profileGuard = ProfileGuard(this)
        blockPolicy = BlockPolicy(this, VowRepository(this))
    }

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 300
        }

        scope.launch {
            while (isActive) {
                delay(20_000)
                currentApp?.let { pkg -> runCatching { enforceAppRules(pkg) } }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val ev = Ev(event.eventType, event.packageName?.toString() ?: return, event.className?.toString())
        scope.launch { runCatching { handle(ev) } }
    }

    private suspend fun handle(ev: Ev) {
        if (ev.type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            ev.pkg != packageName && ev.pkg != "com.android.systemui"
        ) {
            currentApp = ev.pkg
            enforceAppRules(ev.pkg)
        }

        val relevant = ev.pkg in shortVideoApps || ev.pkg in browserPackages ||
            ev.pkg == settingsPackage || ev.pkg in installerPackages
        if (!relevant) return
        if (ev.pkg !in installerPackages && !profileGuard.isPersonalForeground(ev.pkg)) return

        handleShortVideoBlocking(ev)
        handleSelfProtection(ev)
        handleBrowserKeywordBlocking(ev)
    }

    private suspend fun enforceAppRules(packageName: String) {
        if (!profileGuard.isPersonalForeground(packageName)) return
        val reason = withContext(Dispatchers.Default) { blockPolicy.check(packageName) } ?: return
        val now = System.currentTimeMillis()
        val last = lastBlock
        if (last != null && last.first == packageName && now - last.second < 1500) return
        lastBlock = packageName to now
        performGlobalAction(GLOBAL_ACTION_HOME)
        BlockActivity.show(this, reason.title, reason.message, reason.type)
    }

    private var lastCountedBlock = 0L

    /** One attempt per second at most: BACK can fire on several events for the same swipe. */
    private suspend fun countShortVideoBlock() {
        val now = System.currentTimeMillis()
        if (now - lastCountedBlock < 1000) return
        lastCountedBlock = now
        prefs.updateEcoCounters(ExceptionPolicy.dayKey(now)) { it.copy(shortVideoBlocks = it.shortVideoBlocks + 1) }
    }

    private suspend fun shortVideoBlocked(): Boolean =
        prefs.getRestrictions().first().any { it.type == RestrictionType.SHORT_VIDEO && it.enabled } ||
            prefs.getProfiles().first().any { it.isActive && it.blockShortVideos }

    private suspend fun handleShortVideoBlocking(ev: Ev) {
        val indicators = shortVideoApps[ev.pkg] ?: return
        if (ev.type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && ev.type != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        if (!shortVideoBlocked()) return
        val root = rootInActiveWindow ?: return
        if (isInShortVideoSection(root, ev.pkg, indicators)) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            countShortVideoBlock()
        }
    }

    private fun isInShortVideoSection(root: AccessibilityNodeInfo, pkg: String, indicators: List<String>): Boolean {
        for (indicator in indicators) {
            if (root.findAccessibilityNodeInfosByViewId("$pkg:id/$indicator").isNotEmpty()) return true
            if (root.findAccessibilityNodeInfosByText(indicator).any { it.isSelected }) return true
        }
        return false
    }

    /**
     * Keeps protections from being switched off around Vow: its app-info / uninstall / device-admin
     * pages, its accessibility switch and its VPN page while uninstall protection is on, plus the
     * Private DNS and date/time settings when those locks are on. Only screens about Vow are touched.
     */
    private suspend fun handleSelfProtection(ev: Ev) {
        if (ev.type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (ev.pkg != settingsPackage && ev.pkg !in installerPackages) return
        if (prefs.isDeviceOwner.first()) return

        val restrictions = prefs.getRestrictions().first()
        fun on(t: RestrictionType) = restrictions.any { it.type == t && it.enabled }
        val protectUninstall = on(RestrictionType.UNINSTALL)
        val protectDns = on(RestrictionType.DNS)
        val protectTime = on(RestrictionType.DATE_TIME)
        if (!protectUninstall && !protectDns && !protectTime) return

        val root = rootInActiveWindow ?: return
        val cls = ev.cls.orEmpty()
        fun has(text: String) = root.findAccessibilityNodeInfosByText(text).isNotEmpty()
        val label = getString(com.vow.app.R.string.app_name)
        val aboutVow = root.findAccessibilityNodeInfosByText(label).any { it.text?.toString() == label }

        val block = when {
            protectUninstall && ev.pkg in installerPackages -> aboutVow
            protectUninstall && aboutVow && cls.contains("DeviceAdminAdd") -> isAdminActive()
            protectUninstall && aboutVow && (cls.contains("InstalledApp") || cls.contains("AppInfo") ||
                has("Force stop") || has("Uninstall")) -> true
            protectUninstall && has("Use $label") -> true
            protectUninstall && aboutVow && (has("Always-on VPN") || has("Forget VPN")) -> true
            protectDns && isPrivateDnsScreen(cls, root) -> true
            protectTime && (cls.contains("DateTime") || has("Set time automatically")) -> true
            else -> false
        }
        if (block) performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /** The Private DNS editor only; the surrounding settings list stays usable. */
    private fun isPrivateDnsScreen(className: String, root: AccessibilityNodeInfo): Boolean {
        if (className.contains("PrivateDns", ignoreCase = true)) return true
        return PRIVATE_DNS_OPTIONS.any { root.findAccessibilityNodeInfosByText(it).isNotEmpty() }
    }

    private fun isAdminActive(): Boolean =
        getSystemService(DevicePolicyManager::class.java)
            .isAdminActive(VowDeviceAdminReceiver.getComponentName(this))

    private suspend fun handleBrowserKeywordBlocking(ev: Ev) {
        if (ev.pkg !in browserPackages) return
        val restrictionOn = prefs.getRestrictions().first().any { it.type == RestrictionType.KEYWORD_BLOCK && it.enabled }
        val keywords = buildList {
            if (restrictionOn) addAll(prefs.getBlockedKeywords().first().filter { it.enabled }.map { it.keyword })
            prefs.getProfiles().first().filter { it.isActive }.forEach { addAll(it.blockedKeywords) }
        }.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        if (keywords.isEmpty()) return

        val root = rootInActiveWindow ?: return
        val urlBarIds = listOf("url_bar", "url_field", "search", "mozac_browser_toolbar_url_view", "location_bar_edit_text")
        for (id in urlBarIds) {
            for (node in root.findAccessibilityNodeInfosByViewId("${ev.pkg}:id/$id")) {
                if (node.isFocused) continue // don't fight the user while they're typing
                val text = node.text?.toString()?.lowercase() ?: continue
                if (keywords.any { text.contains(it) }) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    return
                }
            }
        }
    }

    private companion object {
        /** Options shown inside the Private DNS chooser on stock and Samsung builds. */
        val PRIVATE_DNS_OPTIONS = listOf(
            "Private DNS provider hostname",
            "Enter hostname of DNS provider",
            "Designated private DNS"
        )
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
