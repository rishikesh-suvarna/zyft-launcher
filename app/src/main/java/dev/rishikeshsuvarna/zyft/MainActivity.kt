package dev.rishikeshsuvarna.zyft

import android.app.Activity
import android.animation.ObjectAnimator
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
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
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Date
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class MainActivity : Activity() {

    private lateinit var prefs: LauncherPrefs
    private lateinit var repository: AppRepository
    private val loader: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var root: FrameLayout
    private lateinit var scroll: ZyftScrollView
    private lateinit var content: LinearLayout
    private lateinit var stickyHeader: BackdropSliceView
    private lateinit var stickyLabel: TextView
    private lateinit var stickyClock: TextView
    private lateinit var bottomFade: BackdropSliceView
    private lateinit var scrollTrack: FrameLayout
    private lateinit var scrollThumb: View
    private var scrollAnimator: ObjectAnimator? = null
    private var welcomeCard: View? = null
    private var activeSheet: SideSheet? = null
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
    /** Favorites order when move mode started, restored if the move is cancelled with Back. */
    private var orderBeforeMove: List<String> = emptyList()
    private lateinit var header: View
    private lateinit var moveHint: View
    /** Bumped on every load so a slow, stale load can't overwrite a newer one. */
    private var loadGeneration = 0
    /** Set once the first real grid is on screen; drives reportFullyDrawn(). */
    private var reportedFullyDrawn = false
    /** Generation of the load currently on screen, so the startup fast path isn't rendered twice. */
    private var shownGeneration = 0

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            loadApps()
        }
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
        Perf.milestone("onCreate")
        super.onCreate(savedInstanceState)
        prefs = LauncherPrefs(this)
        metrics = resources.displayMetrics.let { gridMetrics(it.widthPixels, it.density) }
        repository = AppRepository(
            pm = packageManager,
            resources = resources,
            selfPackage = packageName,
            cacheDir = cacheDir,
            bannerWidthPx = dp(metrics.tileWidthDp),
            bannerHeightPx = dp(metrics.tileHeightDp),
            iconSizePx = dp(APP_ICON_DP),
        )
        // Start reading apps now so it overlaps fonts and layout.
        val initialLoad = loadApps()
        Perf.section("Zyft.fonts") { Fonts.load(this) }
        setContentView(Perf.section("Zyft.buildLayout") { buildLayout() })
        applyBackdrop()
        showInitialAppsIfReady(initialLoad)
        if (Perf.enabled) root.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
            private var frames = 0
            override fun onDraw() {
                // First frame, and the first frame drawn after the tiles were rendered.
                if (frames == 0) Perf.milestone("firstDraw")
                if (apps.isNotEmpty() && frames >= 0) { Perf.milestone("tilesDrawn"); frames = -1; return }
                if (frames >= 0) frames++
            }
        })
        // Row-snapped scrolling (screen 08): follow focus ourselves instead of ScrollView's default.
        root.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (!rendering && newFocus is TileView && isInHome(newFocus)) onTileFocused(newFocus)
        }

        registerSystemReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
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
        if (activeSheet != null || welcomeCard != null) return
        jumpToTop()
    }

    /**
     * Move mode needs the keys before the focused tile sees them: OK would otherwise
     * launch the app and left/right would move focus instead of the tile.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        activeSheet?.let { sheet -> return sheet.interceptKey(event) || super.dispatchKeyEvent(event) }
        if (welcomeCard != null && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN) dismissWelcome()
            return true
        }
        val moving = movingKey ?: return super.dispatchKeyEvent(event)
        val isDown = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> if (isDown) {
                val direction = if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                val visible = visibleFavorites.map { it.packageName }.toSet()
                val before = favoriteTilePositions()
                prefs.moveFavorite(moving, direction) { it in visible }
                render()
                animateSwaps(before)
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (isDown) endMove()
            KeyEvent.KEYCODE_BACK -> if (isDown) {
                prefs.setFavorites(orderBeforeMove)
                endMove()
            }
            // Up/down would leave the row mid-move; swallow everything else too.
        }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when {
        // Number keys and Menu belong to the home grid, not to an open sheet.
        activeSheet != null -> super.onKeyDown(keyCode, event)
        else -> onHomeKeyDown(keyCode, event)
    }

    private fun onHomeKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        // A home screen has nowhere to go back to; Back jumps to the top instead (screen 08).
        KeyEvent.KEYCODE_BACK -> {
            if (scroll.scrollY > 0) jumpToTop()
            true
        }
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

    /** Reloads apps in the background (startup and package changes); the newest load wins. */
    private fun loadApps(): Future<List<AppEntry>> {
        val generation = ++loadGeneration
        val load = loader.submit(Callable { Perf.section("Zyft.loadApps") { repository.loadLaunchableApps() } })
        loader.execute {
            val loaded = runCatching { load.get() }.getOrNull() ?: return@execute
            runOnUiThread { showApps(generation, loaded) }
        }
        return load
    }

    /**
     * Startup: with a warm cache the app list is ready ~100 ms after onCreate starts, so wait for
     * it briefly and render the tiles into the very first frame instead of drawing an empty grid
     * and laying everything out twice. A cache miss (first run, app updates) just times out and
     * the background path above delivers the tiles a moment later.
     */
    private fun showInitialAppsIfReady(load: Future<List<AppEntry>>) {
        val loaded = try {
            load.get(FIRST_FRAME_WAIT_MS, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            Perf.log("app list not ready for the first frame; rendering when it arrives")
            return
        }
        showApps(loadGeneration, loaded)
    }

    private fun showApps(generation: Int, loaded: List<AppEntry>) {
        // Stale (a newer load started), already shown (startup fast path), or activity gone.
        if (generation != loadGeneration || isDestroyed || generation == shownGeneration) return
        shownGeneration = generation
        apps = loaded
        Perf.milestone("render")
        Perf.section("Zyft.render") { render() }
        if (!reportedFullyDrawn) {
            reportedFullyDrawn = true
            // Logged by the system as "Fully drawn": time until real tiles are on screen.
            root.post { reportFullyDrawn() }
        }
    }

    /**
     * Rebuilding the grid briefly removes the focused tile, so focus lands on some other tile
     * (often far below) before it's restored, and the new tiles aren't laid out yet. Scrolling
     * on those focus changes made the page jump, e.g. on every move in move mode. So focus
     * changes are ignored while rendering and the scroll is settled once, after layout.
     */
    private var rendering = false

    private fun render() {
        rendering = true
        try {
            renderGrid()
        } finally {
            rendering = false
        }
        scrollToFocusAfterLayout()
    }

    private fun scrollToFocusAfterLayout() {
        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                root.viewTreeObserver.removeOnPreDrawListener(this)
                (currentFocus as? TileView)?.takeIf(::isInHome)?.let(::onTileFocused)
                return true
            }
        })
    }

    private fun renderGrid() {
        val focusedKey = currentFocus?.let(::tileKey)
        // Our Settings tile replaces the system Settings app, so don't show it twice.
        val userHidden = if (prefs.showHidden) emptySet() else prefs.hidden
        val hidden = userHidden + listOfNotNull(systemSettingsPackage())
        val sortMode = prefs.sortMode
        val order = comparatorFor<AppEntry>(sortMode, { it.label }, { prefs.usage(it.packageName) })
        val arranged = arrange(apps, prefs.favorites, hidden, { it.packageName }, order)
        appsLabel.text = twoTone("Apps", sortMode.title)

        visibleFavorites = arranged.favorites
        fillGrid(favoritesGrid, arranged.favorites.mapIndexed { index, app ->
            appTile(app, number = (index + 1).takeIf { it <= MAX_NUMBERED_FAVORITES })
        })
        emptyFavorites.visibility = if (arranged.favorites.isEmpty()) View.VISIBLE else View.GONE
        val movingApp = arranged.favorites.firstOrNull { it.packageName == movingKey }
        favoritesLabel.text = movingApp?.let { twoTone("Favorites", "Moving \u201C${it.label}\u201D", R.color.accent) } ?: "Favorites"

        fillGrid(appsGrid, arranged.others.map(::appTile) + systemTiles())
        applyMoveDimming(moving = movingApp != null)

        val restored = focusedKey?.let { key ->
            allTiles().firstOrNull { tileKey(it) == key }
        }
        if (restored != null) restored.requestFocus() else focusFirstTile()
        showWelcomeIfFirstRun()
        scroll.post { onHomeScrolled(scroll.scrollY) }
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
        header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // Logo option A lockup (mark + "zyft"), generated from Google Sans by build_fonts.py.
            addView(ImageView(context).apply {
                setImageResource(R.drawable.logo_lockup)
                contentDescription = getString(R.string.app_name)
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
            addView(statusChip, LinearLayout.LayoutParams(WRAP_CONTENT, dp(28)).apply { marginEnd = dp(16) })
            addView(date, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(12) })
            addView(clock)
        }

        favoritesLabel = sectionLabel()
        // Room for the caption under a focused banner in the last row, which would otherwise
        // run into the "Apps" label (the design's 28 dp row gap only covers rows within a grid).
        favoritesGrid = grid().apply { setPadding(0, 0, 0, dp(CAPTION_ROOM_DP)) }
        emptyFavorites = emptyFavoritesSlot()
        appsLabel = sectionLabel()
        appsGrid = grid()

        content = LinearLayout(this).apply {
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

        scroll = ZyftScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            clipChildren = false
            addView(content, MATCH_PARENT, WRAP_CONTENT)
            onScrolled = ::onHomeScrolled
        }
        moveHint = moveHintBar()
        stickyHeader = stickyHeaderBar()
        bottomFade = BackdropSliceView(
            this,
            maskColors = intArrayOf(0x00000000, 0xFF000000.toInt(), 0xFF000000.toInt()),
            maskStops = floatArrayOf(0f, 0.85f, 1f),
        ).apply { visibility = View.GONE }
        scrollTrack = scrollIndicator()
        root = FrameLayout(this).apply {
            addView(scroll, MATCH_PARENT, MATCH_PARENT)
            addView(bottomFade, FrameLayout.LayoutParams(MATCH_PARENT, dp(BOTTOM_FADE_DP), Gravity.BOTTOM))
            addView(stickyHeader, FrameLayout.LayoutParams(MATCH_PARENT, stickyZone(), Gravity.TOP))
            addView(scrollTrack, FrameLayout.LayoutParams(dp(3), MATCH_PARENT, Gravity.END).apply {
                setMargins(0, dp(SCROLL_TRACK_INSET_DP), dp(24), dp(SCROLL_TRACK_INSET_DP))
            })
            addView(moveHint, FrameLayout.LayoutParams(WRAP_CONTENT, dp(44), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = px(R.dimen.safe_y)
            })
        }
        return root
    }

    /** Screen 08: collapsed header shown once the real one scrolls away: section left, time right. */
    private fun stickyHeaderBar(): BackdropSliceView {
        stickyLabel = sectionLabel()
        stickyClock = label(R.dimen.type_clock, R.color.text_primary, medium = true)
        // Solid down to where grid rows start (so the real section label scrolling under it is
        // fully hidden), with a short fade at the very bottom.
        val fadeStart = 1f - dp(STICKY_FADE_DP).toFloat() / stickyZone()
        return BackdropSliceView(
            this,
            maskColors = intArrayOf(0xFF000000.toInt(), 0xFF000000.toInt(), 0x00000000),
            maskStops = floatArrayOf(0f, fadeStart, 1f),
        ).apply {
            visibility = View.GONE
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(px(R.dimen.safe_x), 0, px(R.dimen.safe_x), 0)
                addView(stickyLabel, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(stickyClock)
            }, FrameLayout.LayoutParams(MATCH_PARENT, px(R.dimen.header_height)).apply { topMargin = px(R.dimen.safe_y) })
        }
    }

    /** 3 dp scroll position hint, visible only while scrolling. */
    private fun scrollIndicator() = FrameLayout(this).apply {
        alpha = 0f
        background = roundedFill(R.color.stroke_hairline, R.dimen.radius_key).apply { cornerRadius = dp(2).toFloat() }
        scrollThumb = View(context).apply {
            background = roundedFill(R.color.text_secondary, R.dimen.radius_key).apply { cornerRadius = dp(2).toFloat() }
        }
        addView(scrollThumb, FrameLayout.LayoutParams(MATCH_PARENT, dp(56)))
    }

    private fun onTileFocused(tile: TileView) {
        val rect = Rect(0, 0, tile.width, tile.height)
        content.offsetDescendantRectToMyCoords(tile, rect)
        val target = rowSnapScrollTarget(
            rowTop = rect.top,
            rowBottom = rect.bottom,
            viewportHeight = scroll.height,
            contentHeight = content.height,
            stickyZone = stickyZone(),
            rowPitch = dp(metrics.tileHeightDp) + px(R.dimen.grid_gap_row),
            bottomReserve = px(R.dimen.safe_y) + dp(CAPTION_ROOM_DP),
        )
        stickyLabel.text = if (tile.parent?.parent === favoritesGrid) favoritesLabel.text else appsLabel.text
        smoothScrollTo(target)
    }

    /** Top band covered by the collapsed header; scrolled rows start right below it. */
    private fun stickyZone() = px(R.dimen.safe_y) + px(R.dimen.header_height) + px(R.dimen.section_top)

    private fun smoothScrollTo(target: Int) {
        if (target == scroll.scrollY) return
        scrollAnimator?.cancel()
        scrollAnimator = ObjectAnimator.ofInt(scroll, "scrollY", target).apply {
            duration = Motion.SCROLL_MS
            start()
        }
    }

    private fun jumpToTop() {
        smoothScrollTo(0)
        focusFirstTile()
    }

    private fun onHomeScrolled(scrollY: Int) {
        val collapsed = scrollY > px(R.dimen.header_height)
        stickyHeader.visibility = if (collapsed) View.VISIBLE else View.GONE
        bottomFade.visibility = if (scroll.canScrollVertically(1)) View.VISIBLE else View.GONE

        // Thumb position and size track the viewport; fade the indicator out after scrolling stops.
        val range = (content.height - scroll.height).coerceAtLeast(1)
        val trackHeight = scrollTrack.height
        val thumbHeight = (trackHeight.toLong() * scroll.height / content.height.coerceAtLeast(1)).toInt()
            .coerceIn(dp(24), trackHeight)
        scrollThumb.layoutParams.height = thumbHeight
        scrollThumb.translationY = (trackHeight - thumbHeight) * scrollY.toFloat() / range
        scrollThumb.requestLayout()
        scrollTrack.animate().cancel()
        scrollTrack.alpha = 1f
        scrollTrack.animate().alpha(0f).setStartDelay(Motion.SCROLL_INDICATOR_HIDE_DELAY_MS).setDuration(Motion.FOCUS_IN_MS).start()
    }

    private fun isInHome(view: View): Boolean {
        var parent = view.parent
        while (parent != null) {
            if (parent === content) return true
            parent = parent.parent
        }
        return false
    }

    /** Screen 07: one-time card with the three remote gestures; single focus target. */
    private fun showWelcomeIfFirstRun() {
        if (prefs.welcomeSeen || welcomeCard != null) return
        val got = label(R.dimen.type_list_item, R.color.ink, medium = true, text = "Got it").apply {
            gravity = Gravity.CENTER
            setPadding(dp(24), 0, dp(24), 0)
            background = roundedFill(R.color.accent, R.dimen.radius_list)
        }
        // Lime ring 2 dp outside the button, matching the focus treatment.
        val button = FrameLayout(this).apply {
            isFocusable = true
            isClickable = true
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = GradientDrawable().apply {
                cornerRadius = resources.getDimension(R.dimen.radius_list) + dp(5)
                setStroke(dp(3), getColor(R.color.accent))
            }
            addView(got, FrameLayout.LayoutParams(WRAP_CONTENT, dp(44)))
            setOnClickListener { dismissWelcome() }
        }
        val hints = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf("Hold OK" to "pin or move", "1\u20139" to "open a favorite", "Menu" to "app options").forEachIndexed { i, hint ->
                addView(keyHint(hint), LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { if (i > 0) marginStart = dp(24) })
            }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
            background = roundedFill(R.color.surface_sheet, R.dimen.radius_card, R.color.stroke_hairline)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(R.dimen.type_sheet_title, R.color.text_primary, medium = true, text = "Welcome to Zyft"))
                addView(hints, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(12) })
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(button, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(32) })
        }
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(getColor(R.color.scrim_light))
            addView(card, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM).apply {
                setMargins(px(R.dimen.safe_x), 0, px(R.dimen.safe_x), px(R.dimen.safe_y))
            })
        }
        welcomeCard = overlay
        scroll.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        root.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        button.requestFocus()
    }

    private fun dismissWelcome() {
        val card = welcomeCard ?: return
        prefs.welcomeSeen = true
        welcomeCard = null
        root.removeView(card)
        scroll.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        // Design: focus lands on the first app.
        (tilesIn(appsGrid).firstOrNull() ?: allTiles().firstOrNull())?.requestFocus()
    }

    /** Screen 03: floating key hint while a favorite is being moved. */
    private fun moveHintBar() = keyHint(
        "\u25C0 \u25B6" to "Left / right to move",
        "OK" to "finish",
        "Back" to "cancel",
        captionColorRes = R.color.text_primary,
    ).apply {
        visibility = View.GONE
        setPadding(dp(20), 0, dp(20), 0)
        background = roundedFill(R.color.surface_sheet, R.dimen.radius_card, R.color.stroke_hairline).apply {
            cornerRadius = dp(22).toFloat()
        }
    }

    /** Everything except the lifted tile drops to 40% so the move reads at a glance. */
    private fun applyMoveDimming(moving: Boolean) {
        val dim = if (moving) Scale.DIMMED_ALPHA else 1f
        header.alpha = dim
        appsLabel.alpha = dim
        appsGrid.alpha = dim
        tilesIn(favoritesGrid).forEach { it.alpha = if (!moving || tileKey(it) == movingKey) 1f else dim }
        moveHint.visibility = if (moving) View.VISIBLE else View.GONE
    }

    /** Layout slot (x in row, row top), ignoring the lift/scale transforms of the moving tile. */
    private fun slotOf(tile: View) = intArrayOf(tile.left, (tile.parent as View).top)

    private fun favoriteTilePositions(): Map<String, IntArray> = tilesIn(favoritesGrid).mapNotNull { tile ->
        tileKey(tile)?.let { key -> key to slotOf(tile) }
    }.toMap()

    /** Tiles that changed slot on the same row slide from their old x instead of jumping. */
    private fun animateSwaps(before: Map<String, IntArray>) {
        favoritesGrid.post {
            tilesIn(favoritesGrid).forEach { tile ->
                val old = tileKey(tile)?.let(before::get) ?: return@forEach
                val now = slotOf(tile)
                if (old[1] != now[1] || old[0] == now[0]) return@forEach
                tile.translationX = (old[0] - now[0]).toFloat()
                tile.animate().translationX(0f).setDuration(Motion.MOVE_MS).start()
            }
        }
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
        val tile = if (app.hasBanner) {
            TileView(this, TileView.Kind.BANNER).apply { setBanner(app.art); caption = app.label }
        } else {
            TileView(this, TileView.Kind.APP).apply { setIconAndLabel(app.art, app.label, APP_ICON_DP) }
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

    /** Shows [sheet] over the home screen and parks home focus until it closes. */
    private fun openSheet(sheet: SideSheet) {
        val returnFocus = currentFocus
        activeSheet = sheet
        scroll.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        sheet.show(root)
        sheetReturnFocus = returnFocus
    }

    private var sheetReturnFocus: View? = null

    private fun onSheetClosed() {
        activeSheet = null
        scroll.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        val key = sheetReturnFocus?.let(::tileKey)
        sheetReturnFocus = null
        (key?.let { k -> allTiles().firstOrNull { tileKey(it) == k } } ?: allTiles().firstOrNull())?.requestFocus()
    }

    private fun newSheet(withScrim: Boolean = true, wrapFocus: Boolean = true) =
        SideSheet(this, withScrim, wrapFocus, onClosed = ::onSheetClosed)

    /** Screen 04: right-side sheet; the first action adapts to favorite / not favorite. */
    private fun showAppMenu(app: AppEntry) {
        val pkg = app.packageName
        val position = visibleFavorites.indexOfFirst { it.packageName == pkg }
        val subtitle = when {
            prefs.isHidden(pkg) -> "Hidden"
            position >= 0 -> "In favorites \u00B7 position ${position + 1}"
            else -> "Not in favorites"
        }
        val sheet = newSheet()
        fun act(text: String, block: () -> Unit) = sheet.action(text) {
            sheet.close()
            block()
            render()
        }
        sheet.appHeader(app.art.takeIf { app.hasBanner }, app.art.takeUnless { app.hasBanner }, app.label, subtitle)
        if (position >= 0) {
            act("Remove from favorites") { prefs.toggleFavorite(pkg) }
            if (visibleFavorites.size > 1) act("Move") { startMove(pkg) }
        } else {
            act("Add to favorites") { prefs.toggleFavorite(pkg) }
        }
        if (prefs.isHidden(pkg)) act("Unhide") { prefs.unhide(pkg) } else act("Hide") { prefs.hide(pkg) }
        act("App info") {
            safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
        }
        sheet.footerHint("Back" to "close")
        openSheet(sheet)
    }

    private fun startMove(pkg: String) {
        orderBeforeMove = prefs.favorites
        movingKey = pkg
    }

    private fun endMove() {
        movingKey = null
        render()
    }

    /**
     * Screen 05: no scrim, so sort and hidden-app changes show live behind the sheet, and
     * focusing a background swatch previews it on the real home screen. Back reverts an
     * unconfirmed preview; OK applies.
     */
    private fun showCustomizeMenu() {
        val sheet = newSheet(withScrim = false, wrapFocus = false)
        sheet.onDismiss = ::applyBackdrop
        fillCustomize(sheet, focusTag = null)
        openSheet(sheet)
    }

    private fun fillCustomize(sheet: SideSheet, focusTag: String?) {
        sheet.body.removeAllViews()
        sheet.title("Customize", topPaddingDp = 4, bottomPaddingDp = 16)
        sheet.caption("Sort apps")
        SortMode.entries.forEach { mode ->
            sheet.radio(mode.title, selected = mode == prefs.sortMode) {
                prefs.sortMode = mode
                render()
                fillCustomize(sheet, focusTag = "sort:${mode.name}")
            }.tag = "sort:${mode.name}"
        }
        sheet.caption("Background", topPaddingDp = 16)
        sheet.body.addView(backdropSwatches(sheet))
        val hiddenCount = prefs.hidden.size
        sheet.toggle("Show hidden apps ($hiddenCount)", on = prefs.showHidden) {
            prefs.showHidden = !prefs.showHidden
            render()
            fillCustomize(sheet, focusTag = "hidden")
        }.apply {
            tag = "hidden"
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(12)
        }
        sheet.footerHint("Back" to "close")
        focusTag?.let { t -> sheet.body.findViewWithTag<View>(t)?.requestFocus() }
    }

    private fun backdropSwatches(sheet: SideSheet): View {
        val columns = 3
        val gap = dp(12)
        val cellWidth = (px(R.dimen.sheet_width) - dp(28) - px(R.dimen.safe_x) - gap * (columns - 1)) / columns
        val cellHeight = cellWidth * 9 / 16
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            Backdrop.entries.chunked(columns).forEachIndexed { rowIndex, rowItems ->
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    clipChildren = false
                    rowItems.forEachIndexed { i, backdrop ->
                        addView(backdropSwatch(sheet, backdrop, cellHeight), LinearLayout.LayoutParams(cellWidth, WRAP_CONTENT).apply {
                            if (i > 0) marginStart = gap
                        })
                    }
                }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { if (rowIndex > 0) topMargin = gap })
            }
        }
    }

    private fun backdropSwatch(sheet: SideSheet, backdrop: Backdrop, height: Int): View {
        val isCurrent = backdrop == prefs.backdrop
        val name = label(R.dimen.type_body, R.color.text_secondary, text = backdrop.title).apply { setPadding(0, dp(6), 0, 0) }
        val swatch = TileView(this, TileView.Kind.BANNER).apply {
            setSurface(backdrop.drawable())
            gapColor = getColor(R.color.surface_sheet)
            tag = "backdrop:${backdrop.name}"
            if (isCurrent) addToSurface(View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(getColor(R.color.accent)) }
            }, FrameLayout.LayoutParams(dp(8), dp(8), Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, dp(5), dp(5)) })
            setOnFocusChangeListener { _, focused ->
                name.setTextColor(getColor(if (focused) R.color.text_primary else R.color.text_secondary))
                if (!focused) return@setOnFocusChangeListener
                showBackdrop(backdrop)
                if (backdrop == prefs.backdrop) {
                    sheet.footerHint("Back" to "close")
                } else {
                    sheet.setFooter(label(R.dimen.type_body, R.color.text_tertiary, text = "Previewing ${backdrop.title} \u00B7 OK to apply"))
                }
            }
            setOnClickListener {
                prefs.backdrop = backdrop
                applyBackdrop()
                fillCustomize(sheet, focusTag = "backdrop:${backdrop.name}")
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            addView(swatch, LinearLayout.LayoutParams(MATCH_PARENT, height))
            addView(name)
        }
    }

    /** Screen 06: HDMI / AV inputs the device reports, with signal state, via TV input passthrough. */
    private fun showInputs() {
        val tvInputs = getSystemService(TvInputManager::class.java)
        val inputs = tvInputs?.tvInputList?.filter { it.isPassthroughInput }.orEmpty()
        val sheet = newSheet()
        sheet.title("Inputs")
        if (inputs.isEmpty()) sheet.caption("No external inputs found on this TV.")
        inputs.forEach { input ->
            val label = input.loadLabel(this).toString()
            val subtitle = inputSubtitle(label, input.loadCustomLabel(this)?.toString(), inputStateOf(tvInputs!!.getInputState(input.id)))
            sheet.twoLine(label, subtitle) {
                sheet.close()
                safeStart(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id)))
            }
        }
        if (inputs.isEmpty()) sheet.footerHint("Back" to "close") else sheet.footerHint("OK" to "switch", "Back" to "close")
        openSheet(sheet)
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

    private fun applyBackdrop() = showBackdrop(prefs.backdrop)

    /** Window plus the overlays that repaint a slice of it, so previews stay seamless. */
    private fun showBackdrop(backdrop: Backdrop) {
        window.setBackgroundDrawable(backdrop.drawable())
        if (::stickyHeader.isInitialized) {
            stickyHeader.setBackdrop(backdrop)
            bottomFade.setBackdrop(backdrop)
        }
    }

    private fun updateClock() {
        val now = Date()
        clock.text = DateFormat.getTimeFormat(this).format(now)
        if (::stickyClock.isInitialized) stickyClock.text = clock.text
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
        /** Longest the first frame waits for the cached app list (normally ready in ~50 ms). */
        const val FIRST_FRAME_WAIT_MS = 150L
        const val CAPTION_ROOM_DP = 20
        const val BOTTOM_FADE_DP = 96
        const val STICKY_FADE_DP = 12
        const val SCROLL_TRACK_INSET_DP = 87
        const val UTILITY_ICON_DP = 28
        const val DATE_SKELETON = "EEEdMMM"

        // Prefixed so they can never collide with a package name.
        const val TILE_INPUTS = "system:inputs"
        const val TILE_SETTINGS = "system:settings"
        const val TILE_CUSTOMIZE = "system:customize"
    }
}
