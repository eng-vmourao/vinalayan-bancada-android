package com.vinalayan.bancada.protocol

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.RSAPublicKeySpec
import javax.crypto.Cipher

/**
 * Phone-side packets copied from DashCommands.kt so the bench and the tests
 * speak the same bytes the Vinalayan app sends. Each constant cites the line.
 */
object PhonePackets {
    /** DashCommands.kt:13 authRequest — q3c.e */
    fun authRequest(): ByteArray =
        "0016000200000000020100054b314720000804000101".hexToBytes()

    /** DashCommands.kt:16-18 authSendKey — q3c.d, ciphertext is 128 bytes. */
    fun authSendKey(ciphertext: ByteArray): ByteArray {
        require(ciphertext.size == 128) { "q3c.d expects 128B, got ${ciphertext.size}" }
        return "0095000200000000020100054B3147200008000080".hexToBytes() + ciphertext
    }

    /**
     * DashCommands.kt:57-69. The 0x01 written after the magic is the seq
     * placeholder: DashSocket.send (DashSocket.kt:75) calls patchSeq
     * (K1GPacket.kt:59-65), which replaces that byte. On the wire the TLV is
     * type 0x06 sub 0x0B.
     */
    fun hostnameAnnounce(hostname: String, seq: Int = 0): ByteArray {
        val raw = hostname.toByteArray(Charsets.UTF_8).let {
            if (it.size > 200) it.copyOf(200) else it
        }
        val out = ByteArrayOutputStream()
        out.write("0021000200000000020100054b314720".hexToBytes())
        out.write(byteArrayOf(0x01, 0x06, 0x0B, 0x00, (raw.size + 1).toByte()))
        out.write(raw)
        out.write(0)
        val bytes = out.toByteArray()
        bytes[0] = ((bytes.size shr 8) and 0xFF).toByte()
        bytes[1] = (bytes.size and 0xFF).toByte()
        return patchSeq(bytes, seq)
    }

    /** DashCommands.kt:89 projection keep-alive, 4 Hz. Value 0x55. */
    fun projectionFrame(): ByteArray =
        "0016000200000000020100054B314720000556000155".hexToBytes()

    /** DashCommands.kt:90 */
    fun projectionOn(): ByteArray =
        "0016000200000000020100054B314720000605000155".hexToBytes()

    /** DashCommands.kt:91 */
    fun projectionStop(): ByteArray =
        "0016000200000000020100054B3147200005560001AA".hexToBytes()

    /** DashCommands.kt:266-274 now playing, NUL-separated, max 20 bytes each. */
    fun nowPlaying(title: String, album: String, artist: String): ByteArray {
        val value = ByteArrayOutputStream().apply {
            write(title.take(20).toByteArray(Charsets.UTF_8))
            write(0)
            write(album.take(20).toByteArray(Charsets.UTF_8))
            write(0)
            write(artist.take(20).toByteArray(Charsets.UTF_8))
        }.toByteArray()
        return K1G.buildOutgoing(Tlv(0x05, 0x0D, value))
    }

    /** DashCommands.kt:278-281 */
    fun callNotify(callerName: String): ByteArray =
        K1G.buildOutgoing(Tlv(0x05, 0x22, callerName.take(20).toByteArray(Charsets.UTF_8) + 0x00))

    /** DashCommands.kt:285 */
    fun callClear(): ByteArray =
        K1G.buildOutgoing(Tlv(0x05, 0x22, byteArrayOf(0x00)))

    /**
     * DashAuth.kt:62-71. RSA/ECB/PKCS1 of (SSID UTF-8 ‖ AES-256 key).
     * The dash public key arrived as TLV 07 00 (modulus) and 07 03 (exponent),
     * DashAuth.kt:40-41, BigInteger(1, value).
     */
    fun encryptSession(ssid: String, aesKey: ByteArray, modulus: ByteArray, exponent: ByteArray): ByteArray {
        require(aesKey.size == 32)
        val payload = ssid.toByteArray(Charsets.UTF_8) + aesKey
        val pub: PublicKey = KeyFactory.getInstance("RSA").generatePublic(
            RSAPublicKeySpec(BigInteger(1, modulus), BigInteger(1, exponent)),
        )
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, pub)
        return cipher.doFinal(payload)
    }

    fun patchSeq(pkt: ByteArray, seq: Int): ByteArray {
        val out = pkt.copyOf()
        val magic = byteArrayOf(0x4B, 0x31, 0x47, 0x20)
        outer@ for (i in 0..out.size - 4) {
            for (j in 0..3) if (out[i + j] != magic[j]) continue@outer
            if (i + 4 < out.size) out[i + 4] = (seq and 0xFF).toByte()
            break
        }
        out[0] = ((out.size shr 8) and 0xFF).toByte()
        out[1] = (out.size and 0xFF).toByte()
        return out
    }
}
