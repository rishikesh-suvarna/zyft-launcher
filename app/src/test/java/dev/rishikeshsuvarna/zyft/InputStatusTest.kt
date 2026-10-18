package dev.rishikeshsuvarna.zyft

import android.media.tv.TvInputManager
import org.junit.Assert.assertEquals
import org.junit.Test

class InputStatusTest {

    @Test
    fun `custom name comes first, then the signal state`() {
        assertEquals("Media box · signal", inputSubtitle("HDMI 2", "Media box", InputState.CONNECTED))
    }

    @Test
    fun `without a custom name the state stands alone, capitalised`() {
        assertEquals("No signal", inputSubtitle("HDMI 3", null, InputState.DISCONNECTED))
        assertEquals("Standby", inputSubtitle("HDMI 1", "  ", InputState.STANDBY))
    }

    @Test
    fun `a custom name equal to the port name is not repeated`() {
        assertEquals("Signal", inputSubtitle("HDMI 1", "hdmi 1", InputState.CONNECTED))
    }

    @Test
    fun `framework states map to our enum, unknown means no signal`() {
        assertEquals(InputState.CONNECTED, inputStateOf(TvInputManager.INPUT_STATE_CONNECTED))
        assertEquals(InputState.STANDBY, inputStateOf(TvInputManager.INPUT_STATE_CONNECTED_STANDBY))
        assertEquals(InputState.DISCONNECTED, inputStateOf(TvInputManager.INPUT_STATE_DISCONNECTED))
        assertEquals(InputState.DISCONNECTED, inputStateOf(-1))
    }
}
