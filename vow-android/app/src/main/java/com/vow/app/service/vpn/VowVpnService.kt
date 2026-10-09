package com.vow.app.service.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.vow.app.MainActivity
import com.vow.app.R
import com.vow.app.data.prefs.VowPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

class VowVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var prefs: VowPreferences

    // Built-in adult domain list (compact, covers major sites)
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

    // Suspicious TLD patterns for adult content
    private val adultTlds = setOf(".xxx", ".porn", ".sex", ".adult")

    companion object {
        const val CHANNEL_ID = "vow_vpn_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.vow.app.VPN_START"
        const val ACTION_STOP = "com.vow.app.VPN_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        prefs = VowPreferences(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                startVpn()
            }
        }
        return START_STICKY
    }

    private fun startVpn() {
        scope.launch {
            try {
                val builder = Builder()
                    .setSession("Vow Protection")
                    .addAddress("10.0.0.2", 32)
                    .addDnsServer("8.8.8.8")
                    .addRoute("0.0.0.0", 0)
                    .setBlocking(true)
                    .setMtu(1500)

                vpnInterface = builder.establish()
                vpnInterface?.let { handlePackets(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun handlePackets(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buffer = ByteArray(32767)

        val blockedKeywords = prefs.getBlockedKeywords().first()
            .filter { it.enabled }
            .map { it.keyword.lowercase() }

        val blockAdult = prefs.getRestrictions().first()
            .any { it.type == com.vow.app.data.model.RestrictionType.ADULT_SITES && it.enabled }

        val blockKeywords = prefs.getRestrictions().first()
            .any { it.type == com.vow.app.data.model.RestrictionType.KEYWORD_BLOCK && it.enabled }

        while (!Thread.currentThread().isInterrupted) {
            try {
                val length = input.read(buffer)
                if (length <= 0) {
                    Thread.sleep(100)
                    continue
                }

                val packet = ByteBuffer.wrap(buffer, 0, length)

                // Check if it's a DNS query (UDP port 53)
                if (isDnsQuery(packet)) {
                    val domain = extractDomainFromDns(buffer, length)
                    if (domain != null) {
                        val domainLower = domain.lowercase()
                        var blocked = false

                        // Check adult domains
                        if (blockAdult && isAdultDomain(domainLower)) {
                            blocked = true
                        }

                        // Check keyword blocking
                        if (!blocked && blockKeywords) {
                            for (kw in blockedKeywords) {
                                if (domainLower.contains(kw)) {
                                    blocked = true
                                    break
                                }
                            }
                        }

                        if (blocked) {
                            // Send back a sinkhole response (0.0.0.0)
                            val response = buildDnsSinkhole(buffer, length)
                            if (response != null) {
                                output.write(response)
                            }
                            continue
                        }
                    }
                }

                // Forward non-blocked packets via real DNS
                forwardPacket(buffer, length, output)
            } catch (e: Exception) {
                if (Thread.currentThread().isInterrupted) break
                Thread.sleep(100)
            }
        }
    }

    private fun isDnsQuery(packet: ByteBuffer): Boolean {
        if (packet.limit() < 28) return false
        // IP header: protocol at byte 9, UDP = 17
        val protocol = packet.get(9).toInt() and 0xFF
        if (protocol != 17) return false
        // UDP header starts at byte 20 (assuming no IP options)
        val destPort = ((packet.get(22).toInt() and 0xFF) shl 8) or (packet.get(23).toInt() and 0xFF)
        return destPort == 53
    }

    private fun extractDomainFromDns(buffer: ByteArray, length: Int): String? {
        try {
            // IP header (20 bytes) + UDP header (8 bytes) + DNS header (12 bytes) = 40
            val dnsStart = 28 // IP(20) + UDP(8)
            val questionStart = dnsStart + 12 // DNS header is 12 bytes

            if (questionStart >= length) return null

            val sb = StringBuilder()
            var pos = questionStart
            while (pos < length) {
                val labelLen = buffer[pos].toInt() and 0xFF
                if (labelLen == 0) break
                if (sb.isNotEmpty()) sb.append('.')
                pos++
                for (i in 0 until labelLen) {
                    if (pos >= length) return null
                    sb.append(buffer[pos].toInt().toChar())
                    pos++
                }
            }
            return sb.toString()
        } catch (e: Exception) {
            return null
        }
    }

    private fun isAdultDomain(domain: String): Boolean {
        // Check against known list
        for (ad in adultDomains) {
            if (domain == ad || domain.endsWith(".$ad")) return true
        }
        // Check TLDs
        for (tld in adultTlds) {
            if (domain.endsWith(tld)) return true
        }
        // Heuristic: domain contains "porn", "xxx", "sex" as standalone segments
        val parts = domain.split(".")
        val adultSegments = setOf("porn", "xxx", "sex", "hentai", "nsfw", "adult", "erotic")
        for (part in parts) {
            if (part in adultSegments) return true
        }
        return false
    }

    private fun buildDnsSinkhole(original: ByteArray, length: Int): ByteArray? {
        try {
            // Build a minimal DNS response pointing to 0.0.0.0
            val dnsStart = 28
            if (dnsStart + 12 > length) return null

            val response = original.copyOf(length + 16) // room for answer
            // Swap IP src/dst
            System.arraycopy(original, 12, response, 16, 4) // src -> dst
            System.arraycopy(original, 16, response, 12, 4) // dst -> src
            // Swap UDP src/dst ports
            System.arraycopy(original, 20, response, 22, 2)
            System.arraycopy(original, 22, response, 20, 2)
            // Set DNS flags: response, no error
            response[dnsStart + 2] = 0x81.toByte()
            response[dnsStart + 3] = 0x80.toByte()
            // Answer count = 1
            response[dnsStart + 6] = 0
            response[dnsStart + 7] = 1

            // Append answer: pointer to question, type A, class IN, TTL 60, 0.0.0.0
            val answerStart = length
            response[answerStart] = 0xC0.toByte() // pointer
            response[answerStart + 1] = 0x0C.toByte() // offset 12
            response[answerStart + 2] = 0; response[answerStart + 3] = 1 // Type A
            response[answerStart + 4] = 0; response[answerStart + 5] = 1 // Class IN
            response[answerStart + 6] = 0; response[answerStart + 7] = 0
            response[answerStart + 8] = 0; response[answerStart + 9] = 60 // TTL 60s
            response[answerStart + 10] = 0; response[answerStart + 11] = 4 // RDLENGTH
            response[answerStart + 12] = 0; response[answerStart + 13] = 0
            response[answerStart + 14] = 0; response[answerStart + 15] = 0 // 0.0.0.0

            // Update IP total length and UDP length
            val totalLen = length + 16
            response[2] = ((totalLen shr 8) and 0xFF).toByte()
            response[3] = (totalLen and 0xFF).toByte()
            val udpLen = totalLen - 20
            response[24] = ((udpLen shr 8) and 0xFF).toByte()
            response[25] = (udpLen and 0xFF).toByte()

            return response.copyOf(totalLen)
        } catch (e: Exception) {
            return null
        }
    }

    private fun forwardPacket(buffer: ByteArray, length: Int, output: FileOutputStream) {
        try {
            // For DNS queries, forward to real DNS and write back response
            if (isDnsQuery(ByteBuffer.wrap(buffer, 0, length))) {
                val dnsStart = 28
                val dnsPayload = buffer.copyOfRange(dnsStart, length)

                val socket = DatagramSocket()
                protect(socket) // Bypass VPN for this socket

                val packet = DatagramPacket(
                    dnsPayload, dnsPayload.size,
                    InetAddress.getByName("8.8.8.8"), 53
                )
                socket.soTimeout = 5000
                socket.send(packet)

                val responseBuffer = ByteArray(1024)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(responsePacket)
                socket.close()

                // Build IP+UDP wrapper for the DNS response
                val responseData = responseBuffer.copyOf(responsePacket.length)
                val fullResponse = buildIpUdpWrapper(buffer, length, responseData)
                if (fullResponse != null) {
                    output.write(fullResponse)
                }
            }
        } catch (e: Exception) {
            // Silently drop failed forwards
        }
    }

    private fun buildIpUdpWrapper(originalIp: ByteArray, origLen: Int, dnsResponse: ByteArray): ByteArray? {
        try {
            val totalLen = 20 + 8 + dnsResponse.size // IP + UDP + DNS
            val packet = ByteArray(totalLen)

            // IP header
            packet[0] = 0x45.toByte() // Version 4, IHL 5
            packet[1] = 0
            packet[2] = ((totalLen shr 8) and 0xFF).toByte()
            packet[3] = (totalLen and 0xFF).toByte()
            packet[4] = 0; packet[5] = 0 // Identification
            packet[6] = 0x40.toByte(); packet[7] = 0 // Don't fragment
            packet[8] = 64 // TTL
            packet[9] = 17 // UDP
            packet[10] = 0; packet[11] = 0 // Checksum (skip for tun)
            // Source: original destination (DNS server)
            System.arraycopy(originalIp, 16, packet, 12, 4)
            // Destination: original source (us)
            System.arraycopy(originalIp, 12, packet, 16, 4)

            // UDP header
            // Source port: original dest port (53)
            System.arraycopy(originalIp, 22, packet, 20, 2)
            // Dest port: original source port
            System.arraycopy(originalIp, 20, packet, 22, 2)
            val udpLen = 8 + dnsResponse.size
            packet[24] = ((udpLen shr 8) and 0xFF).toByte()
            packet[25] = (udpLen and 0xFF).toByte()
            packet[26] = 0; packet[27] = 0 // Checksum (0 = skip)

            // DNS response
            System.arraycopy(dnsResponse, 0, packet, 28, dnsResponse.size)

            return packet
        } catch (e: Exception) {
            return null
        }
    }

    private fun stopVpn() {
        scope.cancel()
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Vow Protection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active protection notification"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vow is active")
            .setContentText("Your commitments are being protected")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
