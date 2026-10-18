package dev.rishikeshsuvarna.zyft

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.FocusFinder
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Right-side sheet from the design (screens 04-06): 360 dp panel, flat scrim (no blur),
 * 150 ms slide-in. Back closes; Left closes when there is nothing further left inside;
 * list sheets wrap focus top <-> bottom.
 */
class SideSheet(
    context: Context,
    private val withScrim: Boolean = true,
    private val wrapFocus: Boolean = true,
    private val onClosed: () -> Unit,
) : FrameLayout(context) {

    /** Rows and sections go here, top to bottom. */
    val body = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        // Swatch focus rings and scale draw past their cells.
        clipChildren = false
        clipToPadding = false
    }

    private val footer = FrameLayout(context)
    private val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
        setBackgroundColor(context.getColor(R.color.surface_sheet))
        setPadding(context.dp(28), context.px(R.dimen.safe_y), context.px(R.dimen.safe_x), context.px(R.dimen.safe_y))
        // Keeps key events inside the sheet even when it has no focusable rows.
        isFocusable = true
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        addView(body, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        addView(footer, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }
    private var closing = false

    /** Called after Back / Left closes the sheet, before [onClosed]; lets Customize revert a preview. */
    var onDismiss: (() -> Unit)? = null

    init {
        if (withScrim) addView(View(context).apply { setBackgroundColor(context.getColor(R.color.scrim)) }, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val width = context.px(R.dimen.sheet_width)
        addView(panel, LayoutParams(width, MATCH_PARENT, Gravity.END))
        // 1 dp hairline on the panel's left edge instead of a shadow.
        addView(View(context).apply { setBackgroundColor(context.getColor(R.color.stroke_hairline)) },
            LayoutParams(context.px(R.dimen.hairline), MATCH_PARENT, Gravity.END).apply { marginEnd = width })
    }

    fun setFooter(view: View) {
        footer.removeAllViews()
        footer.addView(view)
    }

    fun show(host: ViewGroup) {
        host.addView(this, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val shift = context.dp(SLIDE_DP).toFloat()
        panel.translationX = shift
        panel.alpha = 0f
        panel.animate().translationX(0f).alpha(1f).setDuration(Motion.SHEET_MS).setInterpolator(EASE_OUT).start()
        if (withScrim) getChildAt(0).apply { alpha = 0f; animate().alpha(1f).setDuration(Motion.SHEET_MS).start() }
        post { firstFocusable()?.requestFocus() ?: panel.requestFocus() }
    }

    fun close() {
        if (closing) return
        closing = true
        panel.animate().translationX(context.dp(SLIDE_DP).toFloat()).alpha(0f).setDuration(Motion.SHEET_MS)
            .withEndAction {
                (parent as? ViewGroup)?.removeView(this)
                onClosed()
            }.start()
        if (withScrim) getChildAt(0).animate().alpha(0f).setDuration(Motion.SHEET_MS).start()
    }

    /** Returns true when the key was handled here (navigation edge cases and closing). */
    fun interceptKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return event.keyCode == KeyEvent.KEYCODE_BACK
        val focused = findFocus()
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> dismiss()
            KeyEvent.KEYCODE_DPAD_LEFT ->
                if (focused == null || nextFocus(focused, View.FOCUS_LEFT) == null) dismiss() else false
            KeyEvent.KEYCODE_DPAD_DOWN -> wrapIfAtEdge(focused, View.FOCUS_DOWN) { firstFocusable() }
            KeyEvent.KEYCODE_DPAD_UP -> wrapIfAtEdge(focused, View.FOCUS_UP) { lastFocusable() }
            // Don't let focus escape to the right edge of the screen.
            KeyEvent.KEYCODE_DPAD_RIGHT -> focused != null && nextFocus(focused, View.FOCUS_RIGHT) == null
            else -> false
        }
    }

    private fun dismiss(): Boolean {
        onDismiss?.invoke()
        close()
        return true
    }

    private inline fun wrapIfAtEdge(focused: View?, direction: Int, target: () -> View?): Boolean {
        if (focused == null || nextFocus(focused, direction) != null) return false
        if (wrapFocus) target()?.requestFocus()
        return true
    }

    private fun nextFocus(from: View, direction: Int): View? = FocusFinder.getInstance().findNextFocus(panel, from, direction)

    private fun focusables(): List<View> = panel.getFocusables(View.FOCUS_FORWARD).filter { it !== panel }
    private fun firstFocusable(): View? = focusables().firstOrNull()
    private fun lastFocusable(): View? = focusables().lastOrNull()

    private companion object {
        const val SLIDE_DP = 24
        val EASE_OUT = DecelerateInterpolator()
    }
}

// region Sheet building blocks

fun SideSheet.title(text: String, topPaddingDp: Int = 16, bottomPaddingDp: Int = 20) {
    body.addView(context.label(R.dimen.type_sheet_title, R.color.text_primary, medium = true, text = text).apply {
        setPadding(0, context.dp(topPaddingDp), 0, context.dp(bottomPaddingDp))
    })
}

fun SideSheet.caption(text: String, topPaddingDp: Int = 0) {
    body.addView(context.label(R.dimen.type_body, R.color.text_tertiary, text = text).apply {
        setPadding(0, context.dp(topPaddingDp), 0, context.dp(8))
    })
}

/** App thumbnail + name + one-line state, top of the app options sheet. */
fun SideSheet.appHeader(banner: Drawable?, icon: Drawable?, name: String, subtitle: String) {
    val ctx = context
    body.addView(LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, ctx.dp(12), 0, ctx.dp(20))
        addView(FrameLayout(ctx).apply {
            clipToOutline = true
            background = ctx.roundedFill(R.color.surface_tile, R.dimen.radius_key)
            if (banner != null) {
                addView(ImageView(ctx).apply { setImageDrawable(banner); scaleType = ImageView.ScaleType.CENTER_CROP })
            } else {
                addView(ImageView(ctx).apply { setImageDrawable(icon) }, FrameLayout.LayoutParams(ctx.dp(28), ctx.dp(28), Gravity.CENTER))
            }
        }, LinearLayout.LayoutParams(ctx.dp(72), ctx.dp(40)).apply { marginEnd = ctx.dp(14) })
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(ctx.label(R.dimen.type_sheet_title, R.color.text_primary, medium = true, text = name).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            addView(ctx.label(R.dimen.type_body, R.color.text_tertiary, text = subtitle).apply { setPadding(0, ctx.dp(4), 0, 0) })
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
    })
}

/** Plain action row: 48 dp, solid lime fill when focused, no scale. */
fun SideSheet.action(text: String, onClick: () -> Unit): View = row(R.dimen.list_item, gapDp = 4) {
    val title = context.label(R.dimen.type_list_item, R.color.text_primary, text = text)
    addView(title)
    onFocus { focused -> title.applyListTextStyle(focused) }
    setOnClickListener { onClick() }
}

/** Radio option row (Customize > Sort apps): selected = lime text + filled radio. */
fun SideSheet.radio(text: String, selected: Boolean, onClick: () -> Unit): View = row(R.dimen.list_item_compact, gapDp = 2) {
    val title = context.label(R.dimen.type_list_item, R.color.text_primary, text = text)
    val dot = radioDot(context)
    addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
    addView(dot, LinearLayout.LayoutParams(context.dp(14), context.dp(14)))
    fun paint(focused: Boolean) {
        title.applyListTextStyle(focused, selectedColor = if (selected) R.color.accent else null)
        dot.background = radioDrawable(context, selected, onAccent = focused)
    }
    paint(false)
    onFocus(::paint)
    setOnClickListener { onClick() }
}

/** Toggle row with a small switch (Customize > Show hidden apps). */
fun SideSheet.toggle(text: String, on: Boolean, onClick: () -> Unit): View = row(R.dimen.list_item_compact, gapDp = 0) {
    val title = context.label(R.dimen.type_list_item, R.color.text_primary, text = text)
    val track = FrameLayout(context)
    val knob = View(context)
    track.addView(knob, FrameLayout.LayoutParams(context.dp(14), context.dp(14), Gravity.CENTER_VERTICAL or if (on) Gravity.END else Gravity.START).apply {
        setMargins(context.dp(3), 0, context.dp(3), 0)
    })
    addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
    addView(track, LinearLayout.LayoutParams(context.dp(34), context.dp(20)))
    fun paint(focused: Boolean) {
        title.applyListTextStyle(focused)
        val trackColor = when {
            on && focused -> R.color.ink
            on -> R.color.accent
            else -> R.color.surface_key
        }
        val knobColor = when {
            on && focused -> R.color.accent
            on -> R.color.ink
            else -> R.color.text_secondary
        }
        track.background = GradientDrawable().apply { cornerRadius = context.dp(10).toFloat(); setColor(context.getColor(trackColor)) }
        knob.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(context.getColor(knobColor)) }
    }
    paint(false)
    onFocus(::paint)
    setOnClickListener { onClick() }
}

/** Two-line row (Inputs): 56 dp, title + status. */
fun SideSheet.twoLine(titleText: String, subtitleText: String, onClick: () -> Unit): View =
    row(R.dimen.list_item_two_line, gapDp = 4, vertical = true) {
        val title = context.label(R.dimen.type_list_item, R.color.text_primary, text = titleText)
        val subtitle = context.label(R.dimen.type_body, R.color.text_tertiary, text = subtitleText).apply { setPadding(0, context.dp(4), 0, 0) }
        addView(title)
        addView(subtitle)
        onFocus { focused ->
            title.applyListTextStyle(focused)
            subtitle.setTextColor(context.getColor(if (focused) R.color.on_accent_secondary else R.color.text_tertiary))
        }
        setOnClickListener { onClick() }
    }

fun SideSheet.footerHint(vararg parts: Pair<String, String>) {
    setFooter(context.keyHint(*parts, captionColorRes = R.color.text_tertiary))
}

private fun SideSheet.row(heightRes: Int, gapDp: Int, vertical: Boolean = false, build: LinearLayout.() -> Unit): View {
    val ctx = context
    val row = LinearLayout(ctx).apply {
        orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        gravity = if (vertical) Gravity.CENTER_VERTICAL else Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        setPadding(ctx.dp(16), 0, ctx.dp(16), 0)
        build()
    }
    body.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, ctx.px(heightRes)).apply {
        if (body.childCount > 0) topMargin = ctx.dp(gapDp)
    })
    return row
}

private fun LinearLayout.onFocus(paint: (Boolean) -> Unit) {
    setOnFocusChangeListener { _, focused ->
        background = if (focused) context.roundedFill(R.color.accent, R.dimen.radius_list) else null
        paint(focused)
    }
}

private fun TextView.applyListTextStyle(focused: Boolean, selectedColor: Int? = null) {
    setTextColor(context.getColor(if (focused) R.color.ink else selectedColor ?: R.color.text_primary))
    typeface = if (focused) TileView.MEDIUM else android.graphics.Typeface.DEFAULT
}

private fun radioDot(context: Context) = View(context)

private fun radioDrawable(context: Context, selected: Boolean, onAccent: Boolean): Drawable {
    val color = context.getColor(
        when {
            onAccent -> R.color.ink
            selected -> R.color.accent
            else -> R.color.text_disabled
        },
    )
    val ring = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke(context.dp(2), color) }
    if (!selected) return ring
    val dot = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
    return android.graphics.drawable.LayerDrawable(arrayOf(ring, dot)).apply {
        val inset = context.dp(4)
        setLayerInset(1, inset, inset, inset, inset)
    }
}

// endregion
