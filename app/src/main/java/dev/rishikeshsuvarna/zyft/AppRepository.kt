package dev.rishikeshsuvarna.zyft

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.File
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class AppEntry(
    val packageName: String,
    val component: ComponentName,
    val label: String,
    /** The tile artwork: the 16:9 TV banner when [hasBanner], else the square launcher icon. */
    val art: Drawable,
    val hasBanner: Boolean,
) {
    val launchIntent: Intent
        get() = Intent(Intent.ACTION_MAIN)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}

/**
 * Finds launchable apps. Opening each app's resources for its label and artwork is what makes
 * a cold start slow on TV hardware (~20-30 ms per app on a TCL 32ES568), so results are kept in
 * [AppArtCache], pre-rendered at tile size, and reused while the app's install is unchanged.
 * Cache misses (first run, updated apps) load in parallel.
 */
class AppRepository(
    private val pm: PackageManager,
    private val resources: Resources,
    private val selfPackage: String,
    cacheDir: File,
    private val bannerWidthPx: Int,
    private val bannerHeightPx: Int,
    private val iconSizePx: Int,
) {
    private val cache = AppArtCache(File(cacheDir, "apps"))

    /** Anything that changes how labels and art are produced; a mismatch discards the cache. */
    private val cacheKey: String
        get() = "${Locale.getDefault().toLanguageTag()}|${bannerWidthPx}x$bannerHeightPx|$iconSizePx"

    /** Blocking (package manager and disk): call off the main thread. */
    fun loadLaunchableApps(): List<AppEntry> {
        val resolved = Perf.section("Zyft.query") { queryLaunchable() }
        val key = cacheKey
        val index = cache.readIndex(key)
        if (resolved.isEmpty()) return emptyList()
        // Per-app work is independent binder calls and image decodes: spread it over the cores.
        val pool = Executors.newFixedThreadPool(minOf(resolved.size, Runtime.getRuntime().availableProcessors()))
        try {
            val hits = Perf.section("Zyft.cacheHits") {
                pool.invokeAll(resolved.map { info -> Callable { fromCache(info, index) } }).map { it.get() }
            }
            val misses = resolved.indices.filter { hits[it] == null }
            if (misses.isEmpty()) {
                val all = hits.map { it!! }
                // Uninstalled apps are gone from the query but still in the index: prune their art.
                if (index.keys != all.map { it.second.packageName }.toSet()) cache.writeIndex(key, all.map { it.second })
                return all.map { it.first }
            }

            val loaded = Perf.section("Zyft.cacheMisses x${misses.size}") {
                pool.invokeAll(misses.map { i -> Callable { i to loadLive(resolved[i]) } }).map { it.get() }.toMap()
            }
            val results = resolved.indices.mapNotNull { hits[it] ?: loaded[it] }
            cache.writeIndex(key, results.map { it.second })
            return results.map { it.first }
        } finally {
            pool.shutdown()
        }
    }

    /** The cached entry when it is still valid for this install of the app and its art is on disk. */
    private fun fromCache(info: ResolveInfo, index: Map<String, CachedApp>): Pair<AppEntry, CachedApp>? {
        val pkg = info.activityInfo.packageName
        val component = info.component()
        val updated = lastUpdateTime(pkg) ?: return null
        val cached = index[pkg]?.takeIf { it.isFreshFor(component.flattenToString(), updated) } ?: return null
        val bitmap = cache.readArt(pkg) ?: return null
        return AppEntry(pkg, component, cached.label, BitmapDrawable(resources, bitmap), cached.hasBanner) to cached
    }

    /** Reads label and art from the app itself, renders the art at tile size and caches it. */
    private fun loadLive(info: ResolveInfo): Pair<AppEntry, CachedApp>? {
        val activity = info.activityInfo
        val pkg = activity.packageName
        val updated = lastUpdateTime(pkg) ?: return null
        val label = info.loadLabel(pm).toString()
        val banner = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm)
        // Icons are only shown for apps without a banner, so don't decode them otherwise.
        val bitmap = if (banner != null) {
            rasterize(banner, bannerWidthPx, bannerHeightPx, centerCrop = true)
        } else {
            rasterize(info.loadIcon(pm), iconSizePx, iconSizePx, centerCrop = false)
        }
        runCatching { cache.writeArt(pkg, bitmap) }
        val component = info.component()
        val record = CachedApp(pkg, component.flattenToString(), label, banner != null, updated)
        return AppEntry(pkg, component, label, BitmapDrawable(resources, bitmap), banner != null) to record
    }

    private fun queryLaunchable(): List<ResolveInfo> {
        // Leanback entries win: they are the TV-optimised activity and carry a banner.
        val resolved = LinkedHashMap<String, ResolveInfo>()
        for (info in query(Intent.CATEGORY_LEANBACK_LAUNCHER) + query(Intent.CATEGORY_LAUNCHER)) {
            val pkg = info.activityInfo.packageName
            if (pkg != selfPackage) resolved.putIfAbsent(pkg, info)
        }
        return resolved.values.toList()
    }

    /** Null when the app vanished between the query and now (uninstalled mid-load). */
    private fun lastUpdateTime(pkg: String): Long? = try {
        pm.getPackageInfo(pkg, 0).lastUpdateTime
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun ResolveInfo.component() = ComponentName(activityInfo.packageName, activityInfo.name)

    private fun query(category: String): List<ResolveInfo> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
}
