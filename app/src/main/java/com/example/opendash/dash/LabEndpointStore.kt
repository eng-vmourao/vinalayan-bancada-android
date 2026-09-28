package com.example.opendash.dash

import android.content.Context

/**
 * Lab-only addresses. Ignored unless BuildConfig.LAB_MODE is true.
 * Same-device mode binds the phone's control socket to an ephemeral port so it
 * does not fight the panel for UDP 2000 (DashSocket.kt:53).
 */
object LabEndpointStore {
    private const val PREFS = "vinalayan_lab_endpoints"
    private const val KEY_MODE = "mode"
    private const val KEY_HOST = "host"
    private const val KEY_SSID = "ssid"

    const val MODE_SAME = "same"
    const val MODE_TWO = "two"

    data class LabTarget(
        val mode: String,
        val host: String,
        val ssid: String,
    ) {
        fun endpoints(): DashEndpoints {
            val same = mode != MODE_TWO
            return DashEndpoints(
                dashIp = if (same) "127.0.0.1" else host.ifBlank { "127.0.0.1" },
                broadcast = if (same) "127.0.0.1" else host.ifBlank { "127.0.0.1" },
                ctrlPort = DashSocket.CTRL_PORT,
                rxPort = DashSocket.RX_PORT,
                rtpPort = DashSocket.RTP_PORT,
                txBindPort = if (same) 0 else DashSocket.CTRL_PORT,
                unicastControl = true,
            )
        }
    }

    fun read(context: Context): LabTarget {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return LabTarget(
            mode = p.getString(KEY_MODE, MODE_SAME) ?: MODE_SAME,
            host = p.getString(KEY_HOST, "") ?: "",
            ssid = p.getString(KEY_SSID, "LAB_DASH")?.ifBlank { "LAB_DASH" } ?: "LAB_DASH",
        )
    }

    fun write(context: Context, target: LabTarget) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, target.mode)
            .putString(KEY_HOST, target.host.trim())
            .putString(KEY_SSID, target.ssid.trim().ifBlank { "LAB_DASH" })
            .apply()
    }
}
