package com.sillage.gpx.data

import com.sillage.gpx.gpx.GpxPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

enum class RoutingProfile(val label: String, val path: String) {
    FOOT("À pied", "routed-foot"),
    BIKE("Vélo", "routed-bike"),
    CAR("Voiture", "routed-car"),
}

/**
 * Services en ligne gratuits, sans clé :
 * - routage OSRM (serveurs FOSSGIS / openstreetmap.de) pour « suivre les chemins » en dessinant ;
 * - altitudes via l'API Open-Meteo (modèle Copernicus DEM 90 m).
 */
object OnlineServices {
    private const val USER_AGENT = "Sillage-GPX/1.0 (Android)"

    suspend fun route(from: GpxPoint, to: GpxPoint, profile: RoutingProfile): List<GpxPoint> = withContext(Dispatchers.IO) {
        val coords = String.format(Locale.ROOT, "%.6f,%.6f;%.6f,%.6f", from.lon, from.lat, to.lon, to.lat)
        val url = "https://routing.openstreetmap.de/${profile.path}/route/v1/driving/$coords?overview=full&geometries=geojson"
        val json = JSONObject(get(url))
        if (json.optString("code") != "Ok") error("Aucun itinéraire trouvé")
        val coordinates = json.getJSONArray("routes").getJSONObject(0)
            .getJSONObject("geometry").getJSONArray("coordinates")
        List(coordinates.length()) { i ->
            val c = coordinates.getJSONArray(i)
            GpxPoint(lat = c.getDouble(1), lon = c.getDouble(0))
        }
    }

    /** Renvoie l'altitude de chaque coordonnée (lat, lon). Interroge l'API par lots de 100. */
    suspend fun elevations(coords: List<Pair<Double, Double>>, onProgress: (Float) -> Unit = {}): Map<Pair<Double, Double>, Double> =
        withContext(Dispatchers.IO) {
            val result = HashMap<Pair<Double, Double>, Double>(coords.size)
            val chunks = coords.chunked(100)
            chunks.forEachIndexed { i, chunk ->
                val lats = chunk.joinToString(",") { String.format(Locale.ROOT, "%.6f", it.first) }
                val lons = chunk.joinToString(",") { String.format(Locale.ROOT, "%.6f", it.second) }
                val json = JSONObject(get("https://api.open-meteo.com/v1/elevation?latitude=$lats&longitude=$lons"))
                val arr = json.getJSONArray("elevation")
                for (k in 0 until minOf(arr.length(), chunk.size)) {
                    val v = arr.optDouble(k)
                    if (!v.isNaN()) result[chunk[k]] = v
                }
                onProgress((i + 1).toFloat() / chunks.size)
            }
            result
        }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("Erreur réseau ($code)")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
