package com.sillage.gpx.offline

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Élément du catalogue mapsforge.org : un dossier (continent, pays) ou une carte. */
@Immutable
data class CatalogEntry(val name: String, val path: String, val isFolder: Boolean, val size: String?) {
    val label: String get() = prettyName(name.removeSuffix("/").removeSuffix(".map"))
}

@Immutable
data class InstalledMap(val file: File, val sizeBytes: Long) {
    val label: String get() = prettyName(file.nameWithoutExtension)
}

@Immutable
data class MapDownload(val id: Long, val fileName: String, val downloaded: Long, val total: Long, val status: Int) {
    val progress: Float? get() = if (total > 0) downloaded.toFloat() / total else null
    val label: String get() = prettyName(fileName.removeSuffix(".map"))
    val failed: Boolean get() = status == DownloadManager.STATUS_FAILED
}

fun prettyName(raw: String): String =
    raw.split('-', '_').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.FRENCH) } }

/**
 * Cartes vectorielles hors ligne (format mapsforge, données OpenStreetMap), téléchargées par région depuis
 * download.mapsforge.org et rendues sur le téléphone : aucun réseau n'est nécessaire une fois installées.
 */
object OfflineMaps {
    const val BASE_URL = "https://download.mapsforge.org/maps/v5/"
    private const val PREFS = "offline_maps"

    fun dir(context: Context): File =
        (context.getExternalFilesDir(DIR) ?: File(context.filesDir, DIR)).apply { mkdirs() }

    private const val DIR = "maps"

    /** Cartes complètes présentes sur le téléphone (les téléchargements en cours sont exclus). */
    fun installed(context: Context): List<InstalledMap> {
        val busy = downloads(context).filter { it.status != DownloadManager.STATUS_SUCCESSFUL }.map { it.fileName }.toSet()
        return dir(context).listFiles { f -> f.isFile && f.name.endsWith(".map") && f.name !in busy && f.length() > 0 }
            .orEmpty().sortedBy { it.name }.map { InstalledMap(it, it.length()) }
    }

    fun installedFiles(context: Context): List<File> = installed(context).map { it.file }

    suspend fun catalog(path: String): List<CatalogEntry> = withContext(Dispatchers.IO) {
        val conn = URL(BASE_URL + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("User-Agent", "Sillage-GPX/1.0 (Android)")
        try {
            if (conn.responseCode !in 200..299) error("Catalogue indisponible (${conn.responseCode})")
            parseListing(conn.inputStream.bufferedReader().use { it.readText() }, path)
        } finally {
            conn.disconnect()
        }
    }

    private val rowRegex = Regex(
        """<a href="([^"?/][^"]*)">[^<]*</a>.*?<td[^>]*>\s*([^<]*?)\s*</td>\s*<td[^>]*>\s*([^<]*?)\s*</td>""",
        RegexOption.IGNORE_CASE,
    )

    /** Analyse la page d'index Apache du serveur. */
    fun parseListing(html: String, path: String): List<CatalogEntry> =
        html.lineSequence().mapNotNull { line ->
            val m = rowRegex.find(line) ?: return@mapNotNull null
            val href = m.groupValues[1]
            val isFolder = href.endsWith("/")
            if (!isFolder && !href.endsWith(".map")) return@mapNotNull null
            val size = m.groupValues[3].trim().takeIf { !isFolder && it.isNotEmpty() && it != "-" }
            CatalogEntry(name = href, path = path + href, isFolder = isFolder, size = size?.let(::humanSize))
        }.toList().sortedWith(compareBy({ !it.isFolder }, { it.label }))

    private fun humanSize(s: String): String = when {
        s.endsWith("G") -> s.dropLast(1).replace('.', ',') + " Go"
        s.endsWith("M") -> s.dropLast(1).replace('.', ',') + " Mo"
        s.endsWith("K") -> s.dropLast(1).replace('.', ',') + " ko"
        else -> s
    }

    fun download(context: Context, entry: CatalogEntry): Long {
        val dm = context.getSystemService(DownloadManager::class.java)
        val fileName = entry.name
        File(dir(context), fileName).delete()
        val request = DownloadManager.Request(Uri.parse(BASE_URL + entry.path))
            .setTitle("Carte hors ligne : ${entry.label}")
            .setDescription("Sillage")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .addRequestHeader("User-Agent", "Sillage-GPX/1.0 (Android)")
        if (context.getExternalFilesDir(DIR) != null) {
            request.setDestinationInExternalFilesDir(context, DIR, fileName)
        } else {
            request.setDestinationUri(Uri.fromFile(File(dir(context), fileName)))
        }
        val id = dm.enqueue(request)
        prefs(context).edit().putString(id.toString(), fileName).apply()
        return id
    }

    /** Téléchargements lancés par l'app et encore suivis. */
    fun downloads(context: Context): List<MapDownload> {
        val prefs = prefs(context)
        val known = prefs.all.mapNotNull { (k, v) -> k.toLongOrNull()?.let { it to v.toString() } }
        if (known.isEmpty()) return emptyList()
        val dm = context.getSystemService(DownloadManager::class.java)
        val result = ArrayList<MapDownload>()
        val seen = HashSet<Long>()
        runCatching {
            dm.query(DownloadManager.Query().setFilterById(*known.map { it.first }.toLongArray()))?.use { c ->
                val idCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val statusCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val soFarCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    seen.add(id)
                    val name = known.firstOrNull { it.first == id }?.second ?: continue
                    result.add(MapDownload(id, name, c.getLong(soFarCol), c.getLong(totalCol), c.getInt(statusCol)))
                }
            }
        }
        // Oublie les téléchargements terminés ou disparus.
        val editor = prefs.edit()
        known.forEach { (id, _) -> if (id !in seen) editor.remove(id.toString()) }
        result.filter { it.status == DownloadManager.STATUS_SUCCESSFUL }.forEach { editor.remove(it.id.toString()) }
        editor.apply()
        return result
    }

    fun cancel(context: Context, download: MapDownload) {
        context.getSystemService(DownloadManager::class.java).remove(download.id)
        prefs(context).edit().remove(download.id.toString()).apply()
        File(dir(context), download.fileName).delete()
    }

    fun delete(map: InstalledMap) {
        map.file.delete()
    }

    fun freeSpaceBytes(context: Context): Long = dir(context).usableSpace

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

}
