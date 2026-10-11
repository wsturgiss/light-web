package org.xml.sax

import java.io.InputStream
import java.io.Reader

// org.xml.sax, the interfaces and classes tools use, for the parser in
// lightweb.compat.SaxXmlReader (made by javax.xml.parsers.SAXParserFactory).

open class SAXException(message: String? = null, val exception: Exception? = null) : Exception(message, exception) {
    constructor(e: Exception) : this(e.message, e)
}

open class SAXParseException(
    message: String?,
    val publicId: String? = null,
    val systemId: String? = null,
    val lineNumber: Int = -1,
    val columnNumber: Int = -1,
) : SAXException(message) {
    override fun toString() = "org.xml.sax.SAXParseException; lineNumber: $lineNumber; columnNumber: $columnNumber; $message"
}

class SAXNotRecognizedException(message: String? = null) : SAXException(message)

class SAXNotSupportedException(message: String? = null) : SAXException(message)

interface Attributes {
    val length: Int
    // By index, non-null: Java returns null only for an index out of range, where these throw.
    fun getURI(index: Int): String
    fun getLocalName(index: Int): String
    fun getQName(index: Int): String
    fun getType(index: Int): String
    fun getValue(index: Int): String
    fun getIndex(uri: String, localName: String): Int
    fun getIndex(qName: String): Int
    fun getType(uri: String, localName: String): String?
    fun getType(qName: String): String?
    fun getValue(uri: String, localName: String): String?
    fun getValue(qName: String): String?
}

interface Locator {
    val publicId: String?
    val systemId: String?
    val lineNumber: Int
    val columnNumber: Int
}

interface ContentHandler {
    fun setDocumentLocator(locator: Locator)
    fun startDocument()
    fun endDocument()
    fun startPrefixMapping(prefix: String, uri: String)
    fun endPrefixMapping(prefix: String)
    fun startElement(uri: String, localName: String, qName: String, atts: Attributes)
    fun endElement(uri: String, localName: String, qName: String)
    fun characters(ch: CharArray, start: Int, length: Int)
    fun ignorableWhitespace(ch: CharArray, start: Int, length: Int)
    fun processingInstruction(target: String, data: String)
    fun skippedEntity(name: String)
}

interface DTDHandler {
    fun notationDecl(name: String, publicId: String?, systemId: String?)
    fun unparsedEntityDecl(name: String, publicId: String?, systemId: String?, notationName: String)
}

fun interface EntityResolver {
    fun resolveEntity(publicId: String?, systemId: String?): InputSource?
}

interface ErrorHandler {
    fun warning(exception: SAXParseException)
    fun error(exception: SAXParseException)
    fun fatalError(exception: SAXParseException)
}

class InputSource() {
    var byteStream: InputStream? = null
    var characterStream: Reader? = null
    var systemId: String? = null
    var publicId: String? = null
    var encoding: String? = null

    constructor(systemId: String) : this() { this.systemId = systemId }
    constructor(byteStream: InputStream) : this() { this.byteStream = byteStream }
    constructor(characterStream: Reader) : this() { this.characterStream = characterStream }
}

interface XMLReader {
    var contentHandler: ContentHandler?
    var dtdHandler: DTDHandler?
    var entityResolver: EntityResolver?
    var errorHandler: ErrorHandler?
    fun getFeature(name: String): Boolean
    fun setFeature(name: String, value: Boolean)
    fun getProperty(name: String): Any?
    fun setProperty(name: String, value: Any?)
    fun parse(input: InputSource)
    fun parse(systemId: String)
}
