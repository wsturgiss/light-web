package java.math

/**
 * `java.math.BigInteger`, on JavaScript's BigInt: arbitrary-precision integers, with the
 * operations tools use. [toByteArray] is Java's big-endian two's complement, minimal length.
 */
class BigInteger private constructor(private val v: JsAny) : Number(), Comparable<BigInteger> {
    constructor(value: String) : this(value, 10)
    constructor(value: String, radix: Int) : this(parse(value.trim(), radix))

    /** A uniformly random value in 0 until 2^[numBits]. */
    constructor(numBits: Int, rnd: java.util.Random) : this(randomBits(numBits, rnd))

    fun add(o: BigInteger) = BigInteger(jsAdd(v, o.v))
    fun subtract(o: BigInteger) = BigInteger(jsSub(v, o.v))
    fun multiply(o: BigInteger) = BigInteger(jsMul(v, o.v))
    fun divide(o: BigInteger) = BigInteger(jsDiv(v, o.v))
    fun mod(m: BigInteger) = BigInteger(jsMod(v, m.v))
    fun remainder(o: BigInteger) = BigInteger(jsRem(v, o.v))
    fun negate() = BigInteger(jsNeg(v))
    fun abs() = if (signum() < 0) negate() else this
    fun pow(exponent: Int) = BigInteger(jsPow(v, exponent))
    fun shiftLeft(n: Int) = BigInteger(jsShl(v, n))
    fun shiftRight(n: Int) = BigInteger(jsShr(v, n))
    fun signum(): Int = jsSign(v)
    /** Bits in the minimal two's complement form, sign bit excluded: of x, or of -x-1 for a negative x. */
    fun bitLength(): Int {
        val m = if (signum() < 0) negate().subtract(ONE) else this
        return m.toString(2).let { if (it == "0") 0 else it.length }
    }

    fun toString(radix: Int): String = jsToString(v, radix)
    override fun toString() = toString(10)

    fun toByteArray(): ByteArray {
        // Two's complement: for negatives, 2^(8n) + v, with n bytes enough for the sign bit.
        val n = bitLength() / 8 + 1
        val unsigned = if (signum() < 0) ONE.shiftLeft(8 * n).add(this) else this
        val hex = unsigned.toString(16).padStart(2 * n, '0')
        return ByteArray(n) { hex.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
    }

    override fun compareTo(other: BigInteger): Int = jsCmp(v, other.v)
    override fun equals(other: Any?) = other is BigInteger && compareTo(other) == 0
    override fun hashCode() = toString().hashCode()

    override fun toLong(): Long = jsToString(jsAsInt64(v), 10).toLong()
    override fun toInt(): Int = toLong().toInt()
    override fun toShort(): Short = toLong().toShort()
    override fun toByte(): Byte = toLong().toByte()
    override fun toDouble(): Double = toString().toDouble()
    override fun toFloat(): Float = toDouble().toFloat()
    fun longValueExact(): Long { val l = toLong(); check(valueOf(l) == this) { "BigInteger out of long range" }; return l }

    companion object {
        val ZERO = valueOf(0)
        val ONE = valueOf(1)
        val TWO = valueOf(2)
        val TEN = valueOf(10)

        fun valueOf(value: Long): BigInteger = BigInteger(jsFromString(value.toString()))

        private fun parse(s: String, radix: Int): JsAny {
            val negative = s.startsWith("-")
            val digits = s.removePrefix("-").removePrefix("+")
            require(digits.isNotEmpty() && digits.all { it.digitToIntOrNull(radix) != null }) { "Zero length BigInteger or bad digit: $s" }
            var acc = jsFromString("0")
            val r = jsFromString(radix.toString())
            for (c in digits) acc = jsAdd(jsMul(acc, r), jsFromString(c.digitToInt(radix).toString()))
            return if (negative) jsNeg(acc) else acc
        }

        private fun randomBits(numBits: Int, rnd: java.util.Random): JsAny {
            require(numBits >= 0)
            if (numBits == 0) return jsFromString("0")
            val bytes = ByteArray((numBits + 7) / 8).also(rnd::nextBytes)
            bytes[0] = (bytes[0].toInt() and (0xff ushr (8 * bytes.size - numBits))).toByte()
            return jsFromString("0x" + bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') })
        }
    }
}

private fun jsFromString(s: String): JsAny = js("BigInt(s)")
private fun jsAdd(a: JsAny, b: JsAny): JsAny = js("a + b")
private fun jsSub(a: JsAny, b: JsAny): JsAny = js("a - b")
private fun jsMul(a: JsAny, b: JsAny): JsAny = js("a * b")
private fun jsDiv(a: JsAny, b: JsAny): JsAny = js("a / b")
private fun jsRem(a: JsAny, b: JsAny): JsAny = js("a % b")
private fun jsMod(a: JsAny, m: JsAny): JsAny = js("((a % m) + m) % m")
private fun jsNeg(a: JsAny): JsAny = js("-a")
private fun jsPow(a: JsAny, e: Int): JsAny = js("a ** BigInt(e)")
private fun jsShl(a: JsAny, n: Int): JsAny = js("a << BigInt(n)")
private fun jsShr(a: JsAny, n: Int): JsAny = js("a >> BigInt(n)")
private fun jsSign(a: JsAny): Int = js("a > 0n ? 1 : a < 0n ? -1 : 0")
private fun jsCmp(a: JsAny, b: JsAny): Int = js("a > b ? 1 : a < b ? -1 : 0")
private fun jsToString(a: JsAny, radix: Int): String = js("a.toString(radix)")
private fun jsAsInt64(a: JsAny): JsAny = js("BigInt.asIntN(64, a)")
