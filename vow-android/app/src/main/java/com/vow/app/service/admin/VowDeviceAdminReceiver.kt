package com.vow.app.service.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import com.vow.app.data.model.Restriction
import com.vow.app.data.model.RestrictionType

class VowDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, VowDeviceAdminReceiver::class.java)
        }

        fun isDeviceOwner(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isDeviceOwnerApp(context.packageName)
        }

        fun isDeviceAdmin(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isAdminActive(getComponentName(context))
        }

        /**
         * Mirrors Vow's restrictions into system policies when Vow is device owner. These grey out
         * the settings natively, which is stronger than the accessibility fallback.
         */
        fun syncDeviceOwnerPolicies(context: Context, restrictions: List<Restriction>) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (!dpm.isDeviceOwnerApp(context.packageName)) return
            val cn = getComponentName(context)
            fun on(t: RestrictionType) = restrictions.any { it.type == t && it.enabled }

            val uninstall = on(RestrictionType.UNINSTALL)
            dpm.setUninstallBlocked(cn, context.packageName, uninstall)
            setRestriction(dpm, cn, UserManager.DISALLOW_APPS_CONTROL, uninstall)
            if (Build.VERSION.SDK_INT >= 29) {
                setRestriction(dpm, cn, UserManager.DISALLOW_CONFIG_PRIVATE_DNS, on(RestrictionType.DNS))
            }
            val time = on(RestrictionType.DATE_TIME)
            if (Build.VERSION.SDK_INT >= 28) setRestriction(dpm, cn, UserManager.DISALLOW_CONFIG_DATE_TIME, time)
            @Suppress("DEPRECATION")
            dpm.setAutoTimeRequired(cn, time)
        }

        private fun setRestriction(dpm: DevicePolicyManager, cn: ComponentName, key: String, on: Boolean) {
            if (on) dpm.addUserRestriction(cn, key) else dpm.clearUserRestriction(cn, key)
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Turning this off removes Vow's uninstall protection. The commitments you made in Vow stay in place."
    }
}
