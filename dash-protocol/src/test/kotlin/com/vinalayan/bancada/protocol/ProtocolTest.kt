package com.vinalayan.bancada.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.SecureRandom

class ProtocolTest {
    @Test
    fun authRequestMatchesVinalayanHex() {
        // DashCommands.kt:13
        assertArrayEquals(
            "0016000200000000020100054b314720000804000101".hexToBytes(),
            PhonePackets.authRequest(),
        )
        val tlvs = K1G.parseOutgoing(PhonePackets.authRequest())
        assertEquals(1, tlvs.size)
        assertEquals(0x08, tlvs[0].type)
        assertEquals(0x04, tlvs[0].sub)
        assertArrayEquals(byteArrayOf(0x01), tlvs[0].value)
    }

    @Test
    fun incomingRoundTripAndConfirmByte() {
        val pkt = K1G.buildIncoming(Tlv(0x07, 0x01, byteArrayOf(0x01)))
        val tlvs = K1G.parseIncoming(pkt)
        assertEquals(0x07, tlvs.single().type)
        assertEquals(0x01, tlvs.single().sub)
        assertEquals(0x01, tlvs.single().value[0].toInt() and 0xFF)
    }

    @Test
    fun hostnameSurvivesSeqPatchAsType06Sub0B() {
        val pkt = PhonePackets.hostnameAnnounce("OpenDash", seq = 7)
        assertEquals(7, pkt[16].toInt() and 0xFF)
        val tlv = K1G.parseOutgoing(pkt).single()
        assertEquals(0x06, tlv.type)
        assertEquals(0x0B, tlv.sub)
        assertTrue(tlv.value.toString(Charsets.UTF_8).startsWith("OpenDash"))
    }

    @Test
    fun nowPlayingAndCallMatchLayout() {
        val packet = PhonePackets.nowPlaying("Title", "Album", "Artist")
        val tlv = K1G.parseOutgoing(packet).single()
        assertEquals(0x05, tlv.type)
        assertEquals(0x0D, tlv.sub)
        assertArrayEquals("Title\u0000Album\u0000Artist".toByteArray(), tlv.value)

        val call = K1G.parseOutgoing(PhonePackets.callNotify("Mãe")).single()
        assertEquals(0x05, call.type)
        assertEquals(0x22, call.sub)
        assertEquals("Mãe", call.value.dropLast(1).toByteArray().toString(Charsets.UTF_8))

        val clear = K1G.parseOutgoing(PhonePackets.callClear()).single()
        assertEquals(0, clear.value[0].toInt())
    }

    @Test
    fun projectionCommands() {
        assertEquals(0x55, K1G.parseOutgoing(PhonePackets.projectionOn()).single().value[0].toInt() and 0xFF)
        assertEquals(0x06, K1G.parseOutgoing(PhonePackets.projectionOn()).single().type)
        assertEquals(0x05, K1G.parseOutgoing(PhonePackets.projectionOn()).single().sub)
        assertEquals(0xAA, K1G.parseOutgoing(PhonePackets.projectionStop()).single().value[0].toInt() and 0xFF)
        assertEquals(0x55, K1G.parseOutgoing(PhonePackets.projectionFrame()).single().value[0].toInt() and 0xFF)
    }

    @Test
    fun rtpSingleAndFragmentedNalRoundTrip() {
        val got = mutableListOf<Pair<ByteArray, Boolean>>()
        val asm = RtpReassembler { nal, marker -> got += nal to marker }
        val pack = RtpPacketizer { asm.push(it) }
        val small = byteArrayOf(0x61, 0x01, 0x02, 0x03)
        pack.packetize(small, endOfAU = false, wallClockMs = 0)
        val big = ByteArray(3000)
        big[0] = 0x65
        for (i in 1 until big.size) big[i] = (i and 0xFF).toByte()
        pack.packetize(big, endOfAU = true, wallClockMs = 40)
        assertEquals(2, got.size)
        assertArrayEquals(small, got[0].first)
        assertEquals(false, got[0].second)
        assertArrayEquals(big, got[1].first)
        assertEquals(true, got[1].second)
    }

    @Test
    fun fakePhoneAuthenticatesAndSendsVideo() {
        val phoneRx = DatagramSocket(0)
        phoneRx.soTimeout = 3000
        val engine = PanelEngine(
            bindHost = "127.0.0.1",
            requestedCtrlPort = 0,
            requestedRtpPort = 0,
            phoneRxPort = phoneRx.localPort,
        )
        val nals = mutableListOf<ByteArray>()
        engine.onAccessUnit = { nal, _ -> nals += nal }
        engine.start()
        try {
            val loopback = InetAddress.getByName("127.0.0.1")
            val tx = DatagramSocket()
            fun send(bytes: ByteArray, port: Int) {
                tx.send(DatagramPacket(bytes, bytes.size, loopback, port))
            }
            send(PhonePackets.patchSeq(PhonePackets.authRequest(), 1), engine.ctrlPort)
            send(PhonePackets.hostnameAnnounce("OpenDash", 2), engine.ctrlPort)

            val modulus = awaitTlv(phoneRx, 0x07, 0x00)
            val exponent = awaitTlv(phoneRx, 0x07, 0x03)
            val aes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val cipher = PhonePackets.encryptSession("LAB_DASH", aes, modulus, exponent)
            send(PhonePackets.authSendKey(cipher), engine.ctrlPort)
            val confirm = awaitTlv(phoneRx, 0x07, 0x01)
            assertEquals(0x01, confirm[0].toInt() and 0xFF)

            val deadline = System.currentTimeMillis() + 2000
            while (engine.link != PanelLink.AUTHENTICATED && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
            assertEquals(PanelLink.AUTHENTICATED, engine.link)
            assertEquals("LAB_DASH", engine.ssid)
            assertEquals("OpenDash", engine.hostname)
            assertArrayEquals(aes, engine.sessionKey)

            send(PhonePackets.nowPlaying("Garota de Ipanema", "Getz", "Gilberto"), engine.ctrlPort)
            send(PhonePackets.callNotify("Oficina"), engine.ctrlPort)
            send(PhonePackets.projectionOn(), engine.ctrlPort)
            Thread.sleep(80)
            assertEquals("Garota de Ipanema", engine.nowPlaying?.title)
            assertEquals("Gilberto", engine.nowPlaying?.artist)
            assertEquals("Oficina", engine.caller)
            assertEquals(PanelLink.PROJECTING, engine.link)

            val rtp = DatagramSocket()
            val pack = RtpPacketizer { pkt ->
                rtp.send(DatagramPacket(pkt, pkt.size, loopback, engine.rtpPort))
            }
            val idr = ByteArray(2000)
            idr[0] = 0x65
            idr[1] = 0x11
            pack.packetize(idr, endOfAU = true, wallClockMs = 0)
            val videoDeadline = System.currentTimeMillis() + 2000
            while (engine.videoFrames.get() < 1 && System.currentTimeMillis() < videoDeadline) {
                Thread.sleep(20)
            }
            assertTrue(engine.videoFrames.get() >= 1)
            assertTrue(nals.any { it.size == idr.size && it[0] == 0x65.toByte() && it[1] == 0x11.toByte() })
            val ack = awaitTlv(phoneRx, 0x09, 0x06)
            assertEquals(0x55, ack[0].toInt() and 0xFF)

            engine.sendButton(DashButtons.MEDIA_NEXT)
            val button = awaitTlv(phoneRx, 0x09, 0x00)
            assertEquals(DashButtons.MEDIA_NEXT, button.last().toInt() and 0xFF)

            engine.setIgnition(false)
            assertEquals(PanelLink.IGNITION_OFF, engine.link)
            val before = engine.packetsOut.get()
            send(PhonePackets.authRequest(), engine.ctrlPort)
            Thread.sleep(100)
            assertEquals(before, engine.packetsOut.get())
            tx.close()
            rtp.close()
        } finally {
            engine.close()
            phoneRx.close()
        }
    }

    private fun awaitTlv(socket: DatagramSocket, type: Int, sub: Int): ByteArray {
        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline) {
            val buf = DatagramPacket(ByteArray(2048), 2048)
            socket.receive(buf)
            val data = buf.data.copyOf(buf.length)
            val match = K1G.parseIncoming(data).firstOrNull { it.type == type && it.sub == sub }
            if (match != null) return match.value
        }
        throw AssertionError("timeout waiting TLV %02X %02X".format(type, sub))
    }
}
