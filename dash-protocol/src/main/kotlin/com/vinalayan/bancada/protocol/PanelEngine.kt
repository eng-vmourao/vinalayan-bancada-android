package com.vinalayan.bancada.protocol

import java.math.BigInteger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class PanelLink {
    WAITING,
    AUTHENTICATING,
    AUTHENTICATED,
    PROJECTING,
    IGNITION_OFF,
    LINK_DOWN,
}

data class NowPlaying(val title: String, val album: String, val artist: String)

data class PanelSnapshot(
    val link: PanelLink,
    val ssid: String?,
    val hostname: String?,
    val nowPlaying: NowPlaying?,
    val caller: String?,
    val clock: String?,
    val packetsIn: Long,
    val packetsOut: Long,
    val videoFrames: Long,
    val videoFps: Double,
    val lastError: String?,
    val ctrlPort: Int,
    val rtpPort: Int,
    val phoneAddress: String?,
)

/**
 * Motorcycle side of the Tripper control plane.
 *
 * Listens on UDP ctrlPort (dash :2000, DashSocket.kt:29) and rtpPort (:5000,
 * DashSocket.kt:31). Replies to the phone's source address on phoneRxPort
 * (:2002, DashSocket.kt:17-20 and :58). The real dash is 192.168.1.1; this
 * process binds whatever host the Android app chooses.
 *
 * Auth, DashAuth.kt:37-52:
 *   phone sends 08 04 (auth request) → we send 07 00 modulus and 07 03 exponent
 *   phone sends 08 00 with 128-byte RSA ciphertext → we decrypt and send 07 01 01
 */
class PanelEngine(
    private val bindHost: String = "0.0.0.0",
    requestedCtrlPort: Int = 2000,
    requestedRtpPort: Int = 5000,
    private val phoneRxPort: Int = 2002,
) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private var ctrlSocket: DatagramSocket? = null
    private var rtpSocket: DatagramSocket? = null
    private var ctrlThread: Thread? = null
    private var rtpThread: Thread? = null

    private val publicKey: RSAPublicKey
    private val privateKey: RSAPrivateKey
    private val modulusBytes: ByteArray
    private val exponentBytes: ByteArray

    @Volatile var link: PanelLink = PanelLink.WAITING
        private set
    @Volatile var ssid: String? = null
        private set
    @Volatile var hostname: String? = null
        private set
    @Volatile var sessionKey: ByteArray? = null
        private set
    @Volatile var nowPlaying: NowPlaying? = null
        private set
    @Volatile var caller: String? = null
        private set
    @Volatile var clock: String? = null
        private set
    @Volatile var phoneAddress: InetAddress? = null
        private set
    @Volatile var lastError: String? = null
        private set
    @Volatile var ignitionOn: Boolean = true
        private set
    @Volatile private var acceptLink: Boolean = true

    fun isLinkUp(): Boolean = acceptLink

    val packetsIn = AtomicLong()
    val packetsOut = AtomicLong()
    val videoFrames = AtomicLong()
    @Volatile var videoFps: Double = 0.0
        private set

    var onAccessUnit: ((ByteArray, Boolean) -> Unit)? = null
    var onState: ((PanelSnapshot) -> Unit)? = null

    val ctrlPort: Int get() = ctrlSocket?.localPort ?: -1
    val rtpPort: Int get() = rtpSocket?.localPort ?: -1

    private val log = ArrayDeque<String>()
    private val reassembler = RtpReassembler { nal, marker ->
        if (marker) {
            val n = videoFrames.incrementAndGet()
            val now = System.nanoTime()
            if (fpsWindowStart == 0L) fpsWindowStart = now
            fpsCount++
            val elapsed = now - fpsWindowStart
            if (elapsed >= 1_000_000_000L) {
                videoFps = fpsCount * 1_000_000_000.0 / elapsed
                fpsCount = 0
                fpsWindowStart = now
            }
            if (n == 1L) note("RX", "primeiro quadro de vídeo (${nal.size} B, marker)")
        }
        onAccessUnit?.invoke(nal, marker)
        if (marker && (nal.isNotEmpty() && (nal[0].toInt() and 0x1F) == 5)) {
            // DashSession.kt:297-301 expects 09 06 55 after an IDR.
            sendIncoming(Tlv(0x09, 0x06, byteArrayOf(0x55)))
        }
    }
    private var fpsWindowStart = 0L
    private var fpsCount = 0

    init {
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(1024)
        val pair = gen.generateKeyPair()
        publicKey = pair.public as RSAPublicKey
        privateKey = pair.private as RSAPrivateKey
        modulusBytes = unsigned(publicKey.modulus)
        exponentBytes = unsigned(publicKey.publicExponent)
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        ctrlSocket = DatagramSocket(null).also {
            it.reuseAddress = true
            it.bind(InetSocketAddress(bindHost, requestedCtrl()))
        }
        rtpSocket = DatagramSocket(null).also {
            it.reuseAddress = true
            it.bind(InetSocketAddress(bindHost, requestedRtp()))
        }
        link = if (!ignitionOn) PanelLink.IGNITION_OFF else if (!acceptLink) PanelLink.LINK_DOWN else PanelLink.WAITING
        note("SYS", "escutando controle :$ctrlPort e vídeo :$rtpPort, resposta para o celular na porta $phoneRxPort")
        ctrlThread = Thread({ ctrlLoop() }, "bancada-ctrl").also { it.isDaemon = true; it.start() }
        rtpThread = Thread({ rtpLoop() }, "bancada-rtp").also { it.isDaemon = true; it.start() }
        publish()
    }

    private fun requestedCtrl(): Int = fieldCtrl
    private fun requestedRtp(): Int = fieldRtp
    private val fieldCtrl = requestedCtrlPort
    private val fieldRtp = requestedRtpPort

    fun snapshot(): PanelSnapshot = PanelSnapshot(
        link = link,
        ssid = ssid,
        hostname = hostname,
        nowPlaying = nowPlaying,
        caller = caller,
        clock = clock,
        packetsIn = packetsIn.get(),
        packetsOut = packetsOut.get(),
        videoFrames = videoFrames.get(),
        videoFps = videoFps,
        lastError = lastError,
        ctrlPort = ctrlPort,
        rtpPort = rtpPort,
        phoneAddress = phoneAddress?.hostAddress,
    )

    fun recentLog(): List<String> = synchronized(log) { log.toList() }

    fun exportLog(): String = buildString {
        appendLine("Bancada Vinalayan — log do painel")
        appendLine("estado=$link ssid=$ssid host=$hostname telefone=${phoneAddress?.hostAddress}")
        appendLine("pacotes in=${packetsIn.get()} out=${packetsOut.get()} quadros=${videoFrames.get()} fps=$videoFps")
        recentLog().forEach { appendLine(it) }
    }

    fun setIgnition(on: Boolean) {
        ignitionOn = on
        if (!on) {
            link = PanelLink.IGNITION_OFF
            note("SYS", "ignição desligada — sem resposta")
        } else if (acceptLink) {
            link = if (sessionKey != null) PanelLink.AUTHENTICATED else PanelLink.WAITING
            note("SYS", "ignição ligada")
        }
        publish()
    }

    fun setLinkUp(up: Boolean) {
        acceptLink = up
        if (!up) {
            link = PanelLink.LINK_DOWN
            note("SYS", "perda de conexão simulada")
        } else if (ignitionOn) {
            link = if (sessionKey != null) PanelLink.AUTHENTICATED else PanelLink.WAITING
            note("SYS", "enlace restaurado")
        }
        publish()
    }

    /** Joystick. Vinalayan reads the last byte: DashSession.kt:312-316. */
    fun sendButton(code: Int) {
        sendIncoming(Tlv(0x09, 0x00, byteArrayOf((code and 0xFF).toByte())))
        note("TX", "joystick 09 00 code=0x${"%02X".format(code and 0xFF)}")
    }

    private fun ctrlLoop() {
        val buf = ByteArray(65535)
        val socket = ctrlSocket ?: return
        while (running.get()) {
            val pkt = DatagramPacket(buf, buf.size)
            try {
                socket.receive(pkt)
            } catch (_: Exception) {
                if (!running.get()) break
                continue
            }
            if (!accepting()) continue
            val data = pkt.data.copyOf(pkt.length)
            packetsIn.incrementAndGet()
            phoneAddress = pkt.address
            note("RX", "controle ${pkt.address.hostAddress}:${pkt.port} ${data.size}B ${data.toHex().take(180)}")
            handleControl(data)
            publish()
        }
    }

    private fun rtpLoop() {
        val buf = ByteArray(65535)
        val socket = rtpSocket ?: return
        while (running.get()) {
            val pkt = DatagramPacket(buf, buf.size)
            try {
                socket.receive(pkt)
            } catch (_: Exception) {
                if (!running.get()) break
                continue
            }
            if (!accepting()) continue
            val data = pkt.data.copyOf(pkt.length)
            packetsIn.incrementAndGet()
            if (phoneAddress == null) phoneAddress = pkt.address
            if (link == PanelLink.AUTHENTICATED) {
                link = PanelLink.PROJECTING
                note("SYS", "projetando — RTP recebido")
            }
            reassembler.push(data)
            publish()
        }
    }

    private fun accepting(): Boolean = ignitionOn && acceptLink && running.get()

    private fun handleControl(data: ByteArray) {
        val tlvs = K1G.parseOutgoing(data)
        if (tlvs.isEmpty()) {
            note("RX", "pacote sem TLV K1G")
            return
        }
        for (tlv in tlvs) {
            when {
                tlv.type == 0x08 && tlv.sub == 0x04 -> {
                    link = PanelLink.AUTHENTICATING
                    note("SYS", "autenticando — pedido 08 04 (DashCommands.kt:13)")
                    sendPubkey()
                }
                tlv.type == 0x08 && tlv.sub == 0x00 -> onSessionKey(tlv.value)
                tlv.type == 0x06 && tlv.sub == 0x0B -> {
                    hostname = tlv.value.dropLastWhile { it == 0.toByte() }.toByteArray().toString(Charsets.UTF_8)
                }
                tlv.type == 0x06 && tlv.sub == 0x05 && tlv.value.firstOrNull() == 0x55.toByte() -> {
                    link = PanelLink.PROJECTING
                    note("SYS", "projetando — 06 05 55 (DashCommands.kt:90)")
                }
                tlv.type == 0x06 && tlv.sub == 0x05 && tlv.value.firstOrNull() == 0xAA.toByte() -> {
                    if (link == PanelLink.PROJECTING) link = PanelLink.AUTHENTICATED
                    note("SYS", "projeção off — 06 05 AA (DashCommands.kt:92)")
                }
                tlv.type == 0x05 && tlv.sub == 0x56 && tlv.value.firstOrNull() == 0x55.toByte() -> {
                    if (link == PanelLink.AUTHENTICATED || link == PanelLink.PROJECTING) {
                        link = PanelLink.PROJECTING
                    }
                }
                tlv.type == 0x05 && tlv.sub == 0x0D -> nowPlaying = parseNowPlaying(tlv.value)
                tlv.type == 0x05 && tlv.sub == 0x22 -> {
                    val name = tlv.value.dropLastWhile { it == 0.toByte() }.toByteArray().toString(Charsets.UTF_8)
                    caller = name.ifBlank { null }
                }
                tlv.type == 0x06 && tlv.sub == 0x06 && tlv.value.size >= 3 -> {
                    clock = "%02d:%02d:%02d".format(
                        tlv.value[0].toInt() and 0xFF,
                        tlv.value[1].toInt() and 0xFF,
                        tlv.value[2].toInt() and 0xFF,
                    )
                }
            }
        }
    }

    private fun sendPubkey() {
        // Separate packets, as DashAuth.kt:25-26 allows.
        sendIncoming(Tlv(0x07, 0x00, modulusBytes))
        sendIncoming(Tlv(0x07, 0x03, exponentBytes))
        note("TX", "chave pública 07 00 (${modulusBytes.size}B) e 07 03")
    }

    private fun onSessionKey(ciphertext: ByteArray) {
        if (ciphertext.size != 128) {
            lastError = "q3c.d com ${ciphertext.size} bytes, esperado 128 (DashCommands.kt:17)"
            sendIncoming(Tlv(0x07, 0x01, byteArrayOf(0x00)))
            note("TX", "auth rejeitada — tamanho")
            return
        }
        val plain = try {
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            lastError = "RSA falhou: ${e.message}"
            sendIncoming(Tlv(0x07, 0x01, byteArrayOf(0x00)))
            note("TX", "auth rejeitada — RSA")
            return
        }
        if (plain.size < 32) {
            lastError = "payload RSA curto (${plain.size})"
            sendIncoming(Tlv(0x07, 0x01, byteArrayOf(0x00)))
            return
        }
        sessionKey = plain.copyOfRange(plain.size - 32, plain.size)
        ssid = plain.copyOfRange(0, plain.size - 32).toString(Charsets.UTF_8)
        link = PanelLink.AUTHENTICATED
        // DashAuth.kt:42-43 treats 07 01 value 0x01 as Confirmed.
        sendIncoming(Tlv(0x07, 0x01, byteArrayOf(0x01)))
        note("SYS", "autenticado — SSID '$ssid'")
    }

    private fun parseNowPlaying(value: ByteArray): NowPlaying {
        val parts = ArrayList<String>()
        var start = 0
        for (i in value.indices) {
            if (value[i] == 0.toByte()) {
                parts += value.copyOfRange(start, i).toString(Charsets.UTF_8)
                start = i + 1
            }
        }
        if (start < value.size) parts += value.copyOfRange(start, value.size).toString(Charsets.UTF_8)
        return NowPlaying(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" }, parts.getOrElse(2) { "" })
    }

    private fun sendIncoming(vararg tlvs: Tlv) {
        val socket = ctrlSocket ?: return
        val dest = phoneAddress ?: return
        if (!accepting()) return
        val bytes = K1G.buildIncoming(*tlvs)
        try {
            socket.send(DatagramPacket(bytes, bytes.size, dest, phoneRxPort))
            packetsOut.incrementAndGet()
            note("TX", "→ ${dest.hostAddress}:$phoneRxPort ${bytes.toHex().take(120)}")
        } catch (e: Exception) {
            lastError = e.message
        }
    }

    fun decryptTelemetry(ivAndCt: ByteArray): ByteArray? {
        val key = sessionKey ?: return null
        if (ivAndCt.size <= 16) return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                IvParameterSpec(ivAndCt.copyOfRange(0, 16)),
            )
            cipher.doFinal(ivAndCt.copyOfRange(16, ivAndCt.size))
        }.getOrNull()
    }

    private fun note(dir: String, message: String) {
        val line = "%tT  %-3s  %s".format(System.currentTimeMillis(), dir, message)
        synchronized(log) {
            log.addLast(line)
            while (log.size > 400) log.removeFirst()
        }
    }

    private fun publish() {
        onState?.invoke(snapshot())
    }

    override fun close() {
        running.set(false)
        runCatching { ctrlSocket?.close() }
        runCatching { rtpSocket?.close() }
        ctrlThread?.join(500)
        rtpThread?.join(500)
    }

    private fun unsigned(n: BigInteger): ByteArray {
        val raw = n.toByteArray()
        return if (raw.size > 1 && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
    }
}
