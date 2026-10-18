package dev.rishi.tvlauncher

import android.view.KeyEvent

/** Remote keys 1-9 (or numpad 1-9) map to favorite slots 0-8; anything else is not a shortcut. */
fun favoriteSlotForKey(keyCode: Int): Int? = when (keyCode) {
    in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> keyCode - KeyEvent.KEYCODE_1
    in KeyEvent.KEYCODE_NUMPAD_1..KeyEvent.KEYCODE_NUMPAD_9 -> keyCode - KeyEvent.KEYCODE_NUMPAD_1
    else -> null
}

const val MAX_NUMBERED_FAVORITES = 9
