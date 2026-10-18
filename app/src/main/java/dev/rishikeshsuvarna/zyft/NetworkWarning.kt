package dev.rishikeshsuvarna.zyft

enum class NetworkWarning(val message: String) {
    NO_NETWORK("No network"),
    SIGN_IN_REQUIRED("Wi-Fi needs sign-in"),
}

/**
 * Deliberately ignores Android's "validated" flag: it relies on reaching a Google
 * endpoint that some networks block, which would show a false "no internet" warning.
 */
fun networkWarning(hasNetwork: Boolean, isCaptivePortal: Boolean): NetworkWarning? = when {
    !hasNetwork -> NetworkWarning.NO_NETWORK
    isCaptivePortal -> NetworkWarning.SIGN_IN_REQUIRED
    else -> null
}
