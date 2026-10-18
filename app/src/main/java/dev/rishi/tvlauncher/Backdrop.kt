package dev.rishi.tvlauncher

import android.graphics.drawable.GradientDrawable

/**
 * Gradients instead of images: drawn by the GPU with no bitmap in memory,
 * which matters on a TV with ~1.5 GB RAM shared with the streaming apps.
 * All stay dark enough for white text.
 */
enum class Backdrop(val title: String, private val from: Long, private val to: Long) {
    MIDNIGHT("Midnight", 0xFF0B1026, 0xFF241A4F),
    OCEAN("Ocean", 0xFF04141F, 0xFF0B3B4C),
    SUNSET("Sunset", 0xFF170B1C, 0xFF4E1D2E),
    FOREST("Forest", 0xFF07140F, 0xFF173A2C),
    GRAPHITE("Graphite", 0xFF111317, 0xFF2B2F37),
    PLAIN("Plain", 0xFF0E0F12, 0xFF0E0F12);

    fun drawable() = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(from.toInt(), to.toInt()),
    )
}
