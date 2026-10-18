package dev.rishikeshsuvarna.zyft

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import java.io.File

/** What we remember about one app between launches; its artwork lives in a PNG next to the index. */
data class CachedApp(
    val packageName: String,
    val component: String,
    val label: String,
    val hasBanner: Boolean,
    val lastUpdateTime: Long,
)

private const val INDEX_VERSION = "zyft-app-cache-v1"
private const val FIELD_SEPARATOR = '\t'

/**
 * Index format: first line is the cache key (anything that changes how art and labels are
 * produced: locale, tile and icon size), then one tab-separated line per app.
 */
fun encodeAppIndex(key: String, apps: Collection<CachedApp>): String = buildString {
    append(INDEX_VERSION).append(FIELD_SEPARATOR).append(key).append('\n')
    apps.forEach { app ->
        // Labels are user-visible text from other apps; tabs/newlines would break the format.
        val label = app.label.replace(Regex("[\\t\\r\\n]"), " ")
        listOf(app.packageName, app.component, label, app.hasBanner, app.lastUpdateTime)
            .joinTo(this, FIELD_SEPARATOR.toString())
        append('\n')
    }
}

/** Entries by package, or empty when the file is from another version or [key]. */
fun decodeAppIndex(text: String, key: String): Map<String, CachedApp> {
    val lines = text.lineSequence().filter { it.isNotEmpty() }.toList()
    if (lines.firstOrNull() != "$INDEX_VERSION$FIELD_SEPARATOR$key") return emptyMap()
    return lines.drop(1).mapNotNull { line ->
        val f = line.split(FIELD_SEPARATOR)
        if (f.size != 5) return@mapNotNull null
        val updated = f[4].toLongOrNull() ?: return@mapNotNull null
        CachedApp(f[0], f[1], f[2], f[3].toBooleanStrictOrNull() ?: return@mapNotNull null, updated)
    }.associateBy { it.packageName }
}

/** A cached entry is reusable only for the same launcher activity of the same app install. */
fun CachedApp.isFreshFor(component: String, lastUpdateTime: Long): Boolean =
    this.component == component && this.lastUpdateTime == lastUpdateTime

/** Bounds that scale (w0 x h0) to cover (w x h), centred: the ImageView CENTER_CROP rule. */
fun centerCropBounds(w0: Int, h0: Int, w: Int, h: Int): IntArray {
    if (w0 <= 0 || h0 <= 0) return intArrayOf(0, 0, w, h)
    val scale = maxOf(w.toFloat() / w0, h.toFloat() / h0)
    val dw = (w0 * scale).toInt()
    val dh = (h0 * scale).toInt()
    val left = (w - dw) / 2
    val top = (h - dh) / 2
    return intArrayOf(left, top, left + dw, top + dh)
}

/** Files under the app's cache dir: the system may clear it any time, which just means a slow start. */
class AppArtCache(private val dir: File) {

    private val index = File(dir, "index.tsv")

    fun readIndex(key: String): Map<String, CachedApp> =
        runCatching { decodeAppIndex(index.readText(), key) }.getOrDefault(emptyMap())

    fun readArt(packageName: String): Bitmap? = artFile(packageName).takeIf { it.isFile }
        ?.let { BitmapFactory.decodeFile(it.path) }

    fun writeArt(packageName: String, bitmap: Bitmap) {
        dir.mkdirs()
        val tmp = File(dir, "$packageName.png.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        tmp.renameTo(artFile(packageName))
    }

    /** Replaces the index and drops art for apps that are no longer listed (uninstalled). */
    fun writeIndex(key: String, apps: Collection<CachedApp>) {
        dir.mkdirs()
        val tmp = File(dir, "index.tsv.tmp")
        tmp.writeText(encodeAppIndex(key, apps))
        tmp.renameTo(index)
        val keep = apps.map { artFile(it.packageName).name }.toSet()
        dir.listFiles { f -> f.name.endsWith(".png") && f.name !in keep }?.forEach(File::delete)
    }

    private fun artFile(packageName: String) = File(dir, "$packageName.png")

    private companion object {
        const val PNG_QUALITY = 100 // ignored for PNG (lossless), required by the API
    }
}

/** Renders any drawable (vector, adaptive, bitmap) at exactly the size the tile shows it. */
fun rasterize(drawable: Drawable, width: Int, height: Int, centerCrop: Boolean): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val bounds = if (centerCrop) centerCropBounds(drawable.intrinsicWidth, drawable.intrinsicHeight, width, height)
    else intArrayOf(0, 0, width, height)
    drawable.setBounds(bounds[0], bounds[1], bounds[2], bounds[3])
    drawable.draw(Canvas(bitmap))
    return bitmap
}
