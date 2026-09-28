package com.vinalayan.bancada.protocol

import java.io.ByteArrayOutputStream

/**
 * Wire format transcribed from Vinalayan, not invented.
 *
 * Outgoing phone → dash: [K1GPacket.kt] lines 8–16 and [build] lines 35–55.
 *   [0:2] outer_len, [2:4] seg_count = 1 + N, [4:8] zeros,
 *   [8:12] 02 01 00 05, [12:16] "K1G ", [16] seq, [17+] TLV
 *   TLV = type:u8, sub:u8, len:u16be, value.
 *
 * Incoming dash → phone: [K1GPacket.parseIncoming] lines 73–90.
 *   [0:2] outer_len, [2:4] seg_count = N (number of TLVs, not 1+N),
 *   [4:8] ignored, [8+] TLVs.
 *
 * Seq patch: [K1GPacket.patchSeq] lines 59–65 overwrites the byte after "K1G ".
 */
data class Tlv(val type: Int, val sub: Int, val value: ByteArray = ByteArray(0)) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Tlv) return false
        return type == other.type && sub == other.sub && value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + sub
        result = 31 * result + value.contentHashCode()
        return result
    }
}

object K1G {
    private val MAGIC = byteArrayOf(0x4B, 0x31, 0x47, 0x20)

    fun buildOutgoing(vararg tlvs: Tlv, seq: Int = 0): ByteArray {
        val segCount = 1 + tlvs.size
        val out = ByteArrayOutputStream()
        out.write(0)
        out.write(0)
        out.write((segCount shr 8) and 0xFF)
        out.write(segCount and 0xFF)
        out.write(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x02, 0x01, 0x00, 0x05, 0x4B, 0x31, 0x47, 0x20))
        out.write(seq and 0xFF)
        for (tlv in tlvs) writeTlv(out, tlv)
        val bytes = out.toByteArray()
        bytes[0] = ((bytes.size shr 8) and 0xFF).toByte()
        bytes[1] = (bytes.size and 0xFF).toByte()
        return bytes
    }

    /** Dash → phone packet. seg_count is the TLV count. See K1GPacket.kt:73-90. */
    fun buildIncoming(vararg tlvs: Tlv): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0)
        out.write(0)
        out.write((tlvs.size shr 8) and 0xFF)
        out.write(tlvs.size and 0xFF)
        out.write(byteArrayOf(0, 0, 0, 0))
        for (tlv in tlvs) writeTlv(out, tlv)
        val bytes = out.toByteArray()
        bytes[0] = ((bytes.size shr 8) and 0xFF).toByte()
        bytes[1] = (bytes.size and 0xFF).toByte()
        return bytes
    }

    fun parseOutgoing(data: ByteArray): List<Tlv> {
        val start = indexOfMagic(data)
        if (start < 0) return emptyList()
        return readTlvs(data, start + 5, Int.MAX_VALUE)
    }

    fun parseIncoming(data: ByteArray): List<Tlv> {
        if (data.size < 8) return emptyList()
        val segCount = u16(data, 2)
        return readTlvs(data, 8, segCount)
    }

    fun u16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun writeTlv(out: ByteArrayOutputStream, tlv: Tlv) {
        out.write(tlv.type and 0xFF)
        out.write(tlv.sub and 0xFF)
        out.write((tlv.value.size shr 8) and 0xFF)
        out.write(tlv.value.size and 0xFF)
        out.write(tlv.value)
    }

    private fun readTlvs(data: ByteArray, start: Int, max: Int): List<Tlv> {
        val tlvs = mutableListOf<Tlv>()
        var i = start
        var n = 0
        while (n < max && i + 4 <= data.size) {
            val type = data[i].toInt() and 0xFF
            val sub = data[i + 1].toInt() and 0xFF
            val len = u16(data, i + 2)
            i += 4
            val end = (i + len).coerceAtMost(data.size)
            tlvs += Tlv(type, sub, data.copyOfRange(i, end))
            i = end
            n++
        }
        return tlvs
    }

    private fun indexOfMagic(b: ByteArray): Int {
        outer@ for (i in 0..b.size - 4) {
            for (j in 0..3) if (b[i + j] != MAGIC[j]) continue@outer
            return i
        }
        return -1
    }
}

fun String.hexToBytes(): ByteArray {
    val clean = replace(" ", "")
    require(clean.length % 2 == 0)
    return ByteArray(clean.length / 2) {
        clean.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
}

fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
