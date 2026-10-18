package dev.rishikeshsuvarna.zyft

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GridMetricsTest {

    private val delta = 0.01f

    @Test
    fun `720p TV gets 4 columns of 204 dp tiles`() {
        val m = gridMetrics(screenWidthPx = 1280, density = 4f / 3f)

        assertEquals(4, m.columns)
        assertEquals(204f, m.tileWidthDp, delta)
        assertEquals(114.75f, m.tileHeightDp, delta)
    }

    @Test
    fun `1080p TV gets 5 columns of 160x90 dp tiles`() {
        val m = gridMetrics(screenWidthPx = 1920, density = 2f)

        assertEquals(5, m.columns)
        assertEquals(160f, m.tileWidthDp, delta)
        assertEquals(90f, m.tileHeightDp, delta)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a zero-size screen`() {
        gridMetrics(screenWidthPx = 0, density = 1f)
    }

    @Test
    fun `css 180 deg gradient runs straight top to bottom`() {
        assertArrayEquals(floatArrayOf(50f, 0f, 50f, 40f), cssGradientLine(180f, 100f, 40f), delta)
    }

    @Test
    fun `css 90 deg gradient runs left to right`() {
        assertArrayEquals(floatArrayOf(0f, 20f, 100f, 20f), cssGradientLine(90f, 100f, 40f), delta)
    }

    @Test
    fun `css 160 deg gradient leans right as it goes down and covers the corners`() {
        val (x0, y0, x1, y1) = cssGradientLine(160f, 960f, 540f)

        assert(x1 > x0 && y1 > y0) { "expected down-right, got ($x0,$y0)->($x1,$y1)" }
        assert(y0 < 0f && y1 > 540f) { "line must extend past the edges so corners hit the end colours" }
    }
}
