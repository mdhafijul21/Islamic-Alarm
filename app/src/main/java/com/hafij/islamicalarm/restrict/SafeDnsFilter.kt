package com.hafij.islamicalarm.restrict

/**
 * Pure-Kotlin IPv4 / IPv6 + UDP + DNS wire-format parser and response packet builder
 * used by [SafeDnsVpnService]. Kept free of Android framework state so it can be
 * thoroughly unit-tested in Robolectric / JVM tests.
 */
object SafeDnsFilter {

    data class ParsedDnsQuery(
        val ipVersion: Int,
        val ipHeaderLength: Int,
        val udpOffset: Int,
        val dnsPayloadOffset: Int,
        val dnsPayloadLength: Int,
        val queryDomain: String,
        val queryType: Int
    )

    val UPSTREAM_DNS_SERVERS = listOf(
        "1.1.1.3",          // Cloudflare Family (blocks adult + enforces SafeSearch)
        "94.140.14.15",     // AdGuard Family (blocks adult + enforces SafeSearch)
        "185.228.168.168"   // CleanBrowsing Family
    )

    fun isDomainBlocked(domain: String, customWords: Collection<String> = emptyList()): Boolean {
        return SafeWords.matchesDomainHost(domain, customWords)
    }

    /**
     * Parses a raw IP packet read from the TUN interface.
     * Returns null for non-UDP or non-DNS (port != 53) packets.
     */
    fun parseDnsQueryPacket(packet: ByteArray, length: Int): ParsedDnsQuery? {
        if (length < 28) return null
        val version = (packet[0].toInt() ushr 4) and 0x0F
        val ipHeaderLen: Int
        val protocol: Int

        when (version) {
            4 -> {
                ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
                if (ipHeaderLen < 20 || length < ipHeaderLen + 8 + 12) return null
                protocol = packet[9].toInt() and 0xFF
            }
            6 -> {
                ipHeaderLen = 40
                if (length < ipHeaderLen + 8 + 12) return null
                protocol = packet[6].toInt() and 0xFF
            }
            else -> return null
        }

        if (protocol != 17) return null // UDP only

        val udpOffset = ipHeaderLen
        val dstPort = ((packet[udpOffset + 2].toInt() and 0xFF) shl 8) or
            (packet[udpOffset + 3].toInt() and 0xFF)
        if (dstPort != 53) return null

        val udpLen = ((packet[udpOffset + 4].toInt() and 0xFF) shl 8) or
            (packet[udpOffset + 5].toInt() and 0xFF)
        val dnsOffset = udpOffset + 8
        val dnsLen = (udpLen - 8).coerceAtMost(length - dnsOffset)
        if (dnsLen < 12) return null

        // Check QR == 0 (query) and QDCOUNT >= 1
        val flagsHigh = packet[dnsOffset + 2].toInt() and 0xFF
        if ((flagsHigh and 0x80) != 0) return null
        val qdCount = ((packet[dnsOffset + 4].toInt() and 0xFF) shl 8) or
            (packet[dnsOffset + 5].toInt() and 0xFF)
        if (qdCount < 1) return null

        val (domain, afterQnameOffset) = readDnsName(packet, dnsOffset + 12, dnsOffset + dnsLen)
            ?: return null
        if (afterQnameOffset + 4 > dnsOffset + dnsLen) return null

        val qtype = ((packet[afterQnameOffset].toInt() and 0xFF) shl 8) or
            (packet[afterQnameOffset + 1].toInt() and 0xFF)

        return ParsedDnsQuery(
            ipVersion = version,
            ipHeaderLength = ipHeaderLen,
            udpOffset = udpOffset,
            dnsPayloadOffset = dnsOffset,
            dnsPayloadLength = dnsLen,
            queryDomain = domain,
            queryType = qtype
        )
    }

    private fun readDnsName(packet: ByteArray, startOffset: Int, endLimit: Int): Pair<String, Int>? {
        val sb = StringBuilder()
        var idx = startOffset
        var steps = 0
        while (idx < endLimit && steps < 128) {
            steps++
            val labelLen = packet[idx].toInt() and 0xFF
            if (labelLen == 0) {
                idx++
                return sb.toString() to idx
            }
            if ((labelLen and 0xC0) != 0) return null // Unexpected pointer in question
            idx++
            if (idx + labelLen > endLimit) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 0 until labelLen) {
                sb.append((packet[idx + i].toInt() and 0xFF).toChar())
            }
            idx += labelLen
        }
        return null
    }

    /**
     * Builds an NXDOMAIN (RCODE = 3) DNS response packet addressed back to the querying app.
     */
    fun buildBlockedDnsResponsePacket(requestPacket: ByteArray, query: ParsedDnsQuery): ByteArray {
        val dnsReq = requestPacket.copyOfRange(
            query.dnsPayloadOffset,
            query.dnsPayloadOffset + query.dnsPayloadLength
        )
        // Set QR = 1, copy RD, set RA = 1, RCODE = 3 (NXDOMAIN), AN/NS/AR = 0
        val rdBit = dnsReq[2].toInt() and 0x01
        dnsReq[2] = (0x80 or rdBit).toByte()
        dnsReq[3] = 0x83.toByte() // RA=1, RCODE=3 (NXDOMAIN)
        dnsReq[6] = 0
        dnsReq[7] = 0
        dnsReq[8] = 0
        dnsReq[9] = 0
        dnsReq[10] = 0
        dnsReq[11] = 0

        return buildIpUdpResponse(requestPacket, query, dnsReq, dnsReq.size)
    }

    /**
     * Wraps a raw DNS response payload from an upstream Family DNS server back into a valid
     * IP+UDP packet addressed to the app that sent [query].
     */
    fun buildForwardedDnsResponsePacket(
        requestPacket: ByteArray,
        query: ParsedDnsQuery,
        dnsResponsePayload: ByteArray,
        dnsResponseLength: Int
    ): ByteArray {
        return buildIpUdpResponse(requestPacket, query, dnsResponsePayload, dnsResponseLength)
    }

    private fun buildIpUdpResponse(
        requestPacket: ByteArray,
        query: ParsedDnsQuery,
        dnsPayload: ByteArray,
        dnsPayloadLen: Int
    ): ByteArray {
        val udpLen = 8 + dnsPayloadLen
        val totalLen = query.ipHeaderLength + udpLen
        val out = ByteArray(totalLen)

        // Copy original IP header template & swap src/dst addresses
        System.arraycopy(requestPacket, 0, out, 0, query.ipHeaderLength)

        if (query.ipVersion == 4) {
            // Total length
            out[2] = ((totalLen ushr 8) and 0xFF).toByte()
            out[3] = (totalLen and 0xFF).toByte()
            // Swap IPv4 src (12..15) <-> dst (16..19)
            for (i in 0 until 4) {
                out[12 + i] = requestPacket[16 + i]
                out[16 + i] = requestPacket[12 + i]
            }
            // Zero IPv4 checksum & compute
            out[10] = 0
            out[11] = 0
            val ipChecksum = computeChecksum(out, 0, query.ipHeaderLength)
            out[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
            out[11] = (ipChecksum and 0xFF).toByte()
        } else {
            // IPv6 payload length (bytes 4..5)
            out[4] = ((udpLen ushr 8) and 0xFF).toByte()
            out[5] = (udpLen and 0xFF).toByte()
            // Swap IPv6 src (8..23) <-> dst (24..39)
            for (i in 0 until 16) {
                out[8 + i] = requestPacket[24 + i]
                out[24 + i] = requestPacket[8 + i]
            }
        }

        val u = query.udpOffset
        // Swap UDP srcPort <-> dstPort
        out[u] = requestPacket[u + 2]
        out[u + 1] = requestPacket[u + 3]
        out[u + 2] = requestPacket[u]
        out[u + 3] = requestPacket[u + 1]
        // UDP length
        out[u + 4] = ((udpLen ushr 8) and 0xFF).toByte()
        out[u + 5] = (udpLen and 0xFF).toByte()
        // Zero UDP checksum before calculation
        out[u + 6] = 0
        out[u + 7] = 0

        // Copy DNS payload
        System.arraycopy(dnsPayload, 0, out, u + 8, dnsPayloadLen)

        if (query.ipVersion == 4) {
            // Optional for IPv4 UDP (0x0000 is valid, or compute pseudo-header checksum)
            val udpChecksum = computeUdpChecksumV4(out, u, udpLen)
            out[u + 6] = ((udpChecksum ushr 8) and 0xFF).toByte()
            out[u + 7] = (udpChecksum and 0xFF).toByte()
        } else {
            val udpChecksum = computeUdpChecksumV6(out, u, udpLen)
            out[u + 6] = ((udpChecksum ushr 8) and 0xFF).toByte()
            out[u + 7] = (udpChecksum and 0xFF).toByte()
        }

        return out
    }

    private fun computeChecksum(data: ByteArray, offset: Int, len: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + len
        while (i + 1 < end) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }
        return (sum.inv() and 0xFFFFL).toInt()
    }

    private fun computeUdpChecksumV4(packet: ByteArray, udpOffset: Int, udpLen: Int): Int {
        var sum = 0L
        // Src & Dst IPv4
        for (i in 12 until 20 step 2) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
        }
        sum += 17L // UDP protocol
        sum += udpLen.toLong()
        sum += foldWords(packet, udpOffset, udpLen)
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }
        val res = (sum.inv() and 0xFFFFL).toInt()
        return if (res == 0) 0xFFFF else res
    }

    private fun computeUdpChecksumV6(packet: ByteArray, udpOffset: Int, udpLen: Int): Int {
        var sum = 0L
        for (i in 8 until 40 step 2) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
        }
        sum += udpLen.toLong()
        sum += 17L
        sum += foldWords(packet, udpOffset, udpLen)
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }
        val res = (sum.inv() and 0xFFFFL).toInt()
        return if (res == 0) 0xFFFF else res
    }

    private fun foldWords(data: ByteArray, offset: Int, len: Int): Long {
        var sum = 0L
        var i = offset
        val end = offset + len
        while (i + 1 < end) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        return sum
    }
}
