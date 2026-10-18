package dev.rishi.tvlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var prefs: LauncherPrefs
    private lateinit var repository: AppRepository
    private val loader: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var scroll: ScrollView
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var favoritesRow: LinearLayout
    private lateinit var favoritesLabel: TextView
    private lateinit var appsLabel: TextView
    private lateinit var appsGrid: LinearLayout

    private var apps: List<AppEntry> = emptyList()
    private var visibleFavorites: List<AppEntry> = emptyList()
    /** Package of the favorite being dragged in move mode, or null when not moving. */
    private var movingKey: String? = null
    /** Bumped on every load so a slow, stale load can't overwrite a newer one. */
    private var loadGeneration = 0

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = loadApps()
    }

    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateClock()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LauncherPrefs(this)
        repository = AppRepository(packageManager, packageName)
        applyBackdrop()
        setContentView(buildLayout())

        registerSystemReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        loadApps()
    }

    override fun onStart() {
        super.onStart()
        updateClock()
        registerSystemReceiver(clockReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        })
    }

    /** Back from an app: usage-based orders may have changed. */
    override fun onRestart() {
        super.onRestart()
        if (prefs.sortMode != SortMode.ALPHABETICAL && apps.isNotEmpty()) render()
    }

    override fun onPause() {
        // Leaving the screen mid-move (e.g. Home or an incoming input switch) just drops the tile.
        if (movingKey != null) endMove()
        super.onPause()
    }

    override fun onStop() {
        unregisterReceiver(clockReceiver)
        super.onStop()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        loader.shutdownNow()
        super.onDestroy()
    }

    /** Home pressed while already home: jump back to the top. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        scroll.scrollTo(0, 0)
        focusFirstTile()
    }

    /**
     * Move mode needs the keys before the focused tile sees them: OK would otherwise
     * launch the app and left/right would move focus instead of the tile.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val moving = movingKey ?: return super.dispatchKeyEvent(event)
        val isDown = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> if (isDown) {
                val direction = if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                val visible = visibleFavorites.map { it.packageName }.toSet()
                prefs.moveFavorite(moving, direction) { it in visible }
                render()
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK ->
                if (isDown) endMove()
            // Up/down would leave the row mid-move; swallow everything else too.
        }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        // A home screen has nowhere to go back to.
        KeyEvent.KEYCODE_BACK -> true
        in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9,
        in KeyEvent.KEYCODE_NUMPAD_1..KeyEvent.KEYCODE_NUMPAD_9 -> {
            favoriteSlotForKey(keyCode)?.let(visibleFavorites::getOrNull)?.let(::launch)
            true
        }
        KeyEvent.KEYCODE_MENU -> {
            (currentFocus?.tag as? AppEntry)?.let(::showAppMenu)
            true
        }
        else -> super.onKeyDown(keyCode, event)
    }

    // region Loading

    private fun loadApps() {
        val generation = ++loadGeneration
        loader.execute {
            val loaded = repository.loadLaunchableApps()
            runOnUiThread {
                if (generation != loadGeneration || isDestroyed) return@runOnUiThread
                apps = loaded
                render()
            }
        }
    }

    private fun render() {
        val focusedKey = currentFocus?.let(::tileKey)
        // Our Settings tile replaces the system Settings app, so don't show it twice.
        val hidden = prefs.hidden + listOfNotNull(systemSettingsPackage())
        val sortMode = prefs.sortMode
        val order = comparatorFor<AppEntry>(sortMode, { it.label }, { prefs.usage(it.packageName) })
        val arranged = arrange(apps, prefs.favorites, hidden, { it.packageName }, order)
        appsLabel.text = "Apps  ·  ${sortMode.title}"

        favoritesRow.removeAllViews()
        visibleFavorites = arranged.favorites
        arranged.favorites.forEachIndexed { index, app ->
            val number = (index + 1).takeIf { it <= MAX_NUMBERED_FAVORITES }
            favoritesRow.addView(appTile(app, number))
        }
        favoritesLabel.visibility = if (arranged.favorites.isEmpty()) View.GONE else View.VISIBLE
        favoritesLabel.text = if (movingKey != null) "Favorites  ·  Left / right to move, OK to finish" else "Favorites"

        val gridTiles = arranged.others.map(::appTile) + systemTiles()
        appsGrid.removeAllViews()
        gridTiles.chunked(gridColumns()).forEach { rowTiles ->
            appsGrid.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                rowTiles.forEach(::addView)
            })
        }

        val restored = focusedKey?.let { key ->
            allTiles().firstOrNull { tileKey(it) == key }
        }
        if (restored != null) restored.requestFocus() else focusFirstTile()
    }

    // endregion

    // region Layout

    private fun buildLayout(): View {
        clock = text(sizeSp = 22f, color = R.color.text_primary)
        date = text(sizeSp = 16f, color = R.color.text_secondary).apply {
            setPadding(0, 0, dp(14), 0)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
            addView(date)
            addView(clock)
        }

        favoritesLabel = sectionLabel("Favorites")
        favoritesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
        }
        val favoritesScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            clipChildren = false
            addView(favoritesRow)
        }

        appsGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            // Let the focused tile's scale-up spill into the screen margin instead of being cut off.
            clipToPadding = false
            val h = dimen(R.dimen.screen_padding_h)
            val v = dimen(R.dimen.screen_padding_v)
            setPadding(h, v, h, v)
            addView(header)
            addView(favoritesLabel)
            addView(favoritesScroller)
            appsLabel = sectionLabel("Apps")
            addView(appsLabel)
            addView(appsGrid)
        }

        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            clipChildren = false
            addView(content, MATCH_PARENT, WRAP_CONTENT)
        }
        return scroll
    }

    /** [number] badges a favorite with its remote shortcut key. */
    private fun appTile(app: AppEntry, number: Int? = null): View {
        val content = app.banner?.let(::bannerView) ?: iconWithLabel(app.icon, app.label)
        val tile = tile(content, tag = app, onClick = { launch(app) }, onLongClick = { showAppMenu(app) })
        number?.let { (tile as FrameLayout).addView(numberBadge(it)) }
        if (app.packageName == movingKey) tile.setBackgroundResource(R.drawable.tile_moving)
        return tile
    }

    private fun systemTiles(): List<View> = listOf(
        tile(
            iconWithLabel(getDrawable(android.R.drawable.ic_menu_view), "Inputs"),
            tag = TILE_INPUTS,
            onClick = ::showInputs,
        ),
        tile(
            iconWithLabel(getDrawable(android.R.drawable.ic_menu_preferences), "Settings"),
            tag = TILE_SETTINGS,
            onClick = { safeStart(Intent(Settings.ACTION_SETTINGS)) },
        ),
        tile(
            iconWithLabel(getDrawable(android.R.drawable.ic_menu_edit), "Customize"),
            tag = TILE_CUSTOMIZE,
            onClick = ::showCustomizeMenu,
        ),
    )

    private fun tile(
        content: View,
        tag: Any? = null,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null,
    ): View = FrameLayout(this).apply {
        this.tag = tag
        isFocusable = true
        isClickable = true
        setBackgroundResource(R.drawable.tile_background)
        clipToOutline = true
        val pad = dp(3)
        setPadding(pad, pad, pad, pad)
        addView(content, MATCH_PARENT, MATCH_PARENT)
        layoutParams = LinearLayout.LayoutParams(dimen(R.dimen.tile_width), dimen(R.dimen.tile_height)).apply {
            val gap = dimen(R.dimen.tile_gap)
            setMargins(0, gap / 2, gap, gap / 2)
        }
        setOnClickListener { onClick() }
        onLongClick?.let { handler -> setOnLongClickListener { handler(); true } }
        setOnFocusChangeListener { view, hasFocus ->
            val scale = if (hasFocus) FOCUSED_SCALE else 1f
            view.animate().scaleX(scale).scaleY(scale).setDuration(FOCUS_ANIM_MS).start()
            view.elevation = if (hasFocus) dp(8).toFloat() else 0f
        }
    }

    private fun numberBadge(number: Int) = text(sizeSp = 11f, color = R.color.text_primary).apply {
        text = number.toString()
        gravity = Gravity.CENTER
        setBackgroundResource(R.drawable.number_badge)
        layoutParams = FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP or Gravity.START).apply {
            setMargins(dp(4), dp(4), 0, 0)
        }
    }

    private fun bannerView(banner: Drawable) = ImageView(this).apply {
        setImageDrawable(banner)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private fun iconWithLabel(icon: Drawable?, label: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(ImageView(context).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(dp(40), dp(40)))
        addView(text(sizeSp = 13f, color = R.color.text_primary).apply {
            text = label
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), 0)
        })
    }

    private fun sectionLabel(label: String) = text(sizeSp = 15f, color = R.color.text_secondary).apply {
        text = label
        setPadding(0, dp(18), 0, dp(6))
    }

    private fun text(sizeSp: Float, color: Int) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(getColor(color))
    }

    // endregion

    // region Actions

    private fun launch(app: AppEntry) {
        prefs.recordLaunch(app.packageName)
        safeStart(app.launchIntent)
    }

    private fun showAppMenu(app: AppEntry) {
        val isFavorite = prefs.isFavorite(app.packageName)
        val actions = buildList<Pair<String, () -> Unit>> {
            if (isFavorite) {
                if (visibleFavorites.size > 1) add("Move" to { movingKey = app.packageName })
                add("Remove from favorites" to { prefs.toggleFavorite(app.packageName) })
            } else {
                add("Add to favorites" to { prefs.toggleFavorite(app.packageName) })
            }
            add("Hide" to { prefs.hide(app.packageName) })
            add("App info" to {
                safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
            })
        }
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which ->
                actions[which].second()
                render()
            }
            .show()
    }

    private fun endMove() {
        movingKey = null
        render()
    }

    private fun showCustomizeMenu() {
        val hiddenCount = prefs.hidden.size
        val actions = buildList<Pair<String, () -> Unit>> {
            add("Sort apps: ${prefs.sortMode.title}" to ::showSortPicker)
            add("Background: ${prefs.backdrop.title}" to ::showBackdropPicker)
            if (hiddenCount > 0) add("Show hidden apps ($hiddenCount)" to { prefs.unhideAll(); render() })
        }
        AlertDialog.Builder(this)
            .setTitle("Customize")
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .show()
    }

    private fun showSortPicker() {
        val modes = SortMode.entries
        AlertDialog.Builder(this)
            .setTitle("Sort apps")
            .setSingleChoiceItems(modes.map { it.title }.toTypedArray(), prefs.sortMode.ordinal) { dialog, which ->
                prefs.sortMode = modes[which]
                render()
                dialog.dismiss()
            }
            .show()
    }

    private fun showBackdropPicker() {
        val backdrops = Backdrop.entries
        AlertDialog.Builder(this)
            .setTitle("Background")
            .setSingleChoiceItems(backdrops.map { it.title }.toTypedArray(), prefs.backdrop.ordinal) { dialog, which ->
                prefs.backdrop = backdrops[which]
                applyBackdrop()
                dialog.dismiss()
            }
            .show()
    }

    /** HDMI / AV inputs, launched through the system's TV input passthrough. */
    private fun showInputs() {
        val inputs = getSystemService(TvInputManager::class.java)
            ?.tvInputList
            ?.filter { it.isPassthroughInput }
            .orEmpty()
        if (inputs.isEmpty()) {
            toast("No external inputs found")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Inputs")
            .setItems(inputs.map { it.loadLabel(this).toString() }.toTypedArray()) { _, which ->
                safeStart(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputs[which].id)))
            }
            .show()
    }

    private fun safeStart(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            toast("Can't open this on this TV")
        } catch (e: SecurityException) {
            toast("Not allowed: ${e.message}")
        }
    }

    // endregion

    // region Helpers

    private fun applyBackdrop() {
        window.setBackgroundDrawable(prefs.backdrop.drawable())
    }

    private fun updateClock() {
        val now = Date()
        clock.text = DateFormat.getTimeFormat(this).format(now)
        // Locale-aware short form, e.g. "Sat, 18 Oct" or "Sat, Oct 18".
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), DATE_SKELETON)
        date.text = DateFormat.format(pattern, now)
    }

    /** Fits as many tiles as the screen allows instead of assuming a density. */
    private fun gridColumns(): Int {
        val usable = resources.displayMetrics.widthPixels - 2 * dimen(R.dimen.screen_padding_h)
        val perTile = dimen(R.dimen.tile_width) + dimen(R.dimen.tile_gap)
        return (usable / perTile).coerceAtLeast(1)
    }

    private fun systemSettingsPackage(): String? =
        packageManager.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)?.activityInfo?.packageName

    /** Stable identity used to keep focus on the same tile across re-renders. */
    private fun tileKey(view: View): String? = (view.tag as? AppEntry)?.packageName ?: view.tag as? String

    private fun focusFirstTile() {
        allTiles().firstOrNull()?.requestFocus()
    }

    private fun allTiles(): List<View> {
        val gridTiles = (0 until appsGrid.childCount).flatMap { r ->
            val row = appsGrid.getChildAt(r) as LinearLayout
            (0 until row.childCount).map(row::getChildAt)
        }
        return (0 until favoritesRow.childCount).map(favoritesRow::getChildAt) + gridTiles
    }

    private fun registerSystemReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun dimen(id: Int) = resources.getDimensionPixelSize(id)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    // endregion

    private companion object {
        const val FOCUSED_SCALE = 1.08f
        const val FOCUS_ANIM_MS = 120L
        const val DATE_SKELETON = "EEEdMMM"

        // Prefixed so they can never collide with a package name.
        const val TILE_INPUTS = "system:inputs"
        const val TILE_SETTINGS = "system:settings"
        const val TILE_CUSTOMIZE = "system:customize"
    }
}
