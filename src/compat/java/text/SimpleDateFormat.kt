package java.text

import java.util.Date
import java.util.Locale
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * `java.text.SimpleDateFormat`'s formatting, in the browser's time zone, for numeric patterns:
 * y M d H h m s S a and quoted text. Month and day names (MMM, EEE) aren't localised here.
 */
class SimpleDateFormat(private val pattern: String, @Suppress("unused") private val locale: Locale? = null) {
    fun format(date: Date): String {
        val t = Instant.fromEpochMilliseconds(date.time).toLocalDateTime(TimeZone.currentSystemDefault())
        val out = StringBuilder()
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            if (c == '\'') {
                val end = pattern.indexOf('\'', i + 1).let { if (it < 0) pattern.length else it }
                out.append(if (end == i + 1) "'" else pattern.substring(i + 1, end))
                i = end + 1
                continue
            }
            var n = 1
            while (i + n < pattern.length && pattern[i + n] == c) n++
            fun num(v: Int) = out.append(v.toString().padStart(n, '0'))
            when (c) {
                'y' -> out.append(if (n == 2) (t.year % 100).toString().padStart(2, '0') else t.year.toString().padStart(n, '0'))
                'M' -> if (n >= 3) out.append(MONTHS[t.month.ordinal].let { if (n == 3) it.take(3) else it }) else num(t.month.ordinal + 1)
                'd' -> num(t.day)
                'H' -> num(t.hour)
                'h' -> num((t.hour + 11) % 12 + 1)
                'm' -> num(t.minute)
                's' -> num(t.second)
                'S' -> num(t.nanosecond / 1_000_000)
                'a' -> out.append(if (t.hour < 12) "AM" else "PM")
                'E' -> out.append(DAYS[t.dayOfWeek.ordinal].let { if (n <= 3) it.take(3) else it })
                else -> repeat(n) { out.append(c) }
            }
            i += n
        }
        return out.toString()
    }
}

private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
