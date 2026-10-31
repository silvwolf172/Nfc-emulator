package com.sillage.gpx.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import com.sillage.gpx.gpx.GpxDocument
import com.sillage.gpx.gpx.GpxEdit
import com.sillage.gpx.gpx.GpxMetadata
import com.sillage.gpx.gpx.GpxParser
import com.sillage.gpx.gpx.GpxSegment
import com.sillage.gpx.gpx.GpxStats
import com.sillage.gpx.gpx.GpxTrack
import com.sillage.gpx.gpx.GpxWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos

/** Résumé d'un fichier de la bibliothèque. */
@Immutable
data class GpxEntry(
    val id: String,
    val name: String,
    val stats: GpxStats,
    /** Silhouette normalisée (0..1) de chaque ligne, pour la vignette. */
    val preview: List<FloatArray>,
    val modified: Long,
    val sizeBytes: Long,
    val error: String? = null,
)

/** Bibliothèque de fichiers GPX stockés dans l'espace privé de l'application. */
class GpxRepository(private val context: Context) {
    private val dir = File(context.filesDir, "gpx").apply { mkdirs() }
    private val cache = ConcurrentHashMap<String, Pair<Long, GpxEntry>>()

    fun file(id: String) = File(dir, id)

    suspend fun list(): List<GpxEntry> = withContext(Dispatchers.IO) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".gpx", ignoreCase = true) }.orEmpty()
        coroutineScope {
            files.map { f -> async { entryFor(f) } }.awaitAll()
        }.sortedByDescending { it.modified }
    }

    private fun entryFor(f: File): GpxEntry {
        val stamp = f.lastModified() xor f.length()
        cache[f.name]?.let { (s, e) -> if (s == stamp) return e }
        val entry = try {
            val doc = f.inputStream().buffered().use { GpxParser.parse(it) }
            GpxEntry(
                id = f.name,
                name = doc.displayName ?: f.nameWithoutExtension,
                stats = GpxStats.of(doc),
                preview = preview(doc),
                modified = f.lastModified(),
                sizeBytes = f.length(),
            )
        } catch (e: Exception) {
            GpxEntry(f.name, f.nameWithoutExtension, GpxStats(), emptyList(), f.lastModified(), f.length(), e.message ?: "Erreur")
        }
        cache[f.name] = stamp to entry
        return entry
    }

    suspend fun load(id: String): GpxDocument = withContext(Dispatchers.IO) {
        file(id).inputStream().buffered().use { GpxParser.parse(it) }
    }

    suspend fun save(id: String, doc: GpxDocument) = withContext(Dispatchers.IO) {
        val target = file(id)
        val tmp = File(dir, "$id.tmp")
        tmp.outputStream().use { GpxWriter.write(doc, it) }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        cache.remove(id)
    }

    suspend fun create(name: String): String {
        val doc = GpxDocument(
            metadata = GpxMetadata(name = name, time = System.currentTimeMillis()),
            tracks = listOf(GpxTrack(name = name, segments = listOf(GpxSegment()))),
        )
        val id = uniqueId(name)
        save(id, doc)
        return id
    }

    /** Copie un GPX externe dans la bibliothèque après l'avoir validé. */
    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Impossible de lire le fichier")
        val doc = GpxParser.parse(bytes.inputStream())
        val baseName = displayName(uri)?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: doc.displayName ?: "Import"
        val id = uniqueId(baseName)
        file(id).writeBytes(bytes)
        id
    }

    suspend fun importText(name: String, text: String): String = withContext(Dispatchers.IO) {
        GpxParser.parse(text) // validation
        val id = uniqueId(name)
        file(id).writeText(text)
        id
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        file(id).delete()
        cache.remove(id)
        Unit
    }

    suspend fun rename(id: String, name: String) {
        val doc = load(id)
        val renamed = doc.copy(metadata = doc.metadata.copy(name = name)).let { d ->
            // Une trace unique prend aussi le nouveau nom, c'est ce qu'affichent la plupart des applis.
            if (d.tracks.size == 1) GpxEdit.renameTrack(d, 0, name) else d
        }
        save(id, renamed)
    }

    suspend fun duplicate(id: String): String {
        val doc = load(id)
        val name = (doc.displayName ?: file(id).nameWithoutExtension) + " (copie)"
        val newId = uniqueId(name)
        save(newId, doc.copy(metadata = doc.metadata.copy(name = name)))
        return newId
    }

    suspend fun exportTo(id: String, uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            file(id).inputStream().use { it.copyTo(out) }
        } ?: error("Impossible d'écrire le fichier")
        Unit
    }

    /** Prépare une copie partageable (FileProvider) portant un nom lisible. */
    suspend fun shareUri(id: String, name: String): Uri = withContext(Dispatchers.IO) {
        val shared = File(context.cacheDir, "shared").apply { mkdirs() }
        shared.listFiles()?.forEach { it.delete() }
        val out = File(shared, sanitize(name) + ".gpx")
        file(id).copyTo(out, overwrite = true)
        FileProvider.getUriForFile(context, context.packageName + ".files", out)
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun uniqueId(name: String): String {
        val base = asciiId(name).ifBlank { "trace" }
        var candidate = "$base.gpx"
        var i = 2
        while (File(dir, candidate).exists()) candidate = "$base ($i).gpx".also { i++ }
        return candidate
    }

    companion object {
        /** Identifiant de fichier interne : ASCII uniquement, pour être sûr sur tous les systèmes de fichiers. */
        fun asciiId(name: String): String =
            java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace(Regex("[^A-Za-z0-9 ._()-]"), "_")
                .replace(Regex("_+"), "_")
                .trim(' ', '_', '.')
                .take(80)

        fun sanitize(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(80)

        /** Réduit chaque ligne à ~80 points normalisés dans un carré unité (en gardant les proportions). */
        fun preview(doc: GpxDocument, maxPoints: Int = 80): List<FloatArray> {
            val lines = doc.lines.filter { it.size >= 2 }
            val bounds = doc.bounds() ?: return emptyList()
            val k = cos(Math.toRadians(bounds.centerLat))
            val w = ((bounds.maxLon - bounds.minLon) * k).coerceAtLeast(1e-9)
            val h = (bounds.maxLat - bounds.minLat).coerceAtLeast(1e-9)
            val span = maxOf(w, h)
            val ox = (span - w) / 2
            val oy = (span - h) / 2
            return lines.map { line ->
                val step = maxOf(1, line.size / maxPoints)
                val sampled = line.filterIndexed { i, _ -> i % step == 0 || i == line.lastIndex }
                FloatArray(sampled.size * 2).also { arr ->
                    sampled.forEachIndexed { i, p ->
                        arr[i * 2] = (((p.lon - bounds.minLon) * k + ox) / span).toFloat()
                        arr[i * 2 + 1] = (1.0 - ((p.lat - bounds.minLat) + oy) / span).toFloat()
                    }
                }
            }
        }
    }
}
