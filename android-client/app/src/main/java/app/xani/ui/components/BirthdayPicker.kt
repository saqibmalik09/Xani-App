package app.xani.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import app.xani.ui.theme.Colors
import app.xani.ui.theme.rdp

private val MonthLabels = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/**
 * Native drum wheels. Three Compose LazyColumns on first SignUp composition made
 * Continue-with-Email feel like a stall; NumberPicker recycles like the country list.
 */
@Composable
fun BirthdayPicker(
    month: Int,
    day: Int,
    year: Int,
    minYear: Int,
    maxYear: Int,
    onMonthChange: (Int) -> Unit,
    onDayChange: (Int) -> Unit,
    onYearChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = daysInMonth(month, year)
    val shape = RoundedCornerShape(rdp(6))
    val ink = Colors.Text.toArgb()

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(rdp(180))
            .clip(shape)
            .background(Colors.Input)
            .border(rdp(1.5f), Colors.BorderStrong, shape),
        factory = { context ->
            BirthdayHost(context).also { host ->
                host.month.minValue = 0
                host.month.maxValue = 11
                host.month.displayedValues = MonthLabels
                host.month.wrapSelectorWheel = true

                host.day.minValue = 1
                host.day.maxValue = 31
                host.day.wrapSelectorWheel = true

                host.year.minValue = minYear
                host.year.maxValue = maxYear
                host.year.wrapSelectorWheel = false

                host.month.value = (month - 1).coerceIn(0, 11)
                host.day.value = day.coerceIn(1, 31)
                host.year.value = year.coerceIn(minYear, maxYear)

                host.month.setOnValueChangedListener { _, _, next ->
                    if (!host.publishing) host.onMonth(next + 1)
                }
                host.day.setOnValueChangedListener { _, _, next ->
                    if (!host.publishing) host.onDay(next)
                }
                host.year.setOnValueChangedListener { _, _, next ->
                    if (!host.publishing) host.onYear(next)
                }
            }
        },
        update = { host ->
            host.onMonth = onMonthChange
            host.onDay = onDayChange
            host.onYear = onYearChange
            host.applyInk(ink)
            host.publishing = true
            try {
                val monthIndex = (month - 1).coerceIn(0, 11)
                if (host.month.value != monthIndex) host.month.value = monthIndex

                if (host.year.minValue != minYear) host.year.minValue = minYear
                if (host.year.maxValue != maxYear) host.year.maxValue = maxYear
                val yearClamped = year.coerceIn(minYear, maxYear)
                if (host.year.value != yearClamped) host.year.value = yearClamped

                if (host.day.value > days) host.day.value = days
                if (host.day.maxValue != days) host.day.maxValue = days
                val dayClamped = day.coerceIn(1, days)
                if (host.day.value != dayClamped) host.day.value = dayClamped
            } finally {
                host.publishing = false
            }
        },
    )
}

private class BirthdayHost(context: android.content.Context) : LinearLayout(context) {
    val month = wheel(context)
    val day = wheel(context)
    val year = wheel(context)
    var onMonth: (Int) -> Unit = {}
    var onDay: (Int) -> Unit = {}
    var onYear: (Int) -> Unit = {}
    var publishing = false
    private val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x148B5CF6.toInt()
    }

    fun applyInk(color: Int) {
        if (Build.VERSION.SDK_INT >= 29) {
            month.textColor = color
            day.textColor = color
            year.textColor = color
        }
    }

    init {
        orientation = HORIZONTAL
        setWillNotDraw(false)
        addView(month, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.15f))
        addView(day, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.85f))
        addView(year, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.15f))
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val strip = 36f * resources.displayMetrics.density
        val top = (height - strip) / 2f
        canvas.drawRect(0f, top, width.toFloat(), top + strip, highlight)
    }
}

private fun wheel(context: android.content.Context): NumberPicker =
    NumberPicker(context).apply {
        descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        wrapSelectorWheel = true
        if (Build.VERSION.SDK_INT >= 29) {
            selectionDividerHeight = 0
            textColor = 0xFF111111.toInt()
        }
    }

fun daysInMonth(month: Int, year: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> 31
}

private fun isLeapYear(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
