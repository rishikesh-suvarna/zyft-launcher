package dev.rishikeshsuvarna.zyft

/**
 * Moves [item] one visible slot in [direction] (-1 left, +1 right).
 * Entries that aren't on screen (pinned but hidden or uninstalled) are stepped over,
 * otherwise a move could swap with an invisible app and look like it did nothing.
 * Returns [list] unchanged when the item is missing or already at that edge.
 */
fun <T> List<T>.movedAmongVisible(item: T, direction: Int, isVisible: (T) -> Boolean): List<T> {
    require(direction == -1 || direction == 1) { "direction must be -1 or 1, was $direction" }
    val from = indexOf(item)
    if (from < 0) return this
    var target = from + direction
    while (target in indices && !isVisible(this[target])) target += direction
    if (target !in indices) return this
    return toMutableList().apply {
        removeAt(from)
        add(target, item)
    }
}
