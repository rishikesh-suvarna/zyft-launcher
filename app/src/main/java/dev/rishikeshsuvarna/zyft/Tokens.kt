package dev.rishikeshsuvarna.zyft

/** Motion tokens: transforms and alpha only, so every animation stays on the GPU. */
object Motion {
    const val FOCUS_IN_MS = 120L
    const val FOCUS_OUT_MS = 100L
    const val PRESS_MS = 100L
    const val MOVE_MS = 150L
    const val SHEET_MS = 150L
    const val SCROLL_MS = 150L
    const val SCROLL_INDICATOR_HIDE_DELAY_MS = 600L
}

object Scale {
    const val FOCUSED = 1.06f
    const val PRESSED = 0.98f
    const val MOVING = 1.12f
    const val SWATCH_FOCUSED = 1.06f
    const val DIMMED_ALPHA = 0.4f
}
