package java.util

/** Enough of `java.util.Locale` for keyboard layouts to name their language. */
class Locale(val language: String, val country: String = "") {
    override fun toString() = if (country.isEmpty()) language else "${language}_$country"
    override fun equals(other: Any?) = other is Locale && other.language == language && other.country == country
    override fun hashCode() = toString().hashCode()

    companion object {
        val ENGLISH = Locale("en")
        val FRENCH = Locale("fr")
        val GERMAN = Locale("de")
        val ROOT = Locale("")
        val US = Locale("en", "US")
        val UK = Locale("en", "GB")
        fun getDefault(): Locale = US
    }
}
