package dev.rishikeshsuvarna.zyft

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLaunchTest {

    private val netflix = "com.netflix.ninja"

    @Test
    fun `opens the chosen app once per boot`() {
        assertTrue(shouldAutoLaunch(netflix, bootCount = 43, handledBoot = 42))
        assertFalse(shouldAutoLaunch(netflix, bootCount = 43, handledBoot = 43))
    }

    @Test
    fun `nothing chosen means nothing opens`() {
        assertFalse(shouldAutoLaunch(null, bootCount = 43, handledBoot = 42))
    }

    @Test
    fun `devices without a boot count never auto-launch, rather than on every restart`() {
        assertFalse(shouldAutoLaunch(netflix, bootCount = null, handledBoot = null))
    }
}
