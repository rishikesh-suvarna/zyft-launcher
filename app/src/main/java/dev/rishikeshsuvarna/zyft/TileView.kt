package dev.rishikeshsuvarna.zyft

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * One home-screen tile, per the design's ZyftTile component:
 * - [Kind.BANNER]: the app's 16:9 TV banner, centre-cropped, captioned only while focused
 * - [Kind.APP]: fallback for apps without a banner, icon + one-line label
 * - [Kind.UTILITY]: Inputs / Settings / Customize
 *
 * Focus is a double stroke (2 dp ink gap + 3 dp lime ring) drawn *outside* the tile, so the
 * artwork keeps its rounded clip while the ring stays readable on any banner or background.
 * Parents must not clip children for the ring, caption and move chevrons to show.
 */
class TileView(context: Context, val kind: Kind) : FrameLayout(context) {

    enum class Kind { BANNER, APP, UTILITY }

    private val density = resources.displayMetrics.density
    private val radius = resources.getDimension(R.dimen.radius_tile)
    private val gapWidth = resources.getDimension(R.dimen.focus_gap)
    private val ringWidth = resources.getDimension(R.dimen.focus_ring)
    private val accent = context.getColor(R.color.accent)

    /** The rounded, clipped surface holding the artwork and the number badge. */
    private val surface = FrameLayout(context).apply {
        clipToOutline = true
        background = GradientDrawable().apply {
            cornerRadius = radius
            when (kind) {
                Kind.BANNER -> setColor(context.getColor(R.color.surface_tile))
                Kind.APP -> {
                    setColor(context.getColor(R.color.surface_tile))
                    setStroke(resources.getDimensionPixelSize(R.dimen.hairline), context.getColor(R.color.stroke_tile))
                }
                Kind.UTILITY -> {
                    setColor(context.getColor(R.color.surface_utility))
                    setStroke(resources.getDimensionPixelSize(R.dimen.hairline), context.getColor(R.color.stroke_hairline))
                }
            }
        }
    }

    /** Shown under a focused banner so banner-only apps are still identifiable. */
    var caption: String? = null

    /** Colour between tile and ring; the sheet uses its own surface colour instead of ink. */
    var gapColor: Int = context.getColor(R.color.ink)

    var isMoving: Boolean = false
        set(value) {
            field = value
            animateToState()
        }

    private var ringProgress = 0f
    private var ringAnimator: ValueAnimator? = null

    private val gapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = gapWidth }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ringWidth; color = accent }
    private val chevronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_primary)
        textSize = resources.getDimension(R.dimen.type_tile_label)
        typeface = MEDIUM
        textAlign = Paint.Align.CENTER
    }
    private val ringRect = RectF()
    private val chevron = Path()

    init {
        isFocusable = true
        isClickable = true
        clipChildren = false
        clipToPadding = false
        setWillNotDraw(false)
        addView(surface, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        // Shadow for the focus/move elevation follows the rounded tile, not the caption.
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) =
                outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
    }

    fun setBanner(banner: Drawable) {
        surface.addView(ImageView(context).apply {
            setImageDrawable(banner)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }, LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    /** Icon + one-line label; [iconSizeDp] is 40 for app icons, 28 for utility glyphs. */
    fun setIconAndLabel(icon: Drawable?, label: String, iconSizeDp: Int) {
        val iconBox = (ICON_BOX_DP * density).toInt()
        val iconSize = (iconSizeDp * density).toInt()
        surface.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = (LABEL_PADDING_DP * density).toInt()
            setPadding(pad, 0, pad, 0)
            addView(FrameLayout(context).apply {
                addView(ImageView(context).apply { setImageDrawable(icon) }, LayoutParams(iconSize, iconSize, Gravity.CENTER))
            }, LinearLayout.LayoutParams(iconBox, iconBox))
            addView(TextView(context).apply {
                text = label
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                typeface = MEDIUM
                setTextColor(context.getColor(R.color.text_primary))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.type_tile_label))
                setPadding(0, (ICON_LABEL_GAP_DP * density).toInt(), 0, 0)
            }, LinearLayout.LayoutParams(MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }, LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    /** Replaces the surface fill, e.g. a background preset preview in Customize. */
    fun setSurface(drawable: Drawable) {
        surface.background = drawable
    }

    /** Small overlay inside the rounded surface (e.g. the "current" dot on a swatch). */
    fun addToSurface(view: View, params: LayoutParams) = surface.addView(view, params)

    /** Remote shortcut badge, 1-9 only. */
    fun setBadge(number: Int) {
        val size = (BADGE_DP * density).toInt()
        val inset = (BADGE_INSET_DP * density).toInt()
        surface.addView(TextView(context).apply {
            text = number.toString()
            gravity = Gravity.CENTER
            includeFontPadding = false
            typeface = MEDIUM
            setTextColor(context.getColor(R.color.text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.type_badge))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(context.getColor(R.color.badge_fill))
                setStroke(resources.getDimensionPixelSize(R.dimen.hairline), context.getColor(R.color.stroke_badge))
            }
        }, LayoutParams(size, size, Gravity.TOP or Gravity.START).apply { setMargins(inset, inset, 0, 0) })
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        animateToState()
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        animateToState()
    }

    private fun animateToState() {
        val active = isFocused || isMoving
        val scale = when {
            isMoving -> Scale.MOVING
            isPressed -> Scale.PRESSED
            isFocused -> Scale.FOCUSED
            else -> 1f
        }
        val duration = when {
            isMoving -> Motion.MOVE_MS
            isPressed -> Motion.PRESS_MS
            active -> Motion.FOCUS_IN_MS
            else -> Motion.FOCUS_OUT_MS
        }
        animate().scaleX(scale).scaleY(scale)
            .translationY(if (isMoving) -MOVE_LIFT_DP * density else 0f)
            .translationZ(
                when {
                    isMoving -> resources.getDimension(R.dimen.elevation_moving)
                    active -> resources.getDimension(R.dimen.elevation_focused)
                    else -> 0f
                },
            )
            .setDuration(duration).setInterpolator(EASE_OUT).start()

        val target = if (active) 1f else 0f
        if (ringProgress == target && ringAnimator?.isRunning != true) return
        ringAnimator?.cancel()
        ringAnimator = ValueAnimator.ofFloat(ringProgress, target).apply {
            this.duration = duration
            interpolator = EASE_OUT
            addUpdateListener {
                ringProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (ringProgress <= 0f) return
        val alpha = (ringProgress * OPAQUE).toInt()

        // Strokes are centred on the path, so offset each by half its width outside the tile.
        val gapOffset = gapWidth / 2
        ringRect.set(-gapOffset, -gapOffset, width + gapOffset, height + gapOffset)
        gapPaint.color = gapColor
        gapPaint.alpha = alpha
        canvas.drawRoundRect(ringRect, radius + gapOffset, radius + gapOffset, gapPaint)

        val ringOffset = gapWidth + ringWidth / 2
        ringRect.set(-ringOffset, -ringOffset, width + ringOffset, height + ringOffset)
        ringPaint.alpha = alpha
        canvas.drawRoundRect(ringRect, radius + ringOffset, radius + ringOffset, ringPaint)

        if (isMoving) drawChevrons(canvas, alpha)
        val text = caption
        if (kind == Kind.BANNER && text != null && isFocused && !isMoving) drawCaption(canvas, text, alpha)
    }

    private fun drawChevrons(canvas: Canvas, alpha: Int) {
        chevronPaint.alpha = alpha
        val w = CHEVRON_W_DP * density
        val h = CHEVRON_H_DP * density
        val outset = CHEVRON_OUTSET_DP * density
        val cy = height / 2f
        chevron.reset()
        chevron.moveTo(-outset + w, cy - h / 2)
        chevron.lineTo(-outset + w, cy + h / 2)
        chevron.lineTo(-outset, cy)
        chevron.close()
        chevron.moveTo(width + outset - w, cy - h / 2)
        chevron.lineTo(width + outset, cy)
        chevron.lineTo(width + outset - w, cy + h / 2)
        chevron.close()
        canvas.drawPath(chevron, chevronPaint)
    }

    private fun drawCaption(canvas: Canvas, text: String, alpha: Int) {
        val maxWidth = width + 2 * CAPTION_OVERHANG_DP * density
        val fitted = TextUtils.ellipsize(text, captionPaint, maxWidth, TextUtils.TruncateAt.END).toString()
        captionPaint.alpha = alpha
        val baseline = height + CAPTION_GAP_DP * density - captionPaint.ascent()
        canvas.drawText(fitted, width / 2f, baseline, captionPaint)
    }

    companion object {
        val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        private val EASE_OUT = DecelerateInterpolator()
        private const val OPAQUE = 255
        private const val ICON_BOX_DP = 40
        private const val LABEL_PADDING_DP = 12
        private const val ICON_LABEL_GAP_DP = 8
        private const val BADGE_DP = 20
        private const val BADGE_INSET_DP = 6
        private const val MOVE_LIFT_DP = 6
        private const val CHEVRON_W_DP = 10
        private const val CHEVRON_H_DP = 14
        private const val CHEVRON_OUTSET_DP = 19
        private const val CAPTION_GAP_DP = 8
        private const val CAPTION_OVERHANG_DP = 8
    }
}
