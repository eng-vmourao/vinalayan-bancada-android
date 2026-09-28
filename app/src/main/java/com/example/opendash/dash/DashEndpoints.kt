package com.example.opendash.dash

/**
 * Production endpoints are the Tripper defaults from DashSocket.kt
 * (192.168.1.1, broadcast 192.168.1.255, UDP 2000/2002/5000).
 * Only the lab flavor passes a non-production instance. local/play/mapboxTest
 * keep [PRODUCTION], so the motorcycle path does not change.
 */
data class DashEndpoints(
    val dashIp: String = DashSocket.DASH_IP,
    val broadcast: String = DashSocket.BROADCAST,
    val ctrlPort: Int = DashSocket.CTRL_PORT,
    val rxPort: Int = DashSocket.RX_PORT,
    val rtpPort: Int = DashSocket.RTP_PORT,
    /** Local port the phone binds to send control. 2000 on the bike. 0 = ephemeral, for same-device lab. */
    val txBindPort: Int = DashSocket.CTRL_PORT,
    /** Lab sends unicast to [dashIp]. The bike always broadcasts. */
    val unicastControl: Boolean = false,
) {
    companion object {
        val PRODUCTION = DashEndpoints()
    }
}
