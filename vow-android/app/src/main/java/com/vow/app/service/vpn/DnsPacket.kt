package com.vow.app.service.vpn

/** Minimal IPv4/UDP/DNS handling for the virtual DNS server. */
object DnsPacket {

    class Query(
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val dnsPayload: ByteArray,
        val domain: String,
        val questionEnd: Int
    )

    fun parseQuery(packet: ByteArray, dnsServer: ByteArray): Query? {
        if (packet.size < 20) return null
        if ((packet[0].toInt() shr 4) != 4) return null
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (packet[9].toInt() and 0xFF != 17) return null
        if (packet.size < ihl + 8 + 12) return null
        val dst = packet.copyOfRange(16, 20)
        if (!dst.contentEquals(dnsServer)) return null
        val srcPort = u16(packet, ihl)
        val dstPort = u16(packet, ihl + 2)
        if (dstPort != 53) return null

        val payload = packet.copyOfRange(ihl + 8, packet.size)
        if (u16(payload, 4) < 1) return null

        val sb = StringBuilder()
        var pos = 12
        while (true) {
            if (pos >= payload.size) return null
            val len = payload[pos].toInt() and 0xFF
            if (len == 0) { pos++; break }
            if (len and 0xC0 != 0) return null
            if (pos + 1 + len > payload.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 1..len) sb.append((payload[pos + i].toInt() and 0xFF).toChar())
            pos += len + 1
        }
        val questionEnd = pos + 4
        if (questionEnd > payload.size) return null

        return Query(packet.copyOfRange(12, 16), dst, srcPort, dstPort, payload, sb.toString(), questionEnd)
    }

    /** Same question back with "no such domain", so the browser fails fast. */
    fun nxdomain(q: Query): ByteArray {
        val r = q.dnsPayload.copyOf(q.questionEnd)
        r[2] = (0x80 or (r[2].toInt() and 0x01)).toByte()
        r[3] = 0x83.toByte()
        for (i in 6..11) r[i] = 0
        return r
    }

    fun wrapReply(q: Query, dns: ByteArray): ByteArray {
        val total = 20 + 8 + dns.size
        val p = ByteArray(total)
        p[0] = 0x45
        put16(p, 2, total)
        p[6] = 0x40
        p[8] = 64
        p[9] = 17
        System.arraycopy(q.dstIp, 0, p, 12, 4)
        System.arraycopy(q.srcIp, 0, p, 16, 4)
        put16(p, 10, checksum(p, 0, 20))
        put16(p, 20, q.dstPort)
        put16(p, 22, q.srcPort)
        put16(p, 24, 8 + dns.size)
        System.arraycopy(dns, 0, p, 28, dns.size)
        return p
    }

    fun checksum(b: ByteArray, off: Int, len: Int): Int {
        var sum = 0L
        var i = off
        while (i < off + len - 1) {
            sum += u16(b, i)
            i += 2
        }
        if (len % 2 == 1) sum += (b[off + len - 1].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    private fun put16(b: ByteArray, i: Int, v: Int) {
        b[i] = (v shr 8).toByte()
        b[i + 1] = v.toByte()
    }
}
