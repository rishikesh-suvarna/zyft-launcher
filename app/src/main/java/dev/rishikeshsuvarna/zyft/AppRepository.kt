package dev.rishikeshsuvarna.zyft

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable

class AppEntry(
    val packageName: String,
    val component: ComponentName,
    val label: String,
    /** 16:9 TV banner; null for apps that only ship a square icon. */
    val banner: Drawable?,
    val icon: Drawable,
) {
    val launchIntent: Intent
        get() = Intent(Intent.ACTION_MAIN)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}

class AppRepository(private val pm: PackageManager, private val selfPackage: String) {

    /** Slow (icon decoding): call off the main thread. */
    fun loadLaunchableApps(): List<AppEntry> {
        // Leanback entries win: they are the TV-optimised activity and carry a banner.
        val resolved = LinkedHashMap<String, ResolveInfo>()
        for (info in query(Intent.CATEGORY_LEANBACK_LAUNCHER) + query(Intent.CATEGORY_LAUNCHER)) {
            val pkg = info.activityInfo.packageName
            if (pkg != selfPackage) resolved.putIfAbsent(pkg, info)
        }
        return resolved.values.map { info ->
            val activity = info.activityInfo
            AppEntry(
                packageName = activity.packageName,
                component = ComponentName(activity.packageName, activity.name),
                label = info.loadLabel(pm).toString(),
                banner = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm),
                icon = info.loadIcon(pm),
            )
        }
    }

    private fun query(category: String): List<ResolveInfo> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
}
