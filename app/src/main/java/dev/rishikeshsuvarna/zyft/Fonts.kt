package dev.rishikeshsuvarna.zyft

import android.content.Context
import android.graphics.Typeface
import android.util.Log

/**
 * Google Sans (SIL OFL 1.1, see assets/fonts/GoogleSans-OFL.txt), subset to Latin by
 * scripts/fonts/build_fonts.py. Glyphs outside the subset fall back to the system font.
 *
 * Loaded from assets because res/font needs API 26 (or AndroidX) and we support API 24.
 * Until [load] runs, or if it fails, the system sans-serif is used so the home screen
 * always renders.
 */
object Fonts {
    var regular: Typeface = Typeface.DEFAULT
        private set
    var medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        try {
            regular = Typeface.createFromAsset(context.assets, "fonts/GoogleSans-Regular.ttf")
            medium = Typeface.createFromAsset(context.assets, "fonts/GoogleSans-Medium.ttf")
        } catch (e: RuntimeException) {
            // A missing asset is a packaging bug; keep the launcher usable with system fonts.
            Log.e("Zyft", "Google Sans failed to load, using system fonts", e)
        }
    }
}
