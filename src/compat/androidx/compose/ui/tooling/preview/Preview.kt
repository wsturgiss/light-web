package androidx.compose.ui.tooling.preview

// Android Studio's preview annotations, which SDK UI files carry. Nothing reads them here.

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Repeatable
annotation class Preview(
    val name: String = "",
    val group: String = "",
    val apiLevel: Int = -1,
    val widthDp: Int = -1,
    val heightDp: Int = -1,
    val locale: String = "",
    val fontScale: Float = 1f,
    val showSystemUi: Boolean = false,
    val showBackground: Boolean = false,
    val backgroundColor: Long = 0,
    val uiMode: Int = 0,
    val device: String = "",
)

interface PreviewParameterProvider<T> {
    val values: Sequence<T>
    val count: Int get() = values.count()
}

annotation class PreviewParameter(
    val provider: kotlin.reflect.KClass<out PreviewParameterProvider<*>>,
    val limit: Int = Int.MAX_VALUE,
)
