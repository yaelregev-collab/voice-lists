package app.voicelists

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.accent(): Int = getColor(android.R.color.system_accent1_600)

fun Context.text(value: String, sizeSp: Float = 16f, bold: Boolean = false): TextView = TextView(this).apply {
    text = value
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    textAlignment = TextView.TEXT_ALIGNMENT_VIEW_START
}

/** A borderless icon button tinted with the accent color. */
fun Context.iconButton(icon: Int, label: String, onClick: () -> Unit): ImageButton = ImageButton(this).apply {
    setImageResource(icon)
    setColorFilter(accent())
    background = null
    contentDescription = label
    setOnClickListener { onClick() }
}

/** A rounded, filled button in the phone's Material You accent color. */
fun Context.pill(label: String, filled: Boolean = true, onClick: () -> Unit): Button = Button(this, null, 0, 0).apply {
    text = label
    isAllCaps = false
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    setPadding(dp(18), dp(10), dp(18), dp(10))
    val bg = GradientDrawable().apply {
        cornerRadius = dp(24).toFloat()
        if (filled) setColor(accent()) else {
            setColor(getColor(android.R.color.system_accent1_100))
        }
    }
    background = bg
    setTextColor(if (filled) getColor(android.R.color.white) else getColor(android.R.color.system_accent1_900))
    setOnClickListener { onClick() }
}
