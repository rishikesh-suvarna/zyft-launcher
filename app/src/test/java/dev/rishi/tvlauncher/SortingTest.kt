package dev.rishi.tvlauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class SortingTest {

    private data class App(val label: String, val usage: Usage = Usage())

    private val netflix = App("Netflix", Usage(launches = 9, lastLaunchedAt = 100))
    private val youtube = App("YouTube", Usage(launches = 3, lastLaunchedAt = 300))
    private val prime = App("Prime Video", Usage(launches = 3, lastLaunchedAt = 200))
    private val appleTv = App("Apple TV")
    private val browser = App("browser")
    private val all = listOf(youtube, browser, netflix, appleTv, prime)

    private fun sorted(mode: SortMode) = all.sortedWith(comparatorFor(mode, { it.label }, { it.usage }))

    @Test
    fun `A-Z ignores case`() {
        assertEquals(listOf(appleTv, browser, netflix, prime, youtube), sorted(SortMode.ALPHABETICAL))
    }

    @Test
    fun `most used ranks by launch count, ties and unused apps fall back to A-Z`() {
        assertEquals(listOf(netflix, prime, youtube, appleTv, browser), sorted(SortMode.MOST_USED))
    }

    @Test
    fun `recently used puts the latest launch first, never-opened apps last in A-Z`() {
        assertEquals(listOf(youtube, prime, netflix, appleTv, browser), sorted(SortMode.RECENT))
    }
}
