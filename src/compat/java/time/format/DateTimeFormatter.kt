package java.time.format

import java.time.LocalDate

/**
 * `DateTimeFormatter.ofPattern` for dates, in English: y, M (number, MMM short, MMMM long),
 * d, E (EEE short, EEEE long), and quoted literals.
 */
class DateTimeFormatter private constructor(private val pattern: String) {

    fun format(date: LocalDate): String = buildString {
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            if (c == '\'') {
                val end = pattern.indexOf('\'', i + 1).let { if (it < 0) pattern.length else it }
                append(pattern, i + 1, end)
                i = end + 1
                continue
            }
            var n = 1
            while (i + n < pattern.length && pattern[i + n] == c) n++
            append(
                when (c) {
                    'y', 'u' -> if (n == 2) (date.year % 100).toString().padStart(2, '0') else date.year.toString().padStart(n, '0')
                    'M', 'L' -> when {
                        n >= 4 -> MONTHS[date.monthValue - 1]
                        n == 3 -> MONTHS[date.monthValue - 1].take(3)
                        else -> date.monthValue.toString().padStart(n, '0')
                    }
                    'd' -> date.dayOfMonth.toString().padStart(n, '0')
                    'D' -> date.dayOfYear.toString().padStart(n, '0')
                    'E' -> DAYS[date.dayOfWeek.ordinal].let { if (n >= 4) it else it.take(3) }
                    else -> c.toString().repeat(n)
                }
            )
            i += n
        }
    }

    companion object {
        private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December")
        private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

        fun ofPattern(pattern: String) = DateTimeFormatter(pattern)
        val ISO_LOCAL_DATE = DateTimeFormatter("yyyy-MM-dd")
    }
}
