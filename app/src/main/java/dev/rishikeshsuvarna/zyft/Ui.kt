package dev.rishikeshsuvarna.zyft

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Small view factories shared by the home screen and the sheets, all driven by tokens. */

fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

fun Context.dp(value: Int): Int = dp(value.toFloat())

fun Context.px(dimenRes: Int): Int = resources.getDimensionPixelSize(dimenRes)

fun Context.label(textSizeRes: Int, colorRes: Int, medium: Boolean = false, text: CharSequence = ""): TextView =
    TextView(this).apply {
        this.text = text
        includeFontPadding = false
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(textSizeRes))
        setTextColor(getColor(colorRes))
        if (medium) typeface = TileView.MEDIUM
    }

fun Context.roundedFill(colorRes: Int, radiusRes: Int, strokeColorRes: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        setColor(getColor(colorRes))
        cornerRadius = resources.getDimension(radiusRes)
        strokeColorRes?.let { setStroke(px(R.dimen.hairline), getColor(it)) }
    }

/** "Apps · Most used": name in primary, qualifier in secondary, per the section label spec. */
fun Context.twoTone(primary: String, secondary: String?, secondaryColorRes: Int = R.color.text_secondary): CharSequence {
    if (secondary == null) return primary
    return SpannableStringBuilder(primary).apply {
        val start = length
        append("  · ").append(secondary)
        setSpan(ForegroundColorSpan(getColor(secondaryColorRes)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

/** Remote-key cap, e.g. [OK] or [Back], used in hints and sheet footers. */
fun Context.keyCap(text: String): TextView = label(R.dimen.type_badge, R.color.text_primary, medium = true, text = text).apply {
    gravity = Gravity.CENTER
    minHeight = dp(24)
    setPadding(dp(8), 0, dp(8), 0)
    background = roundedFill(R.color.surface_key, R.dimen.radius_key)
}

/** Row of key caps and captions: [OK] finish · [Back] cancel. */
fun Context.keyHint(vararg parts: Pair<String, String>, captionColorRes: Int = R.color.text_secondary): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        parts.forEachIndexed { i, (key, caption) ->
            if (i > 0) addView(label(R.dimen.type_date, R.color.text_disabled, text = "·").apply {
                setPadding(dp(12), 0, dp(12), 0)
            })
            addView(keyCap(key))
            addView(label(R.dimen.type_date, captionColorRes, text = caption).apply { setPadding(dp(8), 0, 0, 0) })
        }
    }
