package dev.rishikeshsuvarna.zyft

enum class SortMode(val title: String) {
    ALPHABETICAL("A\u2013Z"),
    MOST_USED("Most used"),
    RECENT("Recently used"),
}

data class Usage(val launches: Int = 0, val lastLaunchedAt: Long = 0)

/** Usage-based modes fall back to A-Z so never-opened apps still have a stable order. */
fun <T> comparatorFor(mode: SortMode, label: (T) -> String, usage: (T) -> Usage): Comparator<T> {
    val byLabel = compareBy<T> { label(it).lowercase() }
    return when (mode) {
        SortMode.ALPHABETICAL -> byLabel
        SortMode.MOST_USED -> compareByDescending<T> { usage(it).launches }.then(byLabel)
        SortMode.RECENT -> compareByDescending<T> { usage(it).lastLaunchedAt }.then(byLabel)
    }
}
