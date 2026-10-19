package dev.rishikeshsuvarna.zyft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SettingsBackupTest {

    private val settings = mapOf<String, Any>(
        "favorites" to "com.netflix.ninja,com.google.android.youtube.tv",
        "hidden" to setOf("com.tcl.ui_mediaCenter", "com.tcl.tvweishi"),
        "show_hidden" to false,
        "launches.com.netflix.ninja" to 12,
        "last_launched.com.netflix.ninja" to 1760000000000L,
        "sort" to "RECENT",
    )

    @Test
    fun `settings round-trip with their types`() {
        assertEquals(settings, decodeSettings(encodeSettings(settings)))
    }

    @Test
    fun `the file is readable text, one setting per line`() {
        val text = encodeSettings(mapOf("sort" to "RECENT", "hidden" to setOf("b.pkg", "a.pkg")))

        assertEquals("zyft-settings-v1\nset\thidden\ta.pkg,b.pkg\ns\tsort\tRECENT\n", text)
    }

    @Test
    fun `an empty set survives`() {
        assertEquals(mapOf("hidden" to emptySet<String>()), decodeSettings(encodeSettings(mapOf("hidden" to emptySet<String>()))))
    }

    @Test
    fun `files from another version or with any bad line are rejected whole`() {
        assertThrows(IllegalArgumentException::class.java) { decodeSettings("") }
        assertThrows(IllegalArgumentException::class.java) { decodeSettings("zyft-settings-v2\ns\tsort\tRECENT\n") }
        assertThrows(IllegalArgumentException::class.java) { decodeSettings("$SETTINGS_VERSION\ni\tlaunches.x\tmany\n") }
        assertThrows(IllegalArgumentException::class.java) { decodeSettings("$SETTINGS_VERSION\nf\tscale\t1.5\n") }
        assertThrows(IllegalArgumentException::class.java) { decodeSettings("$SETTINGS_VERSION\ns\tonly-two-fields\n") }
    }

    @Test
    fun `hand-edited files with CRLF line endings still load`() {
        assertEquals(mapOf("sort" to "RECENT"), decodeSettings("$SETTINGS_VERSION\r\ns\tsort\tRECENT\r\n"))
    }

    @Test
    fun `values that would break the format are refused on export`() {
        assertThrows(IllegalArgumentException::class.java) { encodeSettings(mapOf("label" to "a\tb")) }
        assertThrows(IllegalArgumentException::class.java) { encodeSettings(mapOf("scale" to 1.5f)) }
    }
}
