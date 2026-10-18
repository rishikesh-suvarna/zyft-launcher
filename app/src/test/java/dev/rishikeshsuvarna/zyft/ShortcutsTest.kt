package dev.rishikeshsuvarna.zyft

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutsTest {

    @Test
    fun `keys 1 to 9 map to the first nine favorite slots`() {
        assertEquals(0, favoriteSlotForKey(KeyEvent.KEYCODE_1))
        assertEquals(8, favoriteSlotForKey(KeyEvent.KEYCODE_9))
    }

    @Test
    fun `numpad digits work like remote digits`() {
        assertEquals(2, favoriteSlotForKey(KeyEvent.KEYCODE_NUMPAD_3))
    }

    @Test
    fun `zero and non-digit keys are not shortcuts`() {
        assertNull(favoriteSlotForKey(KeyEvent.KEYCODE_0))
        assertNull(favoriteSlotForKey(KeyEvent.KEYCODE_DPAD_CENTER))
    }
}
