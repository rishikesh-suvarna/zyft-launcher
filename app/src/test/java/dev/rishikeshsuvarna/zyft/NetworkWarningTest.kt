package dev.rishikeshsuvarna.zyft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkWarningTest {

    @Test
    fun `no warning on a normal connection`() {
        assertNull(networkWarning(hasNetwork = true, isCaptivePortal = false))
    }

    @Test
    fun `warns when there is no connection at all`() {
        assertEquals(NetworkWarning.NO_NETWORK, networkWarning(hasNetwork = false, isCaptivePortal = false))
    }

    @Test
    fun `warns when a captive portal needs sign-in`() {
        assertEquals(NetworkWarning.SIGN_IN_REQUIRED, networkWarning(hasNetwork = true, isCaptivePortal = true))
    }
}
