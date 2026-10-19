package dev.rishikeshsuvarna.zyft

import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * "Open at startup": the launcher is the home app, so the system starts it on every boot; the
 * first time it starts in a new boot it opens the chosen app. Comparing boot counts (instead of
 * listening for BOOT_COMPLETED) needs no permission or receiver, and a launcher process that is
 * restarted later in the same boot (low memory, a crash) doesn't reopen the app.
 */
fun shouldAutoLaunch(target: String?, bootCount: Int?, handledBoot: Int?): Boolean =
    target != null && bootCount != null && bootCount != handledBoot

/** Null on devices that don't keep the count (it's AOSP since Android 7, so rare). */
fun currentBootCount(resolver: ContentResolver): Int? = try {
    Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT)
} catch (e: Settings.SettingNotFoundException) {
    null
}

/** The TV-optimised activity when the app has one, like the home grid opens. */
fun PackageManager.launchIntentFor(pkg: String): Intent? =
    getLeanbackLaunchIntentForPackage(pkg) ?: getLaunchIntentForPackage(pkg)
