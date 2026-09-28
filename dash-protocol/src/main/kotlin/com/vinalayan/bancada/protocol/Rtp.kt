package com.vinalayan.bancada.protocol

import java.util.Random

/**
 * RTP/H.264 from RtpPacketizer.kt:15-83.
 * PT 96, 90 kHz clock, no STAP-A, FU-A (type 28) above 1380 bytes,
 * marker only on the last packet of an access unit.
 */
class RtpPacketizer(private val onPacket: (ByteArray) -> Unit) {
    private val rng = Random()
    private var seq = rng.nextInt(0xFFFF)
    private val ssrc = rng.nextInt()
    private val tsBase = rng.nextInt().toLong() and 0xFFFFFFFFL

    fun packetize(nal: ByteArray, endOfAU: Boolean, wallClockMs: Long) {
        val ts = (tsBase + wallClockMs * 90L) and 0xFFFFFFFFL
        if (nal.size <= MAX_PAYLOAD) emit(nal, endOfAU, ts) else fuA(nal, endOfAU, ts)
    }

    private fun fuA(nal: ByteArray, endOfAU: Boolean, ts: Long) {
        val nalType = nal[0].toInt() and 0x1F
        val fuInd = ((nal[0].toInt() and 0xE0) or 28).toByte()
        var offset = 1
        var isFirst = true
        while (offset < nal.size) {
            val remaining = nal.size - offset
            val chunkLen = minOf(MAX_PAYLOAD - 2, remaining)
            val isLast = chunkLen >= remaining
            val fuHeader = ((if (isFirst) 0x80 else 0) or (if (isLast) 0x40 else 0) or nalType).toByte()
            val payload = ByteArray(2 + chunkLen)
            payload[0] = fuInd
            payload[1] = fuHeader
            nal.copyInto(payload, 2, offset, offset + chunkLen)
            emit(payload, isLast && endOfAU, ts)
            offset += chunkLen
            isFirst = false
        }
    }

    private fun emit(payload: ByteArray, marker: Boolean, ts: Long) {
        val pkt = ByteArray(12 + payload.size)
        pkt[0] = 0x80.toByte()
        pkt[1] = ((if (marker) 0x80 else 0) or (PT and 0x7F)).toByte()
        pkt[2] = ((seq shr 8) and 0xFF).toByte()
        pkt[3] = (seq and 0xFF).toByte()
        pkt[4] = ((ts shr 24) and 0xFF).toByte()
        pkt[5] = ((ts shr 16) and 0xFF).toByte()
        pkt[6] = ((ts shr 8) and 0xFF).toByte()
        pkt[7] = (ts and 0xFF).toByte()
        val s = ssrc.toLong() and 0xFFFFFFFFL
        pkt[8] = ((s shr 24) and 0xFF).toByte()
        pkt[9] = ((s shr 16) and 0xFF).toByte()
        pkt[10] = ((s shr 8) and 0xFF).toByte()
        pkt[11] = (s and 0xFF).toByte()
        payload.copyInto(pkt, 12)
        seq = (seq + 1) and 0xFFFF
        onPacket(pkt)
    }

    companion object {
        const val MAX_PAYLOAD = 1380
        const val PT = 96
    }
}

/** Reassembles the RTP stream the phone sends (RtpPacketizer.kt). */
class RtpReassembler(private val onNal: (ByteArray, Boolean) -> Unit) {
    private val fu = ArrayList<ByteArray>()
    private var fuHeader: Int = 0

    fun push(packet: ByteArray) {
        if (packet.size < 12) return
        val marker = (packet[1].toInt() and 0x80) != 0
        val payload = packet.copyOfRange(12, packet.size)
        if (payload.isEmpty()) return
        val nalType = payload[0].toInt() and 0x1F
        if (nalType == 28 && payload.size >= 2) {
            val fuH = payload[1].toInt() and 0xFF
            val start = (fuH and 0x80) != 0
            val end = (fuH and 0x40) != 0
            if (start) {
                fu.clear()
                fuHeader = (payload[0].toInt() and 0xE0) or (fuH and 0x1F)
            }
            fu += payload.copyOfRange(2, payload.size)
            if (end) {
                val bodyLen = fu.sumOf { it.size }
                val nal = ByteArray(1 + bodyLen)
                nal[0] = fuHeader.toByte()
                var o = 1
                for (c in fu) {
                    c.copyInto(nal, o)
                    o += c.size
                }
                fu.clear()
                emitSplit(nal, marker)
            }
        } else if (nalType in 1..23) {
            emitSplit(payload, marker)
        }
    }

    private fun emitSplit(buffer: ByteArray, marker: Boolean) {
        val parts = splitAnnexB(buffer)
        if (parts.size <= 1) {
            onNal(buffer, marker)
            return
        }
        parts.forEachIndexed { index, nal ->
            if (nal.isNotEmpty()) onNal(nal, marker && index == parts.lastIndex)
        }
    }

    private fun splitAnnexB(data: ByteArray): List<ByteArray> {
        if (!containsStartCode(data)) return listOf(data)
        val nals = mutableListOf<ByteArray>()
        var start = -1
        var i = 0
        while (i < data.size) {
            val sc4 = i + 3 < data.size && data[i] == 0.toByte() && data[i + 1] == 0.toByte() &&
                data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()
            val sc3 = !sc4 && i + 2 < data.size && data[i] == 0.toByte() &&
                data[i + 1] == 0.toByte() && data[i + 2] == 1.toByte()
            when {
                sc4 -> {
                    if (start >= 0) nals += data.copyOfRange(start, i)
                    start = i + 4
                    i += 4
                }
                sc3 -> {
                    if (start >= 0) nals += data.copyOfRange(start, i)
                    start = i + 3
                    i += 3
                }
                else -> i++
            }
        }
        if (start in 0 until data.size) nals += data.copyOfRange(start, data.size)
        return nals
    }

    private fun containsStartCode(data: ByteArray): Boolean {
        for (i in 0 until data.size - 3) {
            if (data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()) {
                return true
            }
        }
        return false
    }
}
