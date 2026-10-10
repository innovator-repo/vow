package com.vow.app.service.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.vow.app.MainActivity
import com.vow.app.R
import com.vow.app.data.model.RestrictionType
import com.vow.app.data.prefs.VowPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

/**
 * DNS-only filter. Only traffic to a virtual DNS address enters the tunnel; everything else
 * keeps using the normal network, so a bug here can never take the internet down.
 */
class VowVpnService : VpnService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val upstreamPool = Executors.newFixedThreadPool(4)
    private lateinit var prefs: VowPreferences

    private var vpnInterface: ParcelFileDescriptor? = null
    private var readerThread: Thread? = null
    private var output: FileOutputStream? = null

    @Volatile private var blockAdult = false
    @Volatile private var blockKeywords = false
    @Volatile private var keywords: List<String> = emptyList()

    companion object {
        const val CHANNEL_ID = "vow_vpn_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.vow.app.VPN_STOP"
        private const val TAG = "VowVpn"
        private const val VPN_ADDRESS = "10.111.222.1"
        private const val VPN_DNS = "10.111.222.53"
        private val VPN_DNS_BYTES = byteArrayOf(10, 111, 222.toByte(), 53)
        private val UPSTREAMS = listOf("1.1.1.1", "8.8.8.8")

        /** Firefox turns off its built-in encrypted DNS when this name doesn't resolve. */
        private const val DOH_CANARY = "use-application-dns.net"

        private val adultDomains = setOf(
            "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com",
            "redtube.com", "youporn.com", "tube8.com", "spankbang.com",
            "beeg.com", "txxx.com", "hqporner.com", "eporner.com",
            "pornone.com", "thumbzilla.com", "ixxx.com", "fuq.com",
            "4tube.com", "porntrex.com", "tnaflix.com", "drtuber.com",
            "porntube.com", "fapster.xxx", "lobstertube.com", "sunporno.com",
            "empflix.com", "porndig.com", "tiava.com", "3movs.com",
            "pornpics.com", "sexvid.xxx", "hdporn.net", "porn.com",
            "sex.com", "chaturbate.com", "stripchat.com", "bongacams.com",
            "cam4.com", "camsoda.com", "livejasmin.com", "myfreecams.com",
            "onlyfans.com", "fansly.com", "manyvids.com",
            "brazzers.com", "realitykings.com", "bangbros.com",
            "naughtyamerica.com", "mofos.com", "digitalplayground.com",
            "twistys.com", "babes.com", "wicked.com"
        )
        private val adultTlds = setOf(".xxx", ".porn", ".sex", ".adult")
        private val adultLabels = setOf("porn", "xxx", "sex", "hentai", "nsfw", "adult", "erotic")
        private val strongWords = setOf("porn", "xxx", "hentai", "nsfw")

        fun isAdultDomain(domain: String): Boolean {
            if (adultDomains.any { domain == it || domain.endsWith(".$it") }) return true
            if (adultTlds.any { domain.endsWith(it) }) return true
            val labels = domain.split('.')
            if (labels.any { it in adultLabels }) return true
            // Hyphenated names only match unambiguous words, so "adult-education" stays reachable.
            return labels.flatMap { it.split('-') }.any { it in strongWords }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = VowPreferences(this)
        createNotificationChannel()
        scope.launch {
            combine(prefs.getRestrictions(), prefs.getBlockedKeywords(), prefs.getProfiles()) { r, k, p -> Triple(r, k, p) }
                .collect { (restrictions, kws, profiles) ->
                    val active = profiles.filter { it.isActive }
                    blockAdult = restrictions.any { it.type == RestrictionType.ADULT_SITES && it.enabled } ||
                        active.any { it.blockAdultSites }
                    val restrictionKeywords = if (restrictions.any { it.type == RestrictionType.KEYWORD_BLOCK && it.enabled })
                        kws.filter { it.enabled }.map { it.keyword } else emptyList()
                    keywords = (restrictionKeywords + active.flatMap { it.blockedKeywords })
                        .map { it.lowercase().trim() }.filter { it.isNotEmpty() }.distinct()
                    blockKeywords = keywords.isNotEmpty()
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            )
            if (vpnInterface == null) establish()
        } catch (e: Exception) {
            Log.e(TAG, "Could not start filter", e)
            shutdown()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun establish() {
        if (prepare(this) != null) {
            // Consent was never given or was revoked; nothing to run.
            shutdown()
            return
        }
        val pfd = Builder()
            .setSession("Vow content filter")
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(VPN_DNS)
            .addRoute(VPN_DNS, 32)
            .addDisallowedApplication(packageName)
            .setBlocking(true)
            .setMtu(1500)
            .also { if (Build.VERSION.SDK_INT >= 29) it.setMetered(false) }
            .establish() ?: run { shutdown(); return }

        vpnInterface = pfd
        val out = FileOutputStream(pfd.fileDescriptor)
        output = out
        readerThread = Thread({ readLoop(FileInputStream(pfd.fileDescriptor)) }, "vow-dns").apply { start() }
    }

    private fun readLoop(input: FileInputStream) {
        val buffer = ByteArray(32767)
        while (!Thread.currentThread().isInterrupted) {
            val length = try {
                input.read(buffer)
            } catch (e: Exception) {
                break // interface closed
            }
            if (length <= 0) continue
            try {
                handlePacket(buffer.copyOf(length))
            } catch (e: Exception) {
                Log.w(TAG, "Dropped packet", e)
            }
        }
    }

    private fun handlePacket(packet: ByteArray) {
        val q = DnsPacket.parseQuery(packet, VPN_DNS_BYTES) ?: return
        val domain = q.domain.lowercase().trimEnd('.')

        val blocked = domain == DOH_CANARY ||
            (blockAdult && isAdultDomain(domain)) ||
            (blockKeywords && keywords.any { domain.contains(it) })

        if (blocked) {
            write(DnsPacket.wrapReply(q, DnsPacket.nxdomain(q)))
            return
        }
        upstreamPool.execute {
            val reply = forward(q.dnsPayload) ?: return@execute
            write(DnsPacket.wrapReply(q, reply))
        }
    }

    private fun forward(payload: ByteArray): ByteArray? {
        for (host in UPSTREAMS) {
            try {
                DatagramSocket().use { socket ->
                    protect(socket)
                    socket.soTimeout = 4000
                    socket.send(DatagramPacket(payload, payload.size, InetAddress.getByName(host), 53))
                    val buf = ByteArray(4096)
                    val resp = DatagramPacket(buf, buf.size)
                    socket.receive(resp)
                    return buf.copyOf(resp.length)
                }
            } catch (_: Exception) {
                // try next upstream
            }
        }
        return null
    }

    private fun write(packet: ByteArray) {
        val out = output ?: return
        synchronized(out) {
            try { out.write(packet) } catch (_: Exception) {}
        }
    }

    override fun onRevoke() {
        shutdown()
        super.onRevoke()
    }

    private fun shutdown() {
        readerThread?.interrupt()
        readerThread = null
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        output = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        shutdown()
        scope.cancel()
        upstreamPool.shutdownNow()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Vow Protection", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Active protection notification"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Content filter is on")
            .setContentText("Filtering sites on your personal profile")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
