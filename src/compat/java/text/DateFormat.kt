package java.text

import java.util.Date

/** `java.text.DateFormat`'s locale-styled formats, through the browser's `Intl`. */
class DateFormat private constructor(private val date: Int, private val time: Int) {
    fun format(d: Date): String = formatMillis(d.time.toDouble(), STYLES[date], STYLES[time])

    companion object {
        const val FULL = 0
        const val LONG = 1
        const val MEDIUM = 2
        const val SHORT = 3
        private const val NONE = 4
        private val STYLES = listOf("full", "long", "medium", "short", "")

        fun getTimeInstance(style: Int = MEDIUM) = DateFormat(NONE, style)
        fun getDateInstance(style: Int = MEDIUM) = DateFormat(style, NONE)
        fun getDateTimeInstance(dateStyle: Int = MEDIUM, timeStyle: Int = MEDIUM) = DateFormat(dateStyle, timeStyle)
    }
}

private fun formatMillis(ms: Double, dateStyle: String, timeStyle: String): String = js(
    """{
        const o = {};
        if (dateStyle) o.dateStyle = dateStyle;
        if (timeStyle) o.timeStyle = timeStyle;
        return new Intl.DateTimeFormat(undefined, o).format(new Date(ms));
    }"""
)
