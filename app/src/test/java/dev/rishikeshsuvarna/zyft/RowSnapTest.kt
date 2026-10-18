package dev.rishikeshsuvarna.zyft

import org.junit.Assert.assertEquals
import org.junit.Test

class RowSnapTest {

    // 720p-like numbers in px: 720 viewport, rows 153 tall + 37 gap, sticky header zone 120.
    private fun target(rowTop: Int, contentHeight: Int = 2000) = rowSnapScrollTarget(
        rowTop = rowTop,
        rowBottom = rowTop + 153,
        viewportHeight = 720,
        contentHeight = contentHeight,
        stickyZone = 120,
        rowPitch = 190,
        bottomReserve = 60,
    )

    @Test
    fun `rows that fit on the first screen keep the page at the top`() {
        assertEquals(0, target(rowTop = 150))
        assertEquals(0, target(rowTop = 507)) // bottom 660 + reserve 60 == 720
    }

    @Test
    fun `a deeper row is held as the second visible row under the sticky header`() {
        assertEquals(1000 - 120 - 190, target(rowTop = 1000))
    }

    @Test
    fun `never scrolls past the end of the content`() {
        assertEquals(2000 - 720, target(rowTop = 1900))
    }

    @Test
    fun `short content never scrolls`() {
        assertEquals(0, target(rowTop = 600, contentHeight = 700))
    }
}
