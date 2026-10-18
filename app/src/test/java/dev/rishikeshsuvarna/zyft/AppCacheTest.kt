package dev.rishikeshsuvarna.zyft

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCacheTest {

    private val key = "en-IN|272x153|53"
    private val netflix = CachedApp("com.netflix.ninja", "com.netflix.ninja/.MainActivity", "Netflix", true, 1700000000000)
    private val files = CachedApp("com.example.files", "com.example.files/.Main", "File Manager", false, 42)

    @Test
    fun `index round-trips`() {
        val decoded = decodeAppIndex(encodeAppIndex(key, listOf(netflix, files)), key)

        assertEquals(mapOf(netflix.packageName to netflix, files.packageName to files), decoded)
    }

    @Test
    fun `an index written for another locale or tile size is ignored`() {
        val text = encodeAppIndex(key, listOf(netflix))

        assertTrue(decodeAppIndex(text, "hi-IN|272x153|53").isEmpty())
        assertTrue(decodeAppIndex(text, "en-IN|213x120|53").isEmpty())
    }

    @Test
    fun `tabs and newlines in labels cannot corrupt the index`() {
        val weird = netflix.copy(label = "Net\tflix\nPlus")

        val decoded = decodeAppIndex(encodeAppIndex(key, listOf(weird, files)), key)

        assertEquals("Net flix Plus", decoded.getValue(netflix.packageName).label)
        assertEquals(files, decoded.getValue(files.packageName))
    }

    @Test
    fun `garbage and truncated files decode to an empty or partial cache, never a crash`() {
        assertTrue(decodeAppIndex("", key).isEmpty())
        assertTrue(decodeAppIndex("not an index", key).isEmpty())
        val truncated = encodeAppIndex(key, listOf(netflix)) + "com.broken\tonly-two-fields\n"
        assertEquals(setOf(netflix.packageName), decodeAppIndex(truncated, key).keys)
    }

    @Test
    fun `an app update or a different launcher activity invalidates the entry`() {
        assertTrue(netflix.isFreshFor(netflix.component, netflix.lastUpdateTime))
        assertFalse(netflix.isFreshFor(netflix.component, netflix.lastUpdateTime + 1))
        assertFalse(netflix.isFreshFor("com.netflix.ninja/.OtherActivity", netflix.lastUpdateTime))
    }

    @Test
    fun `center crop covers the target and stays centred`() {
        // 320x180 banner into a 272x153 tile: same 16:9 ratio, just scaled down.
        assertArrayEquals(intArrayOf(0, 0, 272, 153), centerCropBounds(320, 180, 272, 153))
        // A 4:3 image into 16:9 is cropped top and bottom.
        assertArrayEquals(intArrayOf(0, -15, 160, 105), centerCropBounds(400, 300, 160, 90))
        // Drawables without an intrinsic size just fill the target.
        assertArrayEquals(intArrayOf(0, 0, 160, 90), centerCropBounds(-1, -1, 160, 90))
    }
}
