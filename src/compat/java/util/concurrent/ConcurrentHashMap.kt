package java.util.concurrent

/**
 * `java.util.concurrent.ConcurrentHashMap`. The page is single-threaded, so it's a plain hash map
 * underneath; a class rather than an alias so it can carry Java's [newKeySet].
 */
class ConcurrentHashMap<K, V> private constructor(private val map: HashMap<K, V>) : MutableMap<K, V> by map {
    constructor() : this(HashMap())
    constructor(initialCapacity: Int) : this(HashMap(initialCapacity))
    constructor(m: Map<out K, V>) : this(HashMap(m))

    fun putIfAbsent(key: K, value: V): V? = map[key] ?: run { map[key] = value; null }

    override fun toString() = map.toString()
    override fun equals(other: Any?) = map == other
    override fun hashCode() = map.hashCode()

    companion object {
        fun <K> newKeySet(): MutableSet<K> = HashSet()
    }
}
