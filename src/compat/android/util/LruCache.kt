package android.util

/**
 * `android.util.LruCache`: up to [maxSize] (by [sizeOf], 1 per entry unless overridden), the
 * least recently used evicted first.
 */
open class LruCache<K : Any, V : Any>(private var maxSize: Int) {
    private val map = LinkedHashMap<K, V>()
    private var size = 0

    fun get(key: K): V? {
        val v = map.remove(key) ?: return null
        map[key] = v // now the most recently used
        return v
    }

    fun put(key: K, value: V): V? {
        val previous = map.remove(key)
        if (previous != null) size -= safeSizeOf(key, previous)
        map[key] = value
        size += safeSizeOf(key, value)
        previous?.let { entryRemoved(false, key, it, value) }
        trimToSize(maxSize)
        return previous
    }

    fun remove(key: K): V? {
        val previous = map.remove(key) ?: return null
        size -= safeSizeOf(key, previous)
        entryRemoved(false, key, previous, null)
        return previous
    }

    fun evictAll() = trimToSize(-1)

    fun resize(maxSize: Int) { this.maxSize = maxSize; trimToSize(maxSize) }

    open fun trimToSize(maxSize: Int) {
        while (size > maxSize && map.isNotEmpty()) {
            val eldest = map.keys.first()
            val value = map.remove(eldest)!!
            size -= safeSizeOf(eldest, value)
            entryRemoved(true, eldest, value, null)
        }
    }

    fun size(): Int = size
    fun maxSize(): Int = maxSize

    /** Least recently used first, as Android's. */
    fun snapshot(): MutableMap<K, V> = LinkedHashMap(map)

    protected open fun sizeOf(key: K, value: V): Int = 1
    protected open fun entryRemoved(evicted: Boolean, key: K, oldValue: V, newValue: V?) {}

    private fun safeSizeOf(key: K, value: V): Int = sizeOf(key, value).also { check(it >= 0) { "Negative size: $key=$value" } }
}
