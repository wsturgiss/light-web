package org.xml.sax.helpers

import org.xml.sax.Attributes
import org.xml.sax.ContentHandler
import org.xml.sax.DTDHandler
import org.xml.sax.EntityResolver
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.Locator
import org.xml.sax.SAXParseException

/** Does nothing with every event, except a fatal error, which it throws (as Java's does). */
open class DefaultHandler : EntityResolver, DTDHandler, ContentHandler, ErrorHandler {
    override fun resolveEntity(publicId: String?, systemId: String?): InputSource? = null
    override fun notationDecl(name: String, publicId: String?, systemId: String?) {}
    override fun unparsedEntityDecl(name: String, publicId: String?, systemId: String?, notationName: String) {}
    override fun setDocumentLocator(locator: Locator) {}
    override fun startDocument() {}
    override fun endDocument() {}
    override fun startPrefixMapping(prefix: String, uri: String) {}
    override fun endPrefixMapping(prefix: String) {}
    override fun startElement(uri: String, localName: String, qName: String, atts: Attributes) {}
    override fun endElement(uri: String, localName: String, qName: String) {}
    override fun characters(ch: CharArray, start: Int, length: Int) {}
    override fun ignorableWhitespace(ch: CharArray, start: Int, length: Int) {}
    override fun processingInstruction(target: String, data: String) {}
    override fun skippedEntity(name: String) {}
    override fun warning(exception: SAXParseException) {}
    override fun error(exception: SAXParseException) {}
    override fun fatalError(exception: SAXParseException): Unit = throw exception
}

/** A list of attributes that can be copied and changed. */
open class AttributesImpl() : Attributes {
    private val uris = ArrayList<String>()
    private val locals = ArrayList<String>()
    private val qNames = ArrayList<String>()
    private val types = ArrayList<String>()
    private val values = ArrayList<String>()

    constructor(atts: Attributes) : this() {
        setAttributes(atts)
    }

    override val length: Int get() = qNames.size

    override fun getURI(index: Int): String = uris[index]
    override fun getLocalName(index: Int): String = locals[index]
    override fun getQName(index: Int): String = qNames[index]
    override fun getType(index: Int): String = types[index]
    override fun getValue(index: Int): String = values[index]

    override fun getIndex(uri: String, localName: String): Int =
        uris.indices.firstOrNull { uris[it] == uri && locals[it] == localName } ?: -1

    override fun getIndex(qName: String): Int = qNames.indexOf(qName)
    override fun getType(uri: String, localName: String): String? = getIndex(uri, localName).let { if (it < 0) null else types[it] }
    override fun getType(qName: String): String? = getIndex(qName).let { if (it < 0) null else types[it] }
    override fun getValue(uri: String, localName: String): String? = getIndex(uri, localName).let { if (it < 0) null else values[it] }
    override fun getValue(qName: String): String? = getIndex(qName).let { if (it < 0) null else values[it] }

    fun clear() {
        uris.clear(); locals.clear(); qNames.clear(); types.clear(); values.clear()
    }

    fun setAttributes(atts: Attributes) {
        clear()
        for (i in 0 until atts.length) {
            addAttribute(atts.getURI(i), atts.getLocalName(i), atts.getQName(i), atts.getType(i), atts.getValue(i))
        }
    }

    fun addAttribute(uri: String, localName: String, qName: String, type: String, value: String) {
        uris += uri; locals += localName; qNames += qName; types += type; values += value
    }

    fun removeAttribute(index: Int) {
        uris.removeAt(index); locals.removeAt(index); qNames.removeAt(index); types.removeAt(index); values.removeAt(index)
    }

    fun setValue(index: Int, value: String) {
        values[index] = value
    }
}
