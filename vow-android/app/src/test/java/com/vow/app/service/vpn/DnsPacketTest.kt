package com.vow.app.service.vpn

import org.junit.Assert.*
import org.junit.Test

class DnsPacketTest {
    private val server = byteArrayOf(10, 111, 222.toByte(), 53)

    private fun query(name: String, withEdns: Boolean = true): ByteArray {
        val q = java.io.ByteArrayOutputStream()
        q.write(byteArrayOf(0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, if (withEdns) 1 else 0))
        for (l in name.split('.')) { q.write(l.length); q.write(l.toByteArray()) }
        q.write(byteArrayOf(0, 0, 1, 0, 1))
        if (withEdns) q.write(byteArrayOf(0, 0, 41, 0x10, 0, 0, 0, 0, 0, 0, 0))
        val dns = q.toByteArray()
        val total = 28 + dns.size
        val p = ByteArray(total)
        p[0] = 0x45; p[2] = (total shr 8).toByte(); p[3] = total.toByte(); p[8] = 64; p[9] = 17
        byteArrayOf(10, 111, 222.toByte(), 1).copyInto(p, 12); server.copyInto(p, 16)
        p[20] = 0xA1.toByte(); p[21] = 0x2B; p[22] = 0; p[23] = 53
        p[24] = ((8 + dns.size) shr 8).toByte(); p[25] = (8 + dns.size).toByte()
        dns.copyInto(p, 28)
        return p
    }

    @Test fun parsesAndBuildsValidNxdomain() {
        val q = DnsPacket.parseQuery(query("www.Pornhub.com"), server)!!
        assertEquals("www.Pornhub.com", q.domain)
        assertTrue(VowVpnService.isAdultDomain(q.domain.lowercase()))
        val reply = DnsPacket.wrapReply(q, DnsPacket.nxdomain(q))
        assertEquals(0, DnsPacket.checksum(reply, 0, 20))          // kernel-valid IPv4 header
        assertEquals(reply.size, ((reply[2].toInt() and 0xFF) shl 8) or (reply[3].toInt() and 0xFF))
        assertArrayEquals(server, reply.copyOfRange(12, 16))
        assertEquals(0xA1.toByte(), reply[22]); assertEquals(53.toByte(), reply[21])
        val dns = reply.copyOfRange(28, reply.size)
        assertEquals(0x12.toByte(), dns[0]); assertEquals(0x83.toByte(), dns[3]); assertEquals(0x81.toByte(), dns[2])
        assertEquals(0.toByte(), dns[11])                           // EDNS record dropped
        assertEquals(12 + 17 + 4, dns.size)
    }

    @Test fun ignoresTrafficNotForOurDns() {
        val p = query("example.com"); p[19] = 99
        assertNull(DnsPacket.parseQuery(p, server))
    }

    @Test fun safeDomainsPass() {
        listOf("google.com", "essex.ac.uk", "wikipedia.org", "middlesex.gov").forEach {
            assertFalse(it, VowVpnService.isAdultDomain(it))
        }
    }
}
