package dev.rishikeshsuvarna.zyft

import android.media.tv.TvInputManager

enum class InputState { CONNECTED, STANDBY, DISCONNECTED }

fun inputStateOf(tvInputManagerState: Int): InputState = when (tvInputManagerState) {
    TvInputManager.INPUT_STATE_CONNECTED -> InputState.CONNECTED
    TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> InputState.STANDBY
    else -> InputState.DISCONNECTED
}

/**
 * Second line of an Inputs row: the user's custom name for the port (if any) and whether
 * a device is sending a signal, e.g. "Media box · signal" or "No signal".
 */
fun inputSubtitle(label: String, customLabel: String?, state: InputState): String {
    val status = when (state) {
        InputState.CONNECTED -> "signal"
        InputState.STANDBY -> "standby"
        InputState.DISCONNECTED -> "no signal"
    }
    val custom = customLabel?.trim()?.takeIf { it.isNotEmpty() && !it.equals(label, ignoreCase = true) }
    val text = if (custom != null) "$custom · $status" else status
    return text.replaceFirstChar { it.uppercaseChar() }
}
