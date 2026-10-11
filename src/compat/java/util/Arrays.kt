package java.util

/** `java.util.Arrays`, as far as tools use it. */
object Arrays {
    /** Java's contract: the index if found, else `-(insertion point) - 1`. */
    fun binarySearch(a: IntArray, key: Int): Int = binarySearch(a, 0, a.size, key)

    fun binarySearch(a: IntArray, fromIndex: Int, toIndex: Int, key: Int): Int {
        var lo = fromIndex
        var hi = toIndex - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = a[mid]
            when {
                v < key -> lo = mid + 1
                v > key -> hi = mid - 1
                else -> return mid
            }
        }
        return -(lo + 1)
    }

    fun binarySearch(a: LongArray, key: Long): Int {
        var lo = 0
        var hi = a.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            when {
                a[mid] < key -> lo = mid + 1
                a[mid] > key -> hi = mid - 1
                else -> return mid
            }
        }
        return -(lo + 1)
    }

    fun fill(a: IntArray, value: Int) = a.fill(value)
    fun fill(a: ByteArray, value: Byte) = a.fill(value)
    fun equals(a: ByteArray?, b: ByteArray?): Boolean = a.contentEquals(b)
    fun hashCode(a: ByteArray?): Int = a.contentHashCode()
    fun <T> asList(vararg a: T): List<T> = a.asList()
}
