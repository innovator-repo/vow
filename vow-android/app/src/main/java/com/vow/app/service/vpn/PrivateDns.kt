package com.vow.app.service.vpn

import android.content.Context
import android.provider.Settings

/**
 * Android's own encrypted-DNS setting. A filtering provider set here blocks adult sites
 * system-wide without Vow's VPN, which matters on phones whose VPN consent dialog is missing.
 */
object PrivateDns {

    data class Provider(val name: String, val hostname: String, val detail: String)

    val providers = listOf(
        Provider("Cloudflare for Families", "family.cloudflare-dns.com", "Blocks adult sites and malware"),
        Provider("AdGuard Family", "family.adguard-dns.com", "Blocks adult sites, malware and ads"),
        Provider("CleanBrowsing Family", "family-filter-dns.cleanbrowsing.org", "Blocks adult sites, enforces safe search")
    )

    fun hostname(context: Context): String? {
        val mode = runCatching {
            Settings.Global.getString(context.contentResolver, "private_dns_mode")
        }.getOrNull()
        if (mode != "hostname") return null
        return runCatching {
            Settings.Global.getString(context.contentResolver, "private_dns_specifier")
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** True when Android is set to a specific DNS hostname, whichever provider it is. */
    fun isSet(context: Context): Boolean = hostname(context) != null
}
