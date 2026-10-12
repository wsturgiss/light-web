package java.util.concurrent.atomic

// The page has one thread, so an atomic is a plain holder with Java's method names.

class AtomicBoolean(initialValue: Boolean = false) {
    private var value = initialValue
    fun get(): Boolean = value
    fun set(newValue: Boolean) { value = newValue }
    fun getAndSet(newValue: Boolean): Boolean = value.also { value = newValue }
    fun compareAndSet(expect: Boolean, update: Boolean): Boolean =
        if (value == expect) { value = update; true } else false
    fun lazySet(newValue: Boolean) = set(newValue)
    override fun toString() = value.toString()
}

class AtomicInteger(initialValue: Int = 0) : Number() {
    private var value = initialValue
    fun get(): Int = value
    fun set(newValue: Int) { value = newValue }
    fun getAndSet(newValue: Int): Int = value.also { value = newValue }
    fun incrementAndGet(): Int = ++value
    fun decrementAndGet(): Int = --value
    fun getAndIncrement(): Int = value++
    fun getAndDecrement(): Int = value--
    fun addAndGet(delta: Int): Int { value += delta; return value }
    fun getAndAdd(delta: Int): Int = value.also { value += delta }
    fun compareAndSet(expect: Int, update: Int): Boolean = if (value == expect) { value = update; true } else false
    fun updateAndGet(f: (Int) -> Int): Int { value = f(value); return value }
    fun getAndUpdate(f: (Int) -> Int): Int = value.also { value = f(it) }
    override fun toByte() = value.toByte()
    override fun toShort() = value.toShort()
    override fun toInt() = value
    override fun toLong() = value.toLong()
    override fun toFloat() = value.toFloat()
    override fun toDouble() = value.toDouble()
    override fun toString() = value.toString()
}

class AtomicLong(initialValue: Long = 0) : Number() {
    private var value = initialValue
    fun get(): Long = value
    fun set(newValue: Long) { value = newValue }
    fun getAndSet(newValue: Long): Long = value.also { value = newValue }
    fun incrementAndGet(): Long = ++value
    fun decrementAndGet(): Long = --value
    fun getAndIncrement(): Long = value++
    fun getAndDecrement(): Long = value--
    fun addAndGet(delta: Long): Long { value += delta; return value }
    fun getAndAdd(delta: Long): Long = value.also { value += delta }
    fun compareAndSet(expect: Long, update: Long): Boolean = if (value == expect) { value = update; true } else false
    fun updateAndGet(f: (Long) -> Long): Long { value = f(value); return value }
    fun getAndUpdate(f: (Long) -> Long): Long = value.also { value = f(it) }
    override fun toByte() = value.toByte()
    override fun toShort() = value.toShort()
    override fun toInt() = value.toInt()
    override fun toLong() = value
    override fun toFloat() = value.toFloat()
    override fun toDouble() = value.toDouble()
    override fun toString() = value.toString()
}

class AtomicReference<V>(initialValue: V) {
    private var value = initialValue
    fun get(): V = value
    fun set(newValue: V) { value = newValue }
    fun getAndSet(newValue: V): V = value.also { value = newValue }
    fun compareAndSet(expect: V, update: V): Boolean = if (value === expect) { value = update; true } else false
    fun updateAndGet(f: (V) -> V): V { value = f(value); return value }
    fun getAndUpdate(f: (V) -> V): V = value.also { value = f(it) }
}
