package java.time

import java.time.format.DateTimeFormatter

/** `java.time.LocalDate`: a calendar date. `now()` is today in the browser's time zone. */
class LocalDate private constructor(val year: Int, val monthValue: Int, val dayOfMonth: Int) : Comparable<LocalDate> {

    val dayOfWeek: DayOfWeek get() = DayOfWeek.entries[(toEpochDay() + 3).mod(7)]
    val dayOfYear: Int get() = (toEpochDay() - of(year, 1, 1).toEpochDay()).toInt() + 1

    fun toEpochDay(): Long {
        // Days from civil (Howard Hinnant's algorithm).
        val y = if (monthValue <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (monthValue + 9) % 12
        val doy = (153 * mp + 2) / 5 + dayOfMonth - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468
    }

    fun plusDays(n: Long) = ofEpochDay(toEpochDay() + n)
    fun minusDays(n: Long) = plusDays(-n)
    fun plusWeeks(n: Long) = plusDays(7 * n)
    fun minusWeeks(n: Long) = plusDays(-7 * n)
    fun isBefore(other: LocalDate) = this < other
    fun isAfter(other: LocalDate) = this > other
    fun format(formatter: DateTimeFormatter): String = formatter.format(this)

    override fun compareTo(other: LocalDate) = toEpochDay().compareTo(other.toEpochDay())
    override fun equals(other: Any?) = other is LocalDate && other.toEpochDay() == toEpochDay()
    override fun hashCode() = toEpochDay().hashCode()
    override fun toString() =
        "${year.toString().padStart(4, '0')}-${monthValue.toString().padStart(2, '0')}-${dayOfMonth.toString().padStart(2, '0')}"

    companion object {
        fun of(year: Int, month: Int, dayOfMonth: Int): LocalDate {
            require(month in 1..12 && dayOfMonth in 1..31) { "Invalid date $year-$month-$dayOfMonth" }
            return LocalDate(year, month, dayOfMonth)
        }

        fun ofEpochDay(epochDay: Long): LocalDate {
            val z = epochDay + 719468
            val era = (if (z >= 0) z else z - 146096) / 146097
            val doe = z - era * 146097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
            val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
            val y = (yoe + era * 400 + (if (m <= 2) 1 else 0)).toInt()
            return LocalDate(y, m, d)
        }

        fun parse(text: CharSequence): LocalDate {
            val m = Regex("""(-?\d{4,})-(\d{2})-(\d{2})""").matchEntire(text) ?: throw IllegalArgumentException("Text '$text' could not be parsed")
            return of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }

        fun now(): LocalDate = parse(todayIso())
    }
}

enum class DayOfWeek { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;
    val value: Int get() = ordinal + 1
}

private fun todayIso(): String = js(
    "(() => { const d = new Date(); return d.getFullYear().toString().padStart(4,'0') + '-' + String(d.getMonth()+1).padStart(2,'0') + '-' + String(d.getDate()).padStart(2,'0'); })()"
)
