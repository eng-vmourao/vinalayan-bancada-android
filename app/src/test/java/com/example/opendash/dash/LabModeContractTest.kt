package com.example.opendash.dash

import com.example.opendash.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Runs on every flavor. local/play/mapboxTest must keep LAB_MODE false and the Tripper addresses. */
class LabModeContractTest {
    @Test
    fun motorcycleConstantsAndLabFlag() {
        assertEquals("192.168.1.1", DashSocket.DASH_IP)
        assertEquals("192.168.1.255", DashSocket.BROADCAST)
        assertEquals(2000, DashSocket.CTRL_PORT)
        assertEquals(2002, DashSocket.RX_PORT)
        assertEquals(5000, DashSocket.RTP_PORT)
        assertEquals(DashSocket.DASH_IP, DashEndpoints.PRODUCTION.dashIp)
        assertEquals(DashSocket.BROADCAST, DashEndpoints.PRODUCTION.broadcast)
        assertFalse(DashEndpoints.PRODUCTION.unicastControl)
        assertEquals(DashSocket.CTRL_PORT, DashEndpoints.PRODUCTION.txBindPort)
        assertEquals(BuildConfig.FLAVOR == "lab", BuildConfig.LAB_MODE)
    }
}
