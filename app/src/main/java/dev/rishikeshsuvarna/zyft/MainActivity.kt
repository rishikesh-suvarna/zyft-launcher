package dev.rishikeshsuvarna.zyft

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvContract
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.media.tv.TvInputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
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
    private lateinit var statusChip: LinearLayout
    private lateinit var statusText: TextView
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private lateinit var favoritesGrid: LinearLayout
    private lateinit var favoritesLabel: TextView
    private lateinit var appsLabel: TextView
    private lateinit var appsGrid: LinearLayout
    private lateinit var emptyFavorites: View
    private lateinit var metrics: GridMetrics

    private var apps: List<AppEntry> = emptyList()
    private var visibleFavorites: List<AppEntry> = emptyList()
    /** Package of the favorite being dragged in move mode, or null when not moving. */
    private var movingKey: String? = null
    /** Bumped on every load so a slow, stale load can't overwrite a newer one. */
    private var loadGeneration = 0

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = loadApps()
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            showNetworkWarning(networkWarning(hasNetwork = true, isCaptivePortal = capabilities.isCaptivePortal()))

        override fun onLost(network: Network) =
            showNetworkWarning(networkWarning(hasNetwork = false, isCaptivePortal = false))
    }

    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateClock()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LauncherPrefs(this)
        repository = AppRepository(packageManager, packageName)
        applyBackdrop()
        metrics = resources.displayMetrics.let { gridMetrics(it.widthPixels, it.density) }
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
        watchNetwork()
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
        connectivity.unregisterNetworkCallback(networkCallback)
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
        appsLabel.text = twoTone("Apps", sortMode.title)

        visibleFavorites = arranged.favorites
        fillGrid(favoritesGrid, arranged.favorites.mapIndexed { index, app ->
            appTile(app, number = (index + 1).takeIf { it <= MAX_NUMBERED_FAVORITES })
        })
        emptyFavorites.visibility = if (arranged.favorites.isEmpty()) View.VISIBLE else View.GONE
        favoritesLabel.text = if (movingKey != null) "Favorites  ·  Left / right to move, OK to finish" else "Favorites"

        fillGrid(appsGrid, arranged.others.map(::appTile) + systemTiles())

        val restored = focusedKey?.let { key ->
            allTiles().firstOrNull { tileKey(it) == key }
        }
        if (restored != null) restored.requestFocus() else focusFirstTile()
    }

    // endregion

    // region Layout

    private fun buildLayout(): View {
        statusText = label(R.dimen.type_badge, R.color.warning_text, medium = true)
        statusChip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(12), 0, dp(12), 0)
            background = roundedFill(R.color.warning_surface, R.dimen.radius_card, R.color.warning_stroke).apply {
                cornerRadius = dp(14).toFloat()
            }
            addView(View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(getColor(R.color.warning)) }
            }, LinearLayout.LayoutParams(dp(6), dp(6)).apply { marginEnd = dp(8) })
            addView(statusText)
        }
        date = label(R.dimen.type_date, R.color.text_secondary)
        clock = label(R.dimen.type_clock, R.color.text_primary, medium = true)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            addView(statusChip, LinearLayout.LayoutParams(WRAP_CONTENT, dp(28)).apply { marginEnd = dp(16) })
            addView(date, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(12) })
            addView(clock)
        }

        favoritesLabel = sectionLabel()
        favoritesGrid = grid()
        emptyFavorites = emptyFavoritesSlot()
        appsLabel = sectionLabel()
        appsGrid = grid()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            // Let the focused tile's ring and scale spill into the safe-area margin.
            clipToPadding = false
            setPadding(px(R.dimen.safe_x), px(R.dimen.safe_y), px(R.dimen.safe_x), px(R.dimen.safe_y))
            addView(header, LinearLayout.LayoutParams(MATCH_PARENT, px(R.dimen.header_height)))
            addView(favoritesLabel, sectionParams(top = px(R.dimen.favorites_top)))
            addView(emptyFavorites)
            addView(favoritesGrid)
            addView(appsLabel, sectionParams(top = px(R.dimen.section_top)))
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

    private fun sectionLabel() = label(R.dimen.type_section, R.color.text_primary, medium = true)

    private fun sectionParams(top: Int) = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
        topMargin = top
        bottomMargin = px(R.dimen.section_bottom)
    }

    private fun grid() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
    }

    /** Dashed, non-focusable placeholder that teaches pinning while there are no favorites. */
    private fun emptyFavoritesSlot() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
        setPadding(dp(24), 0, dp(24), 0)
        background = GradientDrawable().apply {
            cornerRadius = resources.getDimension(R.dimen.radius_tile)
            setStroke(dp(1.5f), getColor(R.color.stroke_empty), dp(6).toFloat(), dp(4).toFloat())
        }
        addView(label(R.dimen.type_list_item, R.color.text_primary, medium = true, text = "No favorites yet"))
        addView(label(R.dimen.type_body, R.color.text_secondary,
            text = "Hold OK on any app, then choose Add to favorites. Pinned apps get a number key.").apply {
            setPadding(0, dp(4), 0, 0)
        })
        layoutParams = LinearLayout.LayoutParams(
            minOf(px(R.dimen.empty_slot_max_width), resources.displayMetrics.widthPixels - 2 * px(R.dimen.safe_x)),
            dp(metrics.tileHeightDp),
        )
    }

    /** [number] badges a favorite with its remote shortcut key. */
    private fun appTile(app: AppEntry, number: Int? = null): View {
        val banner = app.banner
        val tile = if (banner != null) {
            TileView(this, TileView.Kind.BANNER).apply { setBanner(banner); caption = app.label }
        } else {
            TileView(this, TileView.Kind.APP).apply { setIconAndLabel(app.icon, app.label, APP_ICON_DP) }
        }
        number?.let(tile::setBadge)
        tile.tag = app
        tile.setOnClickListener { launch(app) }
        tile.setOnLongClickListener { showAppMenu(app); true }
        if (app.packageName == movingKey) tile.isMoving = true
        return tile
    }

    private fun systemTiles(): List<View> = listOf(
        utilityTile(R.drawable.ic_util_inputs, "Inputs", TILE_INPUTS, ::showInputs),
        utilityTile(R.drawable.ic_util_settings, "Settings", TILE_SETTINGS) { safeStart(Intent(Settings.ACTION_SETTINGS)) },
        utilityTile(R.drawable.ic_util_customize, "Customize", TILE_CUSTOMIZE, ::showCustomizeMenu),
    )

    private fun utilityTile(iconRes: Int, label: String, key: String, onClick: () -> Unit) =
        TileView(this, TileView.Kind.UTILITY).apply {
            setIconAndLabel(getDrawable(iconRes), label, UTILITY_ICON_DP)
            tag = key
            setOnClickListener { onClick() }
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

    /**
     * The callback only reports when a default network exists, so the "nothing at all"
     * state has to be read once up front.
     */
    private fun watchNetwork() {
        val current = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
        showNetworkWarning(networkWarning(current != null, current?.isCaptivePortal() == true))
        connectivity.registerDefaultNetworkCallback(networkCallback)
    }

    /** Network callbacks arrive on a binder thread. */
    private fun showNetworkWarning(warning: NetworkWarning?) = runOnUiThread {
        statusText.text = warning?.message.orEmpty()
        statusChip.visibility = if (warning == null) View.GONE else View.VISIBLE
    }

    private fun NetworkCapabilities.isCaptivePortal() =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)

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

    private fun systemSettingsPackage(): String? =
        packageManager.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)?.activityInfo?.packageName

    /** Stable identity used to keep focus on the same tile across re-renders. */
    private fun tileKey(view: View): String? = (view.tag as? AppEntry)?.packageName ?: view.tag as? String

    private fun focusFirstTile() {
        allTiles().firstOrNull()?.requestFocus()
    }

    private fun fillGrid(grid: LinearLayout, tiles: List<View>) {
        grid.removeAllViews()
        val width = dp(metrics.tileWidthDp)
        val height = dp(metrics.tileHeightDp)
        tiles.chunked(metrics.columns).forEachIndexed { rowIndex, rowTiles ->
            grid.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
                rowTiles.forEachIndexed { i, tile ->
                    addView(tile, LinearLayout.LayoutParams(width, height).apply {
                        if (i > 0) marginStart = px(R.dimen.grid_gap_col)
                    })
                }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                if (rowIndex > 0) topMargin = px(R.dimen.grid_gap_row)
            })
        }
    }

    private fun tilesIn(grid: LinearLayout): List<View> = (0 until grid.childCount).flatMap { r ->
        val row = grid.getChildAt(r) as LinearLayout
        (0 until row.childCount).map(row::getChildAt)
    }

    private fun allTiles(): List<View> = tilesIn(favoritesGrid) + tilesIn(appsGrid)

    private fun registerSystemReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    // endregion

    private companion object {
        const val APP_ICON_DP = 40
        const val UTILITY_ICON_DP = 28
        const val DATE_SKELETON = "EEEdMMM"

        // Prefixed so they can never collide with a package name.
        const val TILE_INPUTS = "system:inputs"
        const val TILE_SETTINGS = "system:settings"
        const val TILE_CUSTOMIZE = "system:customize"
    }
}
