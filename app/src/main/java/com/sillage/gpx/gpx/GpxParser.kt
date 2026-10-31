package com.sillage.gpx.gpx

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

class GpxParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Lecteur GPX 1.0 / 1.1 en flux (SAX) : rapide et économe en mémoire, même pour de très longues traces.
 * Les extensions propriétaires sont ignorées.
 */
object GpxParser {

    fun parse(input: InputStream): GpxDocument = parse(InputSource(input))

    fun parse(text: String): GpxDocument = parse(InputSource(StringReader(text)))

    private fun parse(source: InputSource): GpxDocument {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            isValidating = false
        }
        val handler = Handler()
        try {
            val parser = factory.newSAXParser()
            runCatching { parser.xmlReader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { parser.xmlReader.setFeature("http://xml.org/sax/features/external-general-entities", false) }
            parser.parse(source, handler)
        } catch (e: SAXException) {
            throw GpxParseException("Fichier GPX invalide : ${e.message}", e)
        }
        if (!handler.sawGpxRoot) throw GpxParseException("Ce fichier n'est pas un GPX (balise <gpx> absente).")
        return handler.build()
    }

    private class PointBuilder(val lat: Double, val lon: Double) {
        var ele: Double? = null
        var time: Long? = null
        var name: String? = null
        var desc: String? = null
        var cmt: String? = null
        var sym: String? = null
        var type: String? = null
        fun build() = GpxPoint(lat, lon, ele, time, name, desc, cmt, sym, type)
    }

    private class Handler : DefaultHandler() {
        var sawGpxRoot = false
        private var creator: String? = null

        private var metaName: String? = null
        private var metaDesc: String? = null
        private var metaAuthor: String? = null
        private var metaTime: Long? = null
        private var metaKeywords: String? = null

        private val waypoints = ArrayList<GpxPoint>()
        private val routes = ArrayList<GpxRoute>()
        private val tracks = ArrayList<GpxTrack>()

        private var point: PointBuilder? = null

        private var routeName: String? = null
        private var routeDesc: String? = null
        private var routeType: String? = null
        private var routePoints: ArrayList<GpxPoint>? = null

        private var trackName: String? = null
        private var trackDesc: String? = null
        private var trackType: String? = null
        private var trackSegments: ArrayList<GpxSegment>? = null
        private var segmentPoints: ArrayList<GpxPoint>? = null

        private val stack = ArrayList<String>()
        private val text = StringBuilder()
        /** Profondeur dans un bloc <extensions> : tout ce qui s'y trouve est ignoré. */
        private var extensionsDepth = 0

        fun build() = GpxDocument(
            metadata = GpxMetadata(metaName, metaDesc, metaAuthor, metaTime, metaKeywords),
            waypoints = waypoints,
            routes = routes,
            tracks = tracks,
            creator = creator,
        )

        private fun parent(level: Int = 1): String? = stack.getOrNull(stack.size - 1 - level)

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            val name = elementName(localName, qName)
            if (extensionsDepth > 0 || name == "extensions") {
                extensionsDepth++
                stack.add(name)
                return
            }
            stack.add(name)
            text.setLength(0)
            when (name) {
                "gpx" -> {
                    sawGpxRoot = true
                    creator = attributes.getValue("creator")
                }
                "wpt", "rtept", "trkpt" -> {
                    val lat = attributes.getValue("lat")?.trim()?.toDoubleOrNull()
                    val lon = attributes.getValue("lon")?.trim()?.toDoubleOrNull()
                    point = if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                        PointBuilder(lat, lon)
                    } else null
                }
                "rte" -> {
                    routeName = null; routeDesc = null; routeType = null
                    routePoints = ArrayList()
                }
                "trk" -> {
                    trackName = null; trackDesc = null; trackType = null
                    trackSegments = ArrayList()
                }
                "trkseg" -> segmentPoints = ArrayList()
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (extensionsDepth == 0) text.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val name = elementName(localName, qName)
            if (extensionsDepth > 0) {
                extensionsDepth--
                stack.removeAt(stack.lastIndex)
                return
            }
            val value = text.toString().trim()
            val parent = parent()
            when (name) {
                "wpt" -> { point?.let { waypoints.add(it.build()) }; point = null }
                "rtept" -> { point?.let { p -> routePoints?.add(p.build()) }; point = null }
                "trkpt" -> { point?.let { p -> segmentPoints?.add(p.build()) }; point = null }
                "trkseg" -> {
                    segmentPoints?.let { trackSegments?.add(GpxSegment(it)) }
                    segmentPoints = null
                }
                "trk" -> {
                    val segs = trackSegments.orEmpty().ifEmpty { listOf(GpxSegment()) }
                    tracks.add(GpxTrack(trackName, trackDesc, trackType, segs))
                    trackSegments = null
                }
                "rte" -> {
                    routes.add(GpxRoute(routeName, routeDesc, routeType, routePoints.orEmpty()))
                    routePoints = null
                }
                else -> assignText(name, parent, value)
            }
            stack.removeAt(stack.lastIndex)
            text.setLength(0)
        }

        private fun assignText(name: String, parent: String?, value: String) {
            if (value.isEmpty()) return
            val p = point
            if (p != null && (parent == "wpt" || parent == "rtept" || parent == "trkpt")) {
                when (name) {
                    "ele" -> p.ele = value.replace(',', '.').toDoubleOrNull()
                    "time" -> p.time = GpxTime.parse(value)
                    "name" -> p.name = value
                    "desc" -> p.desc = value
                    "cmt" -> p.cmt = value
                    "sym" -> p.sym = value
                    "type" -> p.type = value
                }
                return
            }
            when (parent) {
                "trk" -> when (name) {
                    "name" -> trackName = value
                    "desc" -> trackDesc = value
                    "type" -> trackType = value
                }
                "rte" -> when (name) {
                    "name" -> routeName = value
                    "desc" -> routeDesc = value
                    "type" -> routeType = value
                }
                "metadata" -> when (name) {
                    "name" -> metaName = value
                    "desc" -> metaDesc = value
                    "time" -> metaTime = GpxTime.parse(value)
                    "keywords" -> metaKeywords = value
                }
                "author" -> if (name == "name" && parent(2) == "metadata") metaAuthor = value
                // GPX 1.0 : les métadonnées sont directement sous <gpx>.
                "gpx" -> when (name) {
                    "name" -> metaName = metaName ?: value
                    "desc" -> metaDesc = metaDesc ?: value
                    "author" -> metaAuthor = metaAuthor ?: value
                    "time" -> metaTime = metaTime ?: GpxTime.parse(value)
                    "keywords" -> metaKeywords = metaKeywords ?: value
                }
            }
        }

        private fun elementName(localName: String?, qName: String?): String =
            localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty().substringAfter(':')
    }
}
