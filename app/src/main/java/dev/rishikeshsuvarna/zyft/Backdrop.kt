package dev.rishikeshsuvarna.zyft

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Gradients instead of images: drawn by the GPU with no bitmap in memory,
 * which matters on a TV with ~1.5 GB RAM shared with the streaming apps.
 * Colours from the design tokens (bg.*), all at the design's 160 degree angle.
 */
enum class Backdrop(val title: String, val from: Int, val to: Int) {
    MIDNIGHT("Midnight", 0xFF0A0D16.toInt(), 0xFF141B30.toInt()),
    OCEAN("Ocean", 0xFF051419.toInt(), 0xFF0A2C3B.toInt()),
    SUNSET("Sunset", 0xFF170C10.toInt(), 0xFF33171C.toInt()),
    FOREST("Forest", 0xFF08130E.toInt(), 0xFF11281C.toInt()),
    GRAPHITE("Graphite", 0xFF0F1012.toInt(), 0xFF1E2024.toInt()),
    PLAIN("Plain", 0xFF0D0E10.toInt(), 0xFF0D0E10.toInt());

    fun drawable(): Drawable = BackdropDrawable(from, to, GRADIENT_ANGLE_DEG)

    private companion object {
        const val GRADIENT_ANGLE_DEG = 160f
    }
}

/**
 * Start and end points (x0, y0, x1, y1) of a CSS-style linear-gradient line:
 * 0 deg points up, angles grow clockwise, and the line is long enough that the
 * corners get exactly the end colours. Matches what the design tool renders.
 */
fun cssGradientLine(angleDeg: Float, width: Float, height: Float): FloatArray {
    val radians = Math.toRadians(angleDeg.toDouble())
    val dx = sin(radians).toFloat()
    val dy = -cos(radians).toFloat()
    val halfLength = (abs(width * dx) + abs(height * dy)) / 2
    val cx = width / 2
    val cy = height / 2
    return floatArrayOf(cx - dx * halfLength, cy - dy * halfLength, cx + dx * halfLength, cy + dy * halfLength)
}

private class BackdropDrawable(
    private val from: Int,
    private val to: Int,
    private val angleDeg: Float,
) : Drawable() {

    private val paint = Paint()

    override fun onBoundsChange(bounds: Rect) {
        val (x0, y0, x1, y1) = cssGradientLine(angleDeg, bounds.width().toFloat(), bounds.height().toFloat())
        paint.shader = LinearGradient(
            bounds.left + x0, bounds.top + y0, bounds.left + x1, bounds.top + y1,
            from, to, Shader.TileMode.CLAMP,
        )
    }

    override fun draw(canvas: Canvas) = canvas.drawRect(bounds, paint)

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}
