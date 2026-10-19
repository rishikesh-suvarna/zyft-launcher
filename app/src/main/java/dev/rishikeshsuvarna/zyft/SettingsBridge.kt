package dev.rishikeshsuvarna.zyft

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64

/**
 * adb's way in and out of the launcher settings (scripts/settings.sh), since release builds
 * aren't debuggable and `run-as` can't read them. The manifest guards it with the DUMP
 * permission, which the adb shell holds and ordinary apps can't get.
 *
 * Data travels base64-encoded in the broadcast (result data out, an extra in) because
 * `am broadcast` output and shell quoting would mangle tabs and newlines.
 */
class SettingsBridge : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = LauncherPrefs(context)
        val result = runCatching {
            when (intent.action) {
                ACTION_EXPORT -> encodeSettings(prefs.snapshot()).toByteArray().let { Base64.encodeToString(it, Base64.NO_WRAP) }
                ACTION_IMPORT -> {
                    val data = requireNotNull(intent.getStringExtra(EXTRA_DATA)) { "missing --es $EXTRA_DATA" }
                    val settings = decodeSettings(String(Base64.decode(data, Base64.DEFAULT)))
                    check(prefs.restore(settings, currentBootCount(context.contentResolver))) { "could not save settings" }
                    "restored ${settings.size} settings"
                }
                else -> throw IllegalArgumentException("unknown action ${intent.action}")
            }
        }
        resultCode = if (result.isSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED
        resultData = result.getOrElse { "error: ${it.message}" }
    }

    companion object {
        const val ACTION_EXPORT = "dev.rishikeshsuvarna.zyft.EXPORT_SETTINGS"
        const val ACTION_IMPORT = "dev.rishikeshsuvarna.zyft.IMPORT_SETTINGS"
        const val EXTRA_DATA = "data"
    }
}
