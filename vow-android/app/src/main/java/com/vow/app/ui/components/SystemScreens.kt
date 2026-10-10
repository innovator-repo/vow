package com.vow.app.ui.components

import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.vow.app.service.admin.VowDeviceAdminReceiver

/**
 * Opens Android's own settings screens. OEM builds rename, remove or lock these, so every
 * launch tries alternatives and ends with instructions rather than an exception.
 */
object SystemScreens {

    fun openUsageAccess(context: Context) = start(
        context,
        "Open Settings → Apps → Special access → Usage access, then allow Vow.",
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, appUri(context)),
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        appDetails(context)
    )

    fun openAccessibility(context: Context) = start(
        context,
        "Open Settings → Accessibility → Installed apps, then turn on Vow.",
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        appDetails(context)
    )

    /** Android's encrypted-DNS setting: the alternative when Vow's own filter can't be enabled. */
    fun openPrivateDns(context: Context) = start(
        context,
        "Open Settings → Connections → More connection settings → Private DNS.",
        Intent("android.settings.PRIVATE_DNS_SETTINGS"),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )

    /** The system app that shows the VPN permission prompt, so it can be re-enabled if disabled. */
    fun openVpnDialogsAppInfo(context: Context) = start(
        context,
        "Open Settings → Apps → ⋮ → Show system apps → VPN, then tap Enable.",
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", "com.android.vpndialogs", null)),
        Intent(Settings.ACTION_APPLICATION_SETTINGS)
    )

    fun addDeviceAdmin(context: Context, explanation: String) = start(
        context,
        "Open Settings → Security → Device admin apps, then turn on Vow.",
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, VowDeviceAdminReceiver.getComponentName(context))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, explanation),
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    )

    fun goHome(context: Context) {
        start(context, "Press the home button to leave this app.", Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
    }

    /** Launches the first intent that works; otherwise shows [guidance]. Returns true if one opened. */
    fun start(context: Context, guidance: String, vararg intents: Intent): Boolean {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Could not open ${intent.action ?: intent.component}", e)
            }
        }
        Toast.makeText(context, guidance, Toast.LENGTH_LONG).show()
        return false
    }

    /**
     * Runs [launch] for a result, falling back to [onUnavailable] when the target activity is
     * missing — Samsung and other OEM builds can ship without the VPN confirmation dialog.
     */
    fun launchForResult(intent: Intent, launch: (Intent) -> Unit, onUnavailable: () -> Unit) {
        try {
            launch(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No activity for ${intent.component ?: intent.action}", e)
            onUnavailable()
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to launch ${intent.component ?: intent.action}", e)
            onUnavailable()
        }
    }

    private fun appUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri(context))

    private const val TAG = "VowSystemScreens"
}
