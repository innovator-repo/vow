package com.vow.app.service.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager

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
         * Apply Device Owner restrictions. These grey out settings in the system UI
         * rather than blocking with a hostile overlay.
         */
        fun applyDeviceOwnerRestrictions(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val cn = getComponentName(context)

            if (!dpm.isDeviceOwnerApp(context.packageName)) return

            // Block uninstall of this app
            dpm.setUninstallBlocked(cn, context.packageName, true)

            // Prevent changing private DNS
            dpm.addUserRestriction(cn, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)

            // Prevent changing date/time
            dpm.addUserRestriction(cn, UserManager.DISALLOW_CONFIG_DATE_TIME)
            dpm.setAutoTimeRequired(cn, true)

            // Prevent accessing app management to force-stop/disable
            dpm.addUserRestriction(cn, UserManager.DISALLOW_APPS_CONTROL)
        }

        fun removeDeviceOwnerRestrictions(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val cn = getComponentName(context)

            if (!dpm.isDeviceOwnerApp(context.packageName)) return

            dpm.setUninstallBlocked(cn, context.packageName, false)
            dpm.clearUserRestriction(cn, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
            dpm.clearUserRestriction(cn, UserManager.DISALLOW_CONFIG_DATE_TIME)
            dpm.setAutoTimeRequired(cn, false)
            dpm.clearUserRestriction(cn, UserManager.DISALLOW_APPS_CONTROL)
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling Vow's device admin will remove uninstall protection. This action requires your Vow password."
    }
}
