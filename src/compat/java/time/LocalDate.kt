package java.time

import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/**
 * `java.time.LocalDate`: a calendar date, on kotlinx-datetime's. `now()` is today in the
 * browser's time zone.
 */
class LocalDate private constructor(internal val date: kotlinx.datetime.LocalDate) : Comparable<LocalDate> {
    val year: Int get() = date.year
    val monthValue: Int get() = date.month.number
    val dayOfMonth: Int get() = date.day
    val dayOfYear: Int get() = date.dayOfYear
    val dayOfWeek: DayOfWeek get() = DayOfWeek.entries[date.dayOfWeek.ordinal]

    fun toEpochDay(): Long = date.toEpochDays()

    fun plusDays(n: Long) = LocalDate(date.plus(n, DateTimeUnit.DAY))
    fun minusDays(n: Long) = plusDays(-n)
    fun plusWeeks(n: Long) = plusDays(7 * n)
    fun minusWeeks(n: Long) = plusDays(-7 * n)
    fun isBefore(other: LocalDate) = this < other
    fun isAfter(other: LocalDate) = this > other
    fun format(formatter: DateTimeFormatter): String = formatter.format(this)

    override fun compareTo(other: LocalDate) = date.compareTo(other.date)
    override fun equals(other: Any?) = other is LocalDate && other.date == date
    override fun hashCode() = date.hashCode()
    override fun toString() = date.toString()

    companion object {
        fun of(year: Int, month: Int, dayOfMonth: Int): LocalDate =
            try {
                LocalDate(kotlinx.datetime.LocalDate(year, month, dayOfMonth))
            } catch (e: IllegalArgumentException) {
                throw DateTimeException("Invalid date $year-$month-$dayOfMonth: ${e.message}")
            }

        fun ofEpochDay(epochDay: Long) = LocalDate(kotlinx.datetime.LocalDate.fromEpochDays(epochDay))

        fun parse(text: CharSequence): LocalDate =
            try {
                LocalDate(kotlinx.datetime.LocalDate.parse(text))
            } catch (e: IllegalArgumentException) {
                throw DateTimeParseException("Text '$text' could not be parsed: ${e.message}", text)
            }

        fun now(): LocalDate = LocalDate(Clock.System.todayIn(TimeZone.currentSystemDefault()))
    }
}

/** `java.time.DateTimeException`, which `of` throws for a date that doesn't exist. */
open class DateTimeException(message: String) : RuntimeException(message)

enum class DayOfWeek { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;
    val value: Int get() = ordinal + 1
}
