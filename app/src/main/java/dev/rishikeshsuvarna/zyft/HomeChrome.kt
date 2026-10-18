package dev.rishikeshsuvarna.zyft

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.widget.FrameLayout
import android.widget.ScrollView

/**
 * ScrollView that never scrolls on its own when a child takes focus: the home screen
 * drives row-snapped scrolling itself (design screen 08).
 */
class ZyftScrollView(context: Context) : ScrollView(context) {

    var onScrolled: ((scrollY: Int) -> Unit)? = null

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect?): Int = 0

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScrolled?.invoke(t)
    }
}

/**
 * Paints the slice of the full-screen backdrop that sits behind this view, so overlays
 * (sticky header, bottom fade) blend seamlessly with the 160 degree gradient. An optional
 * vertical alpha mask fades the slice in or out.
 */
class BackdropSliceView(
    context: Context,
    private val maskColors: IntArray? = null,
    private val maskStops: FloatArray? = null,
) : FrameLayout(context) {

    private var drawable: Drawable? = null
    private val location = IntArray(2)
    private val maskPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }

    init {
        setWillNotDraw(false)
        // Mask compositing needs an offscreen layer; it's a thin strip, so this stays cheap.
        if (maskColors != null) setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setBackdrop(backdrop: Backdrop) {
        drawable = backdrop.drawable()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val colors = maskColors ?: return
        maskPaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), colors, maskStops, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val slice = drawable ?: return
        getLocationInWindow(location)
        val screen = rootView
        slice.setBounds(-location[0], -location[1], screen.width - location[0], screen.height - location[1])
        slice.draw(canvas)
        if (maskColors != null) canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)
    }
}

/**
 * Where the focused row should scroll to: rows that fit on the first screen keep the page at
 * the top; deeper rows are held as the 2nd visible row under the sticky header.
 * All values in px; result clamped to the scrollable range.
 */
fun rowSnapScrollTarget(
    rowTop: Int,
    rowBottom: Int,
    viewportHeight: Int,
    contentHeight: Int,
    stickyZone: Int,
    rowPitch: Int,
    bottomReserve: Int,
): Int {
    val maxScroll = (contentHeight - viewportHeight).coerceAtLeast(0)
    if (rowBottom + bottomReserve <= viewportHeight) return 0
    return (rowTop - stickyZone - rowPitch).coerceIn(0, maxScroll)
}
