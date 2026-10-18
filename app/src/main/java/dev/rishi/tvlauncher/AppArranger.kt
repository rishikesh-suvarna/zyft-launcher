package dev.rishi.tvlauncher

data class Arranged<T>(val favorites: List<T>, val others: List<T>)

/**
 * Pure ordering logic, kept free of Android types so it can be unit tested on the JVM.
 * Favorites keep the user's pin order; everything else follows [order].
 */
fun <T> arrange(
    items: List<T>,
    favoriteKeys: List<String>,
    hiddenKeys: Set<String>,
    key: (T) -> String,
    order: Comparator<T>,
): Arranged<T> {
    val visible = items.filterNot { key(it) in hiddenKeys }
    val byKey = visible.associateBy(key)
    // Pinned apps that were uninstalled are skipped rather than removed, so reinstalling restores the pin.
    val favorites = favoriteKeys.distinct().mapNotNull { byKey[it] }
    val favoriteSet = favorites.map(key).toSet()
    val others = visible
        .filterNot { key(it) in favoriteSet }
        .sortedWith(order)
    return Arranged(favorites, others)
}
