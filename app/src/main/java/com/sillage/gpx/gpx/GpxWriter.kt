package com.sillage.gpx.gpx

import java.io.OutputStream
import java.io.Writer
import java.util.Locale

/** Écrit un document au format GPX 1.1. */
object GpxWriter {
    const val CREATOR = "Sillage GPX"

    fun write(doc: GpxDocument, out: OutputStream) {
        val writer = out.bufferedWriter(Charsets.UTF_8)
        write(doc, writer)
        writer.flush()
    }

    fun toString(doc: GpxDocument): String = buildString { write(doc, StringBuilderWriter(this)) }

    fun write(doc: GpxDocument, w: Writer) {
        w.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        w.append("<gpx version=\"1.1\" creator=\"").append(escape(CREATOR)).append("\"\n")
        w.append("     xmlns=\"http://www.topografix.com/GPX/1/1\"\n")
        w.append("     xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n")
        w.append("     xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")

        val m = doc.metadata
        if (m.name != null || m.desc != null || m.author != null || m.time != null || m.keywords != null) {
            w.append("  <metadata>\n")
            tag(w, 2, "name", m.name)
            tag(w, 2, "desc", m.desc)
            if (!m.author.isNullOrBlank()) {
                w.append("    <author>\n")
                tag(w, 3, "name", m.author)
                w.append("    </author>\n")
            }
            m.time?.let { tag(w, 2, "time", GpxTime.format(it)) }
            tag(w, 2, "keywords", m.keywords)
            w.append("  </metadata>\n")
        }

        doc.waypoints.forEach { point(w, 1, "wpt", it) }

        doc.routes.forEach { r ->
            w.append("  <rte>\n")
            tag(w, 2, "name", r.name)
            tag(w, 2, "desc", r.desc)
            tag(w, 2, "type", r.type)
            r.points.forEach { point(w, 2, "rtept", it) }
            w.append("  </rte>\n")
        }

        doc.tracks.forEach { t ->
            w.append("  <trk>\n")
            tag(w, 2, "name", t.name)
            tag(w, 2, "desc", t.desc)
            tag(w, 2, "type", t.type)
            t.segments.forEach { s ->
                w.append("    <trkseg>\n")
                s.points.forEach { point(w, 3, "trkpt", it) }
                w.append("    </trkseg>\n")
            }
            w.append("  </trk>\n")
        }
        w.append("</gpx>\n")
    }

    private fun point(w: Writer, depth: Int, name: String, p: GpxPoint) {
        indent(w, depth)
        w.append('<').append(name)
            .append(" lat=\"").append(coord(p.lat)).append('"')
            .append(" lon=\"").append(coord(p.lon)).append('"')
        val hasChildren = p.ele != null || p.time != null || p.name != null || p.desc != null ||
            p.cmt != null || p.sym != null || p.type != null
        if (!hasChildren) {
            w.append("/>\n")
            return
        }
        w.append(">")
        // L'ordre des éléments suit le schéma GPX 1.1.
        p.ele?.let { w.append("<ele>").append(String.format(Locale.ROOT, "%.1f", it)).append("</ele>") }
        p.time?.let { w.append("<time>").append(GpxTime.format(it)).append("</time>") }
        inline(w, "name", p.name)
        inline(w, "cmt", p.cmt)
        inline(w, "desc", p.desc)
        inline(w, "sym", p.sym)
        inline(w, "type", p.type)
        w.append("</").append(name).append(">\n")
    }

    private fun coord(v: Double): String = String.format(Locale.ROOT, "%.7f", v).trimEnd('0').trimEnd('.')

    private fun inline(w: Writer, name: String, value: String?) {
        if (value.isNullOrEmpty()) return
        w.append('<').append(name).append('>').append(escape(value)).append("</").append(name).append('>')
    }

    private fun tag(w: Writer, depth: Int, name: String, value: String?) {
        if (value.isNullOrBlank()) return
        indent(w, depth)
        inline(w, name, value)
        w.append('\n')
    }

    private fun indent(w: Writer, depth: Int) {
        repeat(depth) { w.append("  ") }
    }

    fun escape(s: String): String {
        if (s.none { it == '<' || it == '>' || it == '&' || it == '"' || it == '\'' || (it < ' ' && it != '\n' && it != '\t' && it != '\r') }) return s
        val sb = StringBuilder(s.length + 16)
        for (c in s) {
            when {
                c == '<' -> sb.append("&lt;")
                c == '>' -> sb.append("&gt;")
                c == '&' -> sb.append("&amp;")
                c == '"' -> sb.append("&quot;")
                c == '\'' -> sb.append("&apos;")
                c < ' ' && c != '\n' && c != '\t' && c != '\r' -> Unit // caractères interdits en XML 1.0
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private class StringBuilderWriter(private val sb: StringBuilder) : Writer() {
        override fun write(cbuf: CharArray, off: Int, len: Int) { sb.append(cbuf, off, len) }
        override fun write(str: String) { sb.append(str) }
        override fun append(csq: CharSequence?): Writer { sb.append(csq); return this }
        override fun append(c: Char): Writer { sb.append(c); return this }
        override fun flush() = Unit
        override fun close() = Unit
    }
}
