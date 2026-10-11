package javax.xml.parsers

import java.io.InputStream
import lightweb.compat.SaxXmlReader
import org.xml.sax.InputSource
import org.xml.sax.XMLReader
import org.xml.sax.helpers.DefaultHandler

/** Makes [SaxXmlReader]s: non-validating, namespace-aware if asked. */
class SAXParserFactory private constructor() {
    var isNamespaceAware: Boolean = false
    var isValidating: Boolean = false
    var isXIncludeAware: Boolean = false
    private val features = HashMap<String, Boolean>()

    fun setFeature(name: String, value: Boolean) { features[name] = value }
    fun getFeature(name: String): Boolean = features[name] ?: false

    fun newSAXParser(): SAXParser {
        if (isValidating) throw ParserConfigurationException("validation isn't supported")
        return SAXParser(SaxXmlReader(isNamespaceAware))
    }

    companion object {
        fun newInstance(): SAXParserFactory = SAXParserFactory()
    }
}

class SAXParser internal constructor(val xmlReader: XMLReader) {
    val isNamespaceAware: Boolean get() = (xmlReader as SaxXmlReader).namespaceAware

    fun parse(input: InputStream, handler: DefaultHandler) = parse(InputSource(input), handler)

    fun parse(input: InputSource, handler: DefaultHandler) {
        xmlReader.contentHandler = handler
        xmlReader.entityResolver = handler
        xmlReader.errorHandler = handler
        xmlReader.dtdHandler = handler
        xmlReader.parse(input)
    }
}

class ParserConfigurationException(message: String? = null) : Exception(message)
