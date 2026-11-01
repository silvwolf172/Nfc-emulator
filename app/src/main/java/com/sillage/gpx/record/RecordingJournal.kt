package com.sillage.gpx.record

import com.sillage.gpx.gpx.GpxPoint
import java.io.File
import java.io.Writer
import java.util.Locale

/**
 * Journal de l'enregistrement en cours : chaque point est écrit immédiatement sur disque, pour ne rien
 * perdre si l'application ou le téléphone s'arrête.
 *
 * Format texte : `N|nom|début`, `S` (nouvelle section), `P|lat|lon|ele|time`.
 */
class RecordingJournal(private val file: File) {
    private var writer: Writer? = null

    val exists: Boolean get() = file.exists() && file.length() > 0

    data class Content(val name: String, val startTime: Long, val segments: List<List<GpxPoint>>)

    fun begin(name: String, startTime: Long) {
        close()
        file.parentFile?.mkdirs()
        file.writeText("N|${name.replace('|', ' ').replace('\n', ' ')}|$startTime\n")
    }

    fun appendSegment() = write("S\n")

    fun appendPoint(p: GpxPoint) {
        val ele = p.ele?.let { String.format(Locale.ROOT, "%.1f", it) } ?: ""
        write(String.format(Locale.ROOT, "P|%.7f|%.7f|%s|%d\n", p.lat, p.lon, ele, p.time ?: 0L))
    }

    private fun write(line: String) {
        val w = writer ?: java.io.OutputStreamWriter(java.io.FileOutputStream(file, true), Charsets.UTF_8).buffered()
            .also { writer = it }
        w.write(line)
        w.flush()
    }

    fun read(): Content? {
        if (!exists) return null
        var name = "Parcours"
        var start = 0L
        val segments = ArrayList<ArrayList<GpxPoint>>()
        file.forEachLine { line ->
            val parts = line.split('|')
            when (parts.firstOrNull()) {
                "N" -> { name = parts.getOrNull(1) ?: name; start = parts.getOrNull(2)?.toLongOrNull() ?: 0L }
                "S" -> segments.add(ArrayList())
                "P" -> {
                    val lat = parts.getOrNull(1)?.toDoubleOrNull()
                    val lon = parts.getOrNull(2)?.toDoubleOrNull()
                    if (lat != null && lon != null) {
                        if (segments.isEmpty()) segments.add(ArrayList())
                        segments.last().add(GpxPoint(lat, lon, parts.getOrNull(3)?.toDoubleOrNull(), parts.getOrNull(4)?.toLongOrNull()))
                    }
                }
            }
        }
        return Content(name, start, segments.filter { it.isNotEmpty() })
    }

    fun close() {
        runCatching { writer?.close() }
        writer = null
    }

    fun delete() {
        close()
        file.delete()
    }
}
