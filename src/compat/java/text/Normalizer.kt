package java.text

/** `java.text.Normalizer`, on JavaScript's `String.prototype.normalize`. */
object Normalizer {
    enum class Form { NFD, NFC, NFKD, NFKC }

    fun normalize(src: CharSequence, form: Form): String = jsNormalize(src.toString(), form.name)

    fun isNormalized(src: CharSequence, form: Form): Boolean = normalize(src, form) == src.toString()
}

private fun jsNormalize(s: String, form: String): String = js("s.normalize(form)")
