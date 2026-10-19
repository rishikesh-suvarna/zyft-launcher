package dev.rishikeshsuvarna.zyft

import android.content.Context

class LauncherPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        if (!prefs.getBoolean(KEY_SEEDED, false)) {
            prefs.edit()
                .putString(KEY_FAVORITES, DEFAULT_FAVORITES.joinToString(SEPARATOR))
                .putBoolean(KEY_SEEDED, true)
                .apply()
        }
        // Versioned so existing installs get the list once, and a later "Show hidden apps"
        // isn't undone on the next start.
        if (!prefs.getBoolean(KEY_DEFAULT_HIDDEN_APPLIED, false)) {
            prefs.edit()
                .putStringSet(KEY_HIDDEN, hidden + DEFAULT_HIDDEN_PACKAGES)
                .putBoolean(KEY_DEFAULT_HIDDEN_APPLIED, true)
                .apply()
        }
    }

    val favorites: List<String>
        get() = prefs.getString(KEY_FAVORITES, "").orEmpty()
            .split(SEPARATOR)
            .filter { it.isNotBlank() }

    val hidden: Set<String>
        get() = prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty()

    fun isFavorite(pkg: String) = pkg in favorites

    fun toggleFavorite(pkg: String) {
        val updated = if (isFavorite(pkg)) favorites - pkg else favorites + pkg
        prefs.edit().putString(KEY_FAVORITES, updated.joinToString(SEPARATOR)).apply()
    }

    /** Moves a pinned app one on-screen slot; direction is -1 (left) or +1 (right). */
    fun moveFavorite(pkg: String, direction: Int, isVisible: (String) -> Boolean) {
        val updated = favorites.movedAmongVisible(pkg, direction, isVisible)
        prefs.edit().putString(KEY_FAVORITES, updated.joinToString(SEPARATOR)).apply()
    }

    /** Restores a saved order, e.g. when move mode is cancelled. */
    fun setFavorites(order: List<String>) {
        prefs.edit().putString(KEY_FAVORITES, order.joinToString(SEPARATOR)).apply()
    }

    fun hide(pkg: String) {
        // getStringSet's returned set must not be mutated in place, hence the copy.
        prefs.edit().putStringSet(KEY_HIDDEN, hidden + pkg).apply()
    }

    fun unhide(pkg: String) {
        prefs.edit().putStringSet(KEY_HIDDEN, hidden - pkg).apply()
    }

    fun isHidden(pkg: String) = pkg in hidden

    /** One-time welcome card (design screen 07). */
    var welcomeSeen: Boolean
        get() = prefs.getBoolean(KEY_WELCOME_SEEN, false)
        set(value) = prefs.edit().putBoolean(KEY_WELCOME_SEEN, value).apply()

    /** Customize > Show hidden apps: hidden apps appear in the grid so they can be unhidden. */
    var showHidden: Boolean
        get() = prefs.getBoolean(KEY_SHOW_HIDDEN, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_HIDDEN, value).apply()

    var sortMode: SortMode
        get() = enumOrDefault(prefs.getString(KEY_SORT, null), SortMode.ALPHABETICAL)
        set(value) = prefs.edit().putString(KEY_SORT, value.name).apply()

    var backdrop: Backdrop
        get() = enumOrDefault(prefs.getString(KEY_BACKDROP, null), Backdrop.MIDNIGHT)
        set(value) = prefs.edit().putString(KEY_BACKDROP, value.name).apply()

    fun recordLaunch(pkg: String, now: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putInt(KEY_LAUNCHES_PREFIX + pkg, usage(pkg).launches + 1)
            .putLong(KEY_LAST_LAUNCHED_PREFIX + pkg, now)
            .apply()
    }

    fun usage(pkg: String) = Usage(
        launches = prefs.getInt(KEY_LAUNCHES_PREFIX + pkg, 0),
        lastLaunchedAt = prefs.getLong(KEY_LAST_LAUNCHED_PREFIX + pkg, 0),
    )

    /** Package opened on the first launcher start of each boot, or null. See [shouldAutoLaunch]. */
    var autoLaunch: String?
        get() = prefs.getString(KEY_AUTO_LAUNCH, null)
        set(value) = prefs.edit().putString(KEY_AUTO_LAUNCH, value).apply()

    /** Boot count that auto-launch already ran (or was set up) in. */
    var autoLaunchHandledBoot: Int?
        get() = if (prefs.contains(KEY_AUTO_LAUNCH_BOOT)) prefs.getInt(KEY_AUTO_LAUNCH_BOOT, 0) else null
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_AUTO_LAUNCH_BOOT) else putInt(KEY_AUTO_LAUNCH_BOOT, value)
        }.apply()

    /** Every stored setting worth moving to another TV, for [encodeSettings]. */
    fun snapshot(): Map<String, Any?> = prefs.all - DEVICE_LOCAL_KEYS

    /**
     * Replaces all settings with a backup; synchronous so a restart right after sees them.
     * Device-local state stays: another TV's boot count means nothing here. [bootCount] marks
     * this boot as handled, so the launcher restart that follows doesn't open the startup app.
     */
    fun restore(entries: Map<String, Any>, bootCount: Int?): Boolean {
        val edit = prefs.edit().clear()
        val local = prefs.all.filterKeys { it in DEVICE_LOCAL_KEYS }
        (entries - DEVICE_LOCAL_KEYS + local).forEach { (key, value) ->
            when (value) {
                is String -> edit.putString(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Set<*> -> edit.putStringSet(key, value.map { it as String }.toSet())
                else -> throw IllegalArgumentException("unsupported value for $key: $value")
            }
        }
        bootCount?.let { edit.putInt(KEY_AUTO_LAUNCH_BOOT, it) }
        return edit.commit()
    }

    /** A renamed or removed enum constant must not crash the home screen. */
    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val PREFS_NAME = "launcher"
        const val KEY_FAVORITES = "favorites"
        const val KEY_HIDDEN = "hidden"
        const val KEY_SHOW_HIDDEN = "show_hidden"
        const val KEY_WELCOME_SEEN = "welcome_seen"
        const val KEY_SEEDED = "seeded"
        const val KEY_DEFAULT_HIDDEN_APPLIED = "default_hidden_v1"
        const val KEY_SORT = "sort"
        const val KEY_BACKDROP = "backdrop"
        const val KEY_LAUNCHES_PREFIX = "launches."
        const val KEY_LAST_LAUNCHED_PREFIX = "last_launched."
        const val KEY_AUTO_LAUNCH = "auto_launch"
        const val KEY_AUTO_LAUNCH_BOOT = "auto_launch_boot"
        val DEVICE_LOCAL_KEYS = setOf(KEY_AUTO_LAUNCH_BOOT)
        // Package names never contain commas, so this is a safe delimiter.
        const val SEPARATOR = ","

        // Only the ones actually installed will show up.
        val DEFAULT_FAVORITES = listOf(
            "com.netflix.ninja", // Netflix
            "com.amazon.amazonvideo.livingroom", // Prime Video
            "com.google.android.youtube.tv", // YouTube
            "com.apple.atve.androidtv.appletv", // Apple TV
            "in.startv.hotstar", // JioHotstar
        )
    }
}
