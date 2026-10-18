package dev.rishikeshsuvarna.zyft

import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderTest {

    private val allVisible = { _: String -> true }

    @Test
    fun `moves one slot right and left`() {
        val list = listOf("a", "b", "c")

        assertEquals(listOf("b", "a", "c"), list.movedAmongVisible("a", 1, allVisible))
        assertEquals(listOf("a", "c", "b"), list.movedAmongVisible("c", -1, allVisible))
    }

    @Test
    fun `steps over entries that are not on screen`() {
        val list = listOf("a", "hidden", "b", "c")
        val visible = { it: String -> it != "hidden" }

        assertEquals(listOf("hidden", "b", "a", "c"), list.movedAmongVisible("a", 1, visible))
        assertEquals(listOf("b", "a", "hidden", "c"), list.movedAmongVisible("b", -1, visible))
    }

    @Test
    fun `no-op at the edges and for unknown items`() {
        val list = listOf("a", "b", "hidden")
        val visible = { it: String -> it != "hidden" }

        assertEquals(list, list.movedAmongVisible("a", -1, visible))
        assertEquals(list, list.movedAmongVisible("b", 1, visible))
        assertEquals(list, list.movedAmongVisible("zzz", 1, visible))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects jumps bigger than one slot`() {
        listOf("a", "b").movedAmongVisible("a", 2, allVisible)
    }
}
