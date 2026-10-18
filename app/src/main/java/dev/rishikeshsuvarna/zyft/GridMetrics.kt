package dev.rishikeshsuvarna.zyft

data class GridMetrics(val columns: Int, val tileWidthDp: Float, val tileHeightDp: Float)

private const val WIDE_SCREEN_MIN_PX = 1600
private const val COLUMNS_NARROW = 4
private const val COLUMNS_WIDE = 5
private const val SAFE_X_DP = 48f
private const val GAP_DP = 16f
private const val TILE_ASPECT = 9f / 16f

/**
 * Design rule: 4 columns below 1600 px wide, else 5; tiles share the safe-area width
 * minus the 16 dp gaps and keep 16:9. Gives 204x115 dp at 720p and 160x90 dp at 1080p.
 */
fun gridMetrics(screenWidthPx: Int, density: Float): GridMetrics {
    require(screenWidthPx > 0 && density > 0f) { "invalid screen: $screenWidthPx px @ $density" }
    val columns = if (screenWidthPx < WIDE_SCREEN_MIN_PX) COLUMNS_NARROW else COLUMNS_WIDE
    val contentDp = screenWidthPx / density - 2 * SAFE_X_DP
    val tileWidth = (contentDp - GAP_DP * (columns - 1)) / columns
    return GridMetrics(columns, tileWidth, tileWidth * TILE_ASPECT)
}
