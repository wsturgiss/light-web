package java.time.format

import java.time.DateTimeException
import java.time.LocalDate
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.DateTimeFormatBuilder
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char

/**
 * `DateTimeFormatter.ofPattern` for dates, in English, as a kotlinx-datetime format: y/u (yy
 * two-digit), M/L (number, MMM short name, MMMM full), d, D, E (EEE short, EEEE full), and
 * quoted literals. A letter it doesn't know fails here, when the pattern is made, as Java's does.
 */
class DateTimeFormatter private constructor(private val format: DateTimeFormat<kotlinx.datetime.LocalDate>) {

    fun format(date: LocalDate): String = format.format(date.date)

    companion object {
        fun ofPattern(pattern: String) = DateTimeFormatter(kotlinx.datetime.LocalDate.Format { javaPattern(pattern) })
        val ISO_LOCAL_DATE = DateTimeFormatter(kotlinx.datetime.LocalDate.Formats.ISO)
    }
}

/** `java.time.format.DateTimeParseException`, as LocalDate.parse throws. */
class DateTimeParseException(message: String, val parsedString: CharSequence, val errorIndex: Int = 0) : DateTimeException(message)

private fun DateTimeFormatBuilder.WithDate.javaPattern(pattern: String) {
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        if (c == '\'') {
            val end = pattern.indexOf('\'', i + 1).let { if (it < 0) pattern.length else it }
            if (end == i + 1) char('\'') else pattern.substring(i + 1, end).forEach { char(it) }
            i = end + 1
            continue
        }
        var n = 1
        while (i + n < pattern.length && pattern[i + n] == c) n++
        val pad = if (n >= 2) Padding.ZERO else Padding.NONE
        when (c) {
            'y', 'u' -> if (n == 2) yearTwoDigits(baseYear = 2000) else year(pad)
            'M', 'L' -> when {
                n >= 4 -> monthName(MonthNames.ENGLISH_FULL)
                n == 3 -> monthName(MonthNames.ENGLISH_ABBREVIATED)
                else -> monthNumber(pad)
            }
            'd' -> day(pad)
            'D' -> dayOfYear(if (n >= 3) Padding.ZERO else Padding.NONE)
            'E' -> dayOfWeek(if (n >= 4) DayOfWeekNames.ENGLISH_FULL else DayOfWeekNames.ENGLISH_ABBREVIATED)
            in 'a'..'z', in 'A'..'Z' -> throw IllegalArgumentException("Unsupported pattern letter '$c' in \"$pattern\"")
            else -> repeat(n) { char(c) }
        }
        i += n
    }
}
