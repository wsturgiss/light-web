package lightweb.compat

import java.nio.charset.Charset
import org.xml.sax.ContentHandler
import org.xml.sax.DTDHandler
import org.xml.sax.EntityResolver
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.Locator
import org.xml.sax.SAXNotRecognizedException
import org.xml.sax.SAXParseException
import org.xml.sax.XMLReader
import org.xml.sax.helpers.AttributesImpl

/**
 * A non-validating SAX parser, written here because the browser's XML parsers build a DOM and
 * don't report SAX events. It follows what the JVM's (Xerces) reports, as far as tools see it:
 *
 * - With namespaces on, an element's `uri` and `localName` are resolved, `xmlns` attributes
 *   aren't reported (Xerces' default), and prefix mappings are. With them off, `uri` and
 *   `localName` are empty and `qName` is the name as written.
 * - Nothing outside the document is read: an external DTD or entity is never fetched (the
 *   EntityResolver is asked, and whatever it returns is ignored). Internal entities declared in
 *   the DTD's internal subset are expanded.
 * - An undeclared entity is a fatal error, as the spec requires, unless the document has an
 *   external DTD, which could have declared it; then it's reported to `skippedEntity`, as Xerces
 *   does when it hasn't read that DTD.
 * - Text between markup is reported in one `characters` call; whitespace is never "ignorable",
 *   since that needs a validating parser.
 */
class SaxXmlReader(val namespaceAware: Boolean) : XMLReader {
    override var contentHandler: ContentHandler? = null
    override var dtdHandler: DTDHandler? = null
    override var entityResolver: EntityResolver? = null
    override var errorHandler: ErrorHandler? = null
    private val features = HashMap<String, Boolean>()

    override fun getFeature(name: String): Boolean = when (name) {
        NAMESPACES -> namespaceAware
        else -> features[name] ?: false
    }

    override fun setFeature(name: String, value: Boolean) {
        features[name] = value
    }

    override fun getProperty(name: String): Any? = throw SAXNotRecognizedException(name)
    override fun setProperty(name: String, value: Any?): Unit = throw SAXNotRecognizedException(name)

    override fun parse(systemId: String): Unit = throw SAXNotRecognizedException("Fetching $systemId isn't supported; pass an InputSource with a stream")

    override fun parse(input: InputSource) {
        val text = input.characterStream?.readText()
            ?: decode(input.byteStream?.readBytes() ?: throw SAXNotRecognizedException("InputSource has no stream"), input.encoding)
        Parser(text, input.systemId).parseDocument()
    }

    private inner class Parser(raw: String, private val docSystemId: String?) : Locator {
        private val s = if ('\r' in raw) raw.replace("\r\n", "\n").replace('\r', '\n') else raw
        private var i = if (s.startsWith('﻿')) 1 else 0
        private val handler = contentHandler
        private val entities = HashMap<String, String>()
        private var externalDtd = false
        private val text = StringBuilder()
        private val nsStack = ArrayList<Map<String, String>>()
        private var ns: Map<String, String> = mapOf("xml" to XML_NS)
        private var expanding = 0

        override val publicId: String? get() = null
        override val systemId: String? get() = docSystemId
        override val lineNumber: Int get() = 1 + (0 until minOf(i, s.length)).count { s[it] == '\n' }
        override val columnNumber: Int get() = i - (s.lastIndexOf('\n', i - 1) + 1) + 1

        fun parseDocument() {
            handler?.setDocumentLocator(this)
            handler?.startDocument()
            misc(allowDoctype = true)
            if (i >= s.length || s[i] != '<') fail("Content is not allowed in prolog.")
            element()
            misc(allowDoctype = false)
            if (i < s.length) fail("Content is not allowed in trailing section.")
            handler?.endDocument()
        }

        private fun fail(message: String): Nothing {
            val e = SAXParseException(message, null, docSystemId, lineNumber, columnNumber)
            errorHandler?.fatalError(e)
            throw e
        }

        private fun startsWith(prefix: String) = s.startsWith(prefix, i)

        private fun skipSpace() {
            while (i < s.length && s[i].isXmlSpace()) i++
        }

        private fun expect(c: Char) {
            if (i >= s.length || s[i] != c) fail("Expected '$c'.")
            i++
        }

        /** Comments, processing instructions and whitespace, and the DOCTYPE if [allowDoctype]. */
        private fun misc(allowDoctype: Boolean) {
            var doctypeSeen = false
            while (true) {
                skipSpace()
                when {
                    startsWith("<?") -> pi()
                    startsWith("<!--") -> comment()
                    startsWith("<!DOCTYPE") -> {
                        if (!allowDoctype || doctypeSeen) fail("Already seen doctype.")
                        doctypeSeen = true
                        doctype()
                    }
                    else -> return
                }
            }
        }

        private fun pi() {
            val end = s.indexOf("?>", i + 2).takeIf { it >= 0 } ?: fail("XML document structures must start and end within the same entity.")
            val body = s.substring(i + 2, end)
            i = end + 2
            val target = body.takeWhile { !it.isXmlSpace() }
            if (target.equals("xml", ignoreCase = true)) return
            flushText()
            handler?.processingInstruction(target, body.drop(target.length).trimStart())
        }

        private fun comment() {
            val end = s.indexOf("-->", i + 4).takeIf { it >= 0 } ?: fail("XML document structures must start and end within the same entity.")
            i = end + 3
        }

        private fun doctype() {
            i += "<!DOCTYPE".length
            skipSpace()
            name()
            skipSpace()
            var publicId: String? = null
            var systemId: String? = null
            if (startsWith("PUBLIC")) {
                i += 6; skipSpace(); publicId = quoted(); skipSpace(); systemId = quoted()
            } else if (startsWith("SYSTEM")) {
                i += 6; skipSpace(); systemId = quoted()
            }
            if (systemId != null) {
                externalDtd = true
                // Asked, as Java's parser asks; what it returns isn't read.
                entityResolver?.resolveEntity(publicId, systemId)
            }
            skipSpace()
            if (i < s.length && s[i] == '[') {
                i++
                internalSubset()
            }
            skipSpace()
            expect('>')
        }

        private fun internalSubset() {
            while (true) {
                skipSpace()
                when {
                    i >= s.length -> fail("XML document structures must start and end within the same entity.")
                    s[i] == ']' -> { i++; return }
                    startsWith("<!--") -> comment()
                    startsWith("<?") -> pi()
                    startsWith("<!ENTITY") -> entityDecl()
                    s[i] == '%' -> { externalDtd = true; i = s.indexOf(';', i).takeIf { it >= 0 }?.plus(1) ?: fail("Bad parameter entity reference.") }
                    startsWith("<!") -> skipDecl()
                    else -> fail("The markup declarations contained or pointed to by the document type declaration must be well-formed.")
                }
            }
        }

        private fun skipDecl() {
            while (i < s.length && s[i] != '>') {
                if (s[i] == '"' || s[i] == '\'') quoted() else i++
            }
            expect('>')
        }

        private fun entityDecl() {
            i += "<!ENTITY".length
            skipSpace()
            if (i < s.length && s[i] == '%') { externalDtd = true; skipDecl(); return }
            val name = name()
            skipSpace()
            if (i < s.length && (s[i] == '"' || s[i] == '\'')) {
                val value = quoted()
                entities.putIfAbsentHere(name, value)
                skipSpace()
                expect('>')
            } else {
                skipDecl()
            }
        }

        private fun <K, V> HashMap<K, V>.putIfAbsentHere(key: K, value: V) {
            if (key !in this) this[key] = value
        }

        private fun quoted(): String {
            if (i >= s.length || (s[i] != '"' && s[i] != '\'')) fail("Expected a quoted string.")
            val q = s[i]
            val end = s.indexOf(q, i + 1).takeIf { it >= 0 } ?: fail("XML document structures must start and end within the same entity.")
            return s.substring(i + 1, end).also { i = end + 1 }
        }

        private fun name(): String {
            val start = i
            if (i >= s.length || !s[i].isNameStart()) fail("The markup in the document must be well-formed: expected a name.")
            i++
            while (i < s.length && s[i].isNameChar()) i++
            return s.substring(start, i)
        }

        private fun element() {
            expect('<')
            val qName = name()
            val attrs = AttributesImpl()
            val rawNames = ArrayList<String>()
            val rawValues = ArrayList<String>()
            while (true) {
                val hadSpace = i < s.length && s[i].isXmlSpace()
                skipSpace()
                if (i >= s.length) fail("XML document structures must start and end within the same entity.")
                if (s[i] == '>' || startsWith("/>")) break
                if (!hadSpace) fail("Element type \"$qName\" must be followed by either attribute specifications, \">\" or \"/>\".")
                val an = name()
                skipSpace()
                expect('=')
                skipSpace()
                if (an in rawNames) fail("Attribute \"$an\" was already specified for element \"$qName\".")
                rawNames += an
                rawValues += attributeValue(quoted())
            }
            val empty = startsWith("/>")
            i += if (empty) 2 else 1

            val prefixes = ArrayList<String>()
            if (namespaceAware) {
                var scope: MutableMap<String, String>? = null
                for (k in rawNames.indices) {
                    val an = rawNames[k]
                    val prefix = when {
                        an == "xmlns" -> ""
                        an.startsWith("xmlns:") -> an.substring(6)
                        else -> continue
                    }
                    if (scope == null) scope = HashMap(ns)
                    scope[prefix] = rawValues[k]
                    prefixes += prefix
                }
                nsStack += ns
                if (scope != null) ns = scope
                for (k in rawNames.indices) {
                    val an = rawNames[k]
                    if (an == "xmlns" || an.startsWith("xmlns:")) continue
                    val colon = an.indexOf(':')
                    val uri = if (colon < 0) "" else ns[an.substring(0, colon)] ?: fail("The prefix \"${an.substring(0, colon)}\" for attribute \"$an\" associated with an element type \"$qName\" is not bound.")
                    attrs.addAttribute(uri, an.substring(colon + 1), an, "CDATA", rawValues[k])
                }
            } else {
                for (k in rawNames.indices) attrs.addAttribute("", "", rawNames[k], "CDATA", rawValues[k])
            }
            val (uri, local) = resolve(qName)

            flushText()
            for (p in prefixes) handler?.startPrefixMapping(p, ns[p].orEmpty())
            handler?.startElement(uri, local, qName, attrs)
            if (!empty) content(qName)
            flushText()
            handler?.endElement(uri, local, qName)
            if (namespaceAware) {
                for (p in prefixes) handler?.endPrefixMapping(p)
                ns = nsStack.removeAt(nsStack.lastIndex)
            }
        }

        private fun resolve(qName: String): Pair<String, String> {
            if (!namespaceAware) return "" to ""
            val colon = qName.indexOf(':')
            val prefix = if (colon < 0) "" else qName.substring(0, colon)
            val uri = ns[prefix] ?: if (colon < 0) "" else fail("The prefix \"$prefix\" for element \"$qName\" is not bound.")
            return uri to qName.substring(colon + 1)
        }

        private fun content(qName: String) {
            while (true) {
                if (i >= s.length) fail("XML document structures must start and end within the same entity.")
                val c = s[i]
                when {
                    c == '<' -> when {
                        startsWith("</") -> {
                            i += 2
                            val end = name()
                            if (end != qName) fail("The element type \"$qName\" must be terminated by the matching end-tag \"</$qName>\".")
                            skipSpace()
                            expect('>')
                            return
                        }
                        startsWith("<!--") -> comment()
                        startsWith("<![CDATA[") -> {
                            val end = s.indexOf("]]>", i + 9).takeIf { it >= 0 } ?: fail("The CDATA section must end with \"]]>\".")
                            text.append(s, i + 9, end)
                            i = end + 3
                        }
                        startsWith("<?") -> pi()
                        else -> element()
                    }
                    c == '&' -> reference(text, inAttribute = false)
                    else -> {
                        val start = i
                        while (i < s.length && s[i] != '<' && s[i] != '&') i++
                        text.append(s, start, i)
                    }
                }
            }
        }

        private fun flushText() {
            if (text.isEmpty()) return
            val chars = text.toString().toCharArray()
            text.setLength(0)
            handler?.characters(chars, 0, chars.size)
        }

        /** An attribute's value with references replaced and whitespace characters as spaces. */
        private fun attributeValue(raw: String): String {
            if ('&' !in raw && raw.none { it == '\n' || it == '\t' || it == '<' }) return raw
            val out = StringBuilder(raw.length)
            var k = 0
            while (k < raw.length) {
                val c = raw[k]
                when (c) {
                    '<' -> fail("The value of attribute must not contain the '<' character.")
                    '\n', '\t' -> { out.append(' '); k++ }
                    '&' -> {
                        val end = raw.indexOf(';', k).takeIf { it > k } ?: fail("The entity name must immediately follow the '&' in the entity reference.")
                        appendReference(raw.substring(k + 1, end), out, inAttribute = true)
                        k = end + 1
                    }
                    else -> { out.append(c); k++ }
                }
            }
            return out.toString()
        }

        private fun reference(out: StringBuilder, inAttribute: Boolean) {
            val end = s.indexOf(';', i).takeIf { it > i + 1 && s.substring(i + 1, it).none { c -> c.isXmlSpace() || c == '<' || c == '&' } }
                ?: fail("The entity name must immediately follow the '&' in the entity reference.")
            val name = s.substring(i + 1, end)
            i = end + 1
            appendReference(name, out, inAttribute)
        }

        private fun appendReference(name: String, out: StringBuilder, inAttribute: Boolean) {
            if (name.startsWith("#")) {
                val code = (if (name.startsWith("#x")) name.substring(2).toIntOrNull(16) else name.substring(1).toIntOrNull())
                    ?.takeIf { it in 1..0x10FFFF && it !in 0xD800..0xDFFF }
                    ?: fail("Character reference \"&$name\" is an invalid XML character.")
                if (code >= 0x10000) {
                    val v = code - 0x10000
                    out.append((0xD800 + (v shr 10)).toChar()).append((0xDC00 + (v and 0x3FF)).toChar())
                } else {
                    out.append(code.toChar())
                }
                return
            }
            PREDEFINED[name]?.let { out.append(it); return }
            val value = entities[name]
            if (value == null) {
                if (!externalDtd) fail("The entity \"$name\" was referenced, but not declared.")
                if (!inAttribute) {
                    flushText()
                    handler?.skippedEntity(name)
                }
                return
            }
            if (++expanding > MAX_EXPANSION_DEPTH) fail("Recursive entity reference \"$name\".")
            try {
                // Replacement text, with its own references expanded; markup in it is read as text.
                var k = 0
                while (k < value.length) {
                    val c = value[k]
                    if (c == '&') {
                        val e = value.indexOf(';', k).takeIf { it > k } ?: fail("The entity name must immediately follow the '&' in the entity reference.")
                        appendReference(value.substring(k + 1, e), out, inAttribute)
                        k = e + 1
                    } else {
                        out.append(if (inAttribute && (c == '\n' || c == '\t')) ' ' else c)
                        k++
                    }
                }
            } finally {
                expanding--
            }
        }
    }

    private companion object {
        const val NAMESPACES = "http://xml.org/sax/features/namespaces"
        const val XML_NS = "http://www.w3.org/XML/1998/namespace"
        const val MAX_EXPANSION_DEPTH = 32
        val PREDEFINED = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'")
    }
}

private fun Char.isXmlSpace() = this == ' ' || this == '\n' || this == '\t' || this == '\r'

private fun Char.isNameStart() = isLetter() || this == '_' || this == ':' || code >= 0x80 && !isWhitespace()

private fun Char.isNameChar() = isNameStart() || isDigit() || this == '-' || this == '.' || this == '·'

/** Bytes as text: a byte order mark, else the XML declaration's encoding, else UTF-8 (as the spec says). */
private fun decode(bytes: ByteArray, declared: String?): String {
    fun b(k: Int) = if (k < bytes.size) bytes[k].toInt() and 0xff else -1
    if (b(0) == 0xEF && b(1) == 0xBB && b(2) == 0xBF) return bytes.decodeToString(3, bytes.size)
    if ((b(0) == 0xFE && b(1) == 0xFF) || (b(0) == 0xFF && b(1) == 0xFE)) return Charset.UTF_16.decode(bytes)
    val name = declared ?: run {
        val head = bytes.decodeToString(0, minOf(bytes.size, 200))
        Regex("""^<\?xml[^>]*encoding\s*=\s*["']([A-Za-z0-9._-]+)["']""").find(head)?.groupValues?.get(1)
    }
    val charset = name?.let { if (Charset.isSupported(it)) Charset.forName(it) else null } ?: Charset.UTF_8
    return charset.decode(bytes)
}
