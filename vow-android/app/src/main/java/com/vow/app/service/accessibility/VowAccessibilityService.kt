package com.vow.app.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.vow.app.data.model.RestrictionType
import com.vow.app.data.prefs.VowPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class VowAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var prefs: VowPreferences
    private lateinit var profileGuard: ProfileGuard

    // Package names for short video detection
    private val shortVideoApps = mapOf(
        "com.google.android.youtube" to listOf("Shorts", "shorts_pivot_item"),
        "com.zhiliaoapp.musically" to emptyList(), // TikTok - block feed entirely is optional
        "com.instagram.android" to listOf("Reels", "reels_tray"),
        "com.facebook.katana" to listOf("Reels"),
        "com.facebook.orca" to listOf("Reels"),
        "com.snapchat.android" to listOf("Spotlight")
    )

    private val browserPackages = setOf(
        "com.android.chrome", "org.mozilla.firefox", "com.opera.browser",
        "com.brave.browser", "com.microsoft.emmx", "com.sec.android.app.sbrowser"
    )

    private val settingsPackage = "com.android.settings"

    // Settings pages to redirect away from (fallback mode without Device Owner)
    private val protectedSettingsActivities = setOf(
        "com.android.settings/.DeviceAdminAdd",
        "com.android.settings/.applications.InstalledAppDetailsTop",
        "com.android.settings/.network.PrivateDnsModeDialogPreference",
        "com.android.settings/.datetime.DateTimeSettings"
    )

    override fun onCreate() {
        super.onCreate()
        prefs = VowPreferences(this)
        profileGuard = ProfileGuard(this)
    }

    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 300
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        scope.launch {
            try {
                val packageName = event.packageName?.toString() ?: return@launch
                if (packageName !in shortVideoApps && packageName !in browserPackages &&
                    packageName != settingsPackage) return@launch
                if (!profileGuard.isPersonalForeground(packageName)) return@launch

                // Check short video blocking
                handleShortVideoBlocking(event, packageName)

                // Settings protection (fallback when not Device Owner)
                handleSettingsProtection(event, packageName)

                // URL bar keyword checking in browsers
                handleBrowserKeywordBlocking(event, packageName)
            } catch (_: Exception) {}
        }
    }

    private suspend fun handleShortVideoBlocking(event: AccessibilityEvent, packageName: String) {
        val restrictions = prefs.getRestrictions().first()
        val shortVideoBlocked = restrictions.any {
            it.type == RestrictionType.SHORT_VIDEO && it.enabled
        }
        if (!shortVideoBlocked) return

        val shortVideoConfig = shortVideoApps[packageName] ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {

            val rootNode = rootInActiveWindow ?: return

            // Check if we're in a short video section
            if (isInShortVideoSection(rootNode, shortVideoConfig)) {
                // Press back to exit the short video feed
                performGlobalAction(GLOBAL_ACTION_BACK)
            }

            rootNode.recycle()
        }
    }

    private fun isInShortVideoSection(root: AccessibilityNodeInfo, indicators: List<String>): Boolean {
        if (indicators.isEmpty()) return false

        for (indicator in indicators) {
            // Check view IDs
            val nodes = root.findAccessibilityNodeInfosByViewId(indicator)
            if (nodes.isNotEmpty()) {
                nodes.forEach { it.recycle() }
                return true
            }

            // Check text content
            val textNodes = root.findAccessibilityNodeInfosByText(indicator)
            for (node in textNodes) {
                // Check if this is a tab/pivot that is selected
                if (node.isSelected || node.isFocused) {
                    textNodes.forEach { it.recycle() }
                    return true
                }
            }
            textNodes.forEach { it.recycle() }
        }
        return false
    }

    private suspend fun handleSettingsProtection(event: AccessibilityEvent, packageName: String) {
        if (packageName != settingsPackage) return

        val isDeviceOwner = prefs.isDeviceOwner.first()
        if (isDeviceOwner) return // Device Owner handles this natively

        val restrictions = prefs.getRestrictions().first()
        val protectUninstall = restrictions.any { it.type == RestrictionType.UNINSTALL && it.enabled }
        val protectDns = restrictions.any { it.type == RestrictionType.DNS && it.enabled }
        val protectDateTime = restrictions.any { it.type == RestrictionType.DATE_TIME && it.enabled }

        if (!protectUninstall && !protectDns && !protectDateTime) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val className = event.className?.toString() ?: return

            // Check if user is in a protected settings area
            val shouldRedirect = when {
                protectUninstall && (className.contains("InstalledApp") ||
                        className.contains("UninstallConfirm") ||
                        className.contains("DeviceAdminAdd")) -> isAboutVow()
                protectDns && className.contains("PrivateDns") -> true
                protectDateTime && className.contains("DateTime") -> true
                else -> false
            }

            if (shouldRedirect) {
                // Gently redirect home instead of blocking with an overlay
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(homeIntent)
            }
        }
    }

    private suspend fun handleBrowserKeywordBlocking(event: AccessibilityEvent, packageName: String) {
        val restrictions = prefs.getRestrictions().first()
        val keywordBlocked = restrictions.any { it.type == RestrictionType.KEYWORD_BLOCK && it.enabled }
        if (!keywordBlocked) return

        if (packageName !in browserPackages) return

        val root = rootInActiveWindow ?: return
        val keywords = prefs.getBlockedKeywords().first().filter { it.enabled }.map { it.keyword.lowercase() }

        // Find URL bar
        val urlBarIds = listOf(
            "$packageName:id/url_bar",
            "$packageName:id/url_field",
            "$packageName:id/search",
            "$packageName:id/mozac_browser_toolbar_url_view"
        )

        for (id in urlBarIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                val text = node.text?.toString()?.lowercase() ?: continue
                for (kw in keywords) {
                    if (text.contains(kw)) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        nodes.forEach { it.recycle() }
                        root.recycle()
                        return
                    }
                }
            }
            nodes.forEach { it.recycle() }
        }
        root.recycle()
    }

    // Only guard Vow's own app-info / admin pages, never other apps' (including work apps).
    private fun isAboutVow(): Boolean {
        val root = rootInActiveWindow ?: return false
        val label = getString(com.vow.app.R.string.app_name)
        val hit = root.findAccessibilityNodeInfosByText(label).any { it.text?.toString() == label }
        root.recycle()
        return hit
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
