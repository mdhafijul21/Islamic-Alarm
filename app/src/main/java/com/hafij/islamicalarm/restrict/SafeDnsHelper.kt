package com.hafij.islamicalarm.restrict

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import java.util.Locale

/**
 * Inspects Android's native Private DNS (API 28+) and active external VPNs so Safe Mode can:
 *  - Avoid disconnecting an external VPN the user is currently using
 *  - Skip launching the local VPN tunnel when a Family-Safe Private DNS is already configured
 *  - Help users copy and configure a Family-Safe Private DNS hostname in Android Settings
 *    (which requires zero VPN icon and leaves the single VPN slot free for other VPN apps).
 */
object SafeDnsHelper {

    const val HOST_ADGUARD_FAMILY = "family.adguard-dns.com"
    const val HOST_CLOUDFLARE_FAMILY = "family.cloudflare-dns.com"

    private val KNOWN_FAMILY_DNS_HOSTS = listOf(
        "family.adguard-dns.com",
        "dns-family.adguard.com",
        "family.cloudflare-dns.com",
        "adult-filter-dns.cleanbrowsing.org",
        "family-filter-dns.cleanbrowsing.org",
        "familyshield.opendns.com",
        "family.dns.mullvad.net"
    )

    data class PrivateDnsStatus(
        val isSupported: Boolean,
        val isActive: Boolean,
        val serverName: String?,
        val isFamilySafe: Boolean
    )

    fun getPrivateDnsStatus(context: Context): PrivateDnsStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return PrivateDnsStatus(
                isSupported = false,
                isActive = false,
                serverName = null,
                isFamilySafe = false
            )
        }
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return PrivateDnsStatus(true, false, null, false)
            val network = cm.activeNetwork
                ?: return PrivateDnsStatus(true, false, null, false)
            val lp = cm.getLinkProperties(network)
                ?: return PrivateDnsStatus(true, false, null, false)
            val active = lp.isPrivateDnsActive
            val host = lp.privateDnsServerName?.trim()?.takeIf { it.isNotEmpty() }
            val familySafe = active && isKnownFamilyHostname(host)
            PrivateDnsStatus(
                isSupported = true,
                isActive = active,
                serverName = host,
                isFamilySafe = familySafe
            )
        } catch (_: Throwable) {
            PrivateDnsStatus(
                isSupported = true,
                isActive = false,
                serverName = null,
                isFamilySafe = false
            )
        }
    }

    fun isKnownFamilyHostname(hostname: String?): Boolean {
        val clean = hostname?.trim()?.lowercase(Locale.ROOT) ?: return false
        if (clean.isEmpty()) return false
        if (KNOWN_FAMILY_DNS_HOSTS.any { clean == it || clean.endsWith(".$it") }) return true
        return clean.contains("family") || clean.contains("adult-filter") || clean.contains("safesearch")
    }

    /**
     * Returns true when another VPN app holds the system VPN transport (i.e. a VPN is active
     * on the device and it is NOT our own [SafeDnsVpnService]).
     */
    fun isExternalVpnActive(context: Context): Boolean {
        if (SafeDnsVpnService.isVpnRunning) return false
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeCaps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            if (activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                return true
            }
            @Suppress("DEPRECATION")
            cm.allNetworks.any { net ->
                cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun copyHostname(context: Context, hostname: String = HOST_ADGUARD_FAMILY): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return false
            val clip = ClipData.newPlainText("Safe Private DNS Hostname", hostname)
            clipboard.setPrimaryClip(clip)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun openPrivateDnsSettings(context: Context): Boolean {
        val candidates = listOf(
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Throwable) {
            }
        }
        return false
    }
}
