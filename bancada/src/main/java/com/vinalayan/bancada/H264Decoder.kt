package com.vinalayan.bancada

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

/**
 * Decodes the Annex-B NALs reassembled from the phone's RTP
 * (RtpPacketizer.kt, NalProcessor.kt). SPS/PPS are cached the same way
 * the phone caches them before an IDR. Not exercised by the JVM test.
 */
class H264Decoder {
    private val lock = Any()
    private var surface: Surface? = null
    private var codec: MediaCodec? = null
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null
    private var started = false
    private var ptsUs = 0L
    val error = AtomicReference<String?>(null)
    val framesOut = java.util.concurrent.atomic.AtomicLong()

    fun attach(surface: Surface) {
        synchronized(lock) {
            this.surface = surface
            releaseCodecLocked()
        }
    }

    fun detach() {
        synchronized(lock) {
            surface = null
            releaseCodecLocked()
        }
    }

    fun onNal(nal: ByteArray, marker: Boolean) {
        if (nal.isEmpty()) return
        synchronized(lock) {
            when (nal[0].toInt() and 0x1F) {
                7 -> {
                    sps = nal
                    releaseCodecLocked()
                }
                8 -> {
                    pps = nal
                    releaseCodecLocked()
                }
                else -> {
                    if (!ensureStartedLocked()) return
                    queueLocked(nal, marker)
                }
            }
        }
    }

    private fun ensureStartedLocked(): Boolean {
        if (started) return true
        val s = sps ?: return false
        val p = pps ?: return false
        val out = surface ?: return false
        if (!out.isValid) return false
        return try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 800, 480)
            format.setByteBuffer("csd-0", ByteBuffer.wrap(byteArrayOf(0, 0, 0, 1) + s))
            format.setByteBuffer("csd-1", ByteBuffer.wrap(byteArrayOf(0, 0, 0, 1) + p))
            val c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            c.configure(format, out, null, 0)
            c.start()
            codec = c
            started = true
            error.set(null)
            true
        } catch (e: Exception) {
            error.set(e.message ?: e.javaClass.simpleName)
            releaseCodecLocked()
            false
        }
    }

    private fun queueLocked(nal: ByteArray, marker: Boolean) {
        val c = codec ?: return
        try {
            val inIndex = c.dequeueInputBuffer(10_000)
            if (inIndex >= 0) {
                val buf = c.getInputBuffer(inIndex) ?: return
                buf.clear()
                val annex = byteArrayOf(0, 0, 0, 1) + nal
                buf.put(annex)
                ptsUs += 250_000
                c.queueInputBuffer(inIndex, 0, annex.size, ptsUs, if (marker) 0 else 0)
            }
            val info = MediaCodec.BufferInfo()
            while (true) {
                val outIndex = c.dequeueOutputBuffer(info, 0)
                if (outIndex >= 0) {
                    c.releaseOutputBuffer(outIndex, true)
                    framesOut.incrementAndGet()
                } else break
            }
        } catch (e: Exception) {
            error.set(e.message ?: e.javaClass.simpleName)
            releaseCodecLocked()
        }
    }

    private fun releaseCodecLocked() {
        started = false
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
    }

    fun release() {
        synchronized(lock) { releaseCodecLocked() }
    }
}
