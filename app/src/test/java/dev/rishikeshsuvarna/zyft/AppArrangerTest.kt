package dev.rishikeshsuvarna.zyft

import org.junit.Assert.assertEquals
import org.junit.Test

class AppArrangerTest {

    private data class App(val pkg: String, val label: String)

    private val netflix = App("com.netflix.ninja", "Netflix")
    private val youtube = App("com.google.android.youtube.tv", "YouTube")
    private val prime = App("com.amazon.amazonvideo.livingroom", "Prime Video")
    private val browser = App("com.example.browser", "browser")
    private val weather = App("com.tcl.weather", "Weather")
    private val all = listOf(weather, youtube, browser, prime, netflix)

    private fun run(favorites: List<String>, hidden: Set<String> = emptySet()) =
        arrange(all, favorites, hidden, { it.pkg }, compareBy { it.label.lowercase() })

    @Test
    fun `favorites keep pin order and the rest are sorted case-insensitively`() {
        val result = run(listOf(youtube.pkg, netflix.pkg))

        assertEquals(listOf(youtube, netflix), result.favorites)
        assertEquals(listOf(browser, prime, weather), result.others)
    }

    @Test
    fun `pinned but uninstalled apps are skipped`() {
        val result = run(listOf("com.apple.atve.androidtv.appletv", netflix.pkg))

        assertEquals(listOf(netflix), result.favorites)
    }

    @Test
    fun `hidden apps appear nowhere, even if pinned`() {
        val result = run(listOf(netflix.pkg), hidden = setOf(netflix.pkg, weather.pkg))

        assertEquals(emptyList<App>(), result.favorites)
        assertEquals(listOf(browser, prime, youtube), result.others)
    }

    @Test
    fun `duplicate pins do not duplicate tiles`() {
        val result = run(listOf(netflix.pkg, netflix.pkg))

        assertEquals(listOf(netflix), result.favorites)
    }
}
