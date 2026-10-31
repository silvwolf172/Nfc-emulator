package com.sillage.gpx.ui.map

import android.content.Context
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.runtime.Stable
import com.sillage.gpx.gpx.GeoBounds
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.TileSystem
import org.osmdroid.views.MapView
import java.io.File
import kotlin.math.PI
import kotlin.math.atan

import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.tan

enum class MapStyle(val label: String, val description: String) {
    AUTO("Auto", "Clair ou sombre selon le thème"),
    LIGHT("Clair", "OpenStreetMap adouci : la trace ressort"),
    DARK("Nuit", "OpenStreetMap en mode sombre"),
    STANDARD("OpenStreetMap", "Couleurs d'origine"),
    TOPO("Topographique", "Relief et courbes de niveau"),
    CYCLE("CyclOSM", "Pistes cyclables et sentiers"),
}

/** Fonds de carte libres et sans clé d'API. */
object TileSources {
    val CyclOsm: ITileSource = XYTileSource(
        "CyclOSM", 0, 20, 256, ".png",
        arrayOf("a", "b", "c").map { "https://$it.tile-cyclosm.openstreetmap.fr/cyclosm/" }.toTypedArray(),
        "© OpenStreetMap contributors, CyclOSM",
    )

    fun forStyle(style: MapStyle): ITileSource = when (style) {
        MapStyle.TOPO -> TileSourceFactory.OpenTopo
        MapStyle.CYCLE -> CyclOsm
        else -> TileSourceFactory.MAPNIK
    }

    /** Filtre de couleur appliqué aux tuiles (null = couleurs d'origine). */
    fun filterFor(style: MapStyle, dark: Boolean): ColorFilter? = when (style) {
        MapStyle.AUTO -> if (dark) nightFilter else softFilter
        MapStyle.LIGHT -> softFilter
        MapStyle.DARK -> nightFilter
        else -> null
    }

    fun filterKey(style: MapStyle, dark: Boolean): String = when (filterFor(style, dark)) {
        null -> "none"
        nightFilter -> "night"
        else -> "soft"
    }

    fun attribution(style: MapStyle): String = when (style) {
        MapStyle.TOPO -> "© OpenStreetMap · SRTM · © OpenTopoMap"
        MapStyle.CYCLE -> "© OpenStreetMap · CyclOSM"
        else -> "© OpenStreetMap"
    }

    /** Couleurs adoucies et éclaircies : un fond calme, à la Material You. */
    private val softFilter: ColorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
        setSaturation(0.5f)
        postConcat(ColorMatrix(floatArrayOf(
            0.88f, 0f, 0f, 0f, 28f,
            0f, 0.88f, 0f, 0f, 30f,
            0f, 0f, 0.88f, 0f, 32f,
            0f, 0f, 0f, 1f, 0f,
        )))
    })

    /** Inversion « intelligente » (luminosité inversée, teintes conservées) puis assombrissement. */
    private val nightFilter: ColorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    )).apply {
        postConcat(hueRotate(180f))
        postConcat(ColorMatrix().apply { setSaturation(0.45f) })
        postConcat(ColorMatrix(floatArrayOf(
            0.80f, 0f, 0f, 0f, 2f,
            0f, 0.84f, 0f, 0f, 6f,
            0f, 0f, 0.90f, 0f, 12f,
            0f, 0f, 0f, 1f, 0f,
        )))
    })

    private fun hueRotate(degrees: Float): ColorMatrix {
        val r = Math.toRadians(degrees.toDouble())
        val c = kotlin.math.cos(r).toFloat()
        val s = kotlin.math.sin(r).toFloat()
        return ColorMatrix(floatArrayOf(
            0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
            0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
            0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }
}

object MapConfig {
    private var done = false

    fun init(context: Context) {
        if (done) return
        val conf = Configuration.getInstance()
        conf.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        conf.userAgentValue = "Sillage-GPX/1.0 (${context.packageName})"
        val base = File(context.cacheDir, "osmdroid")
        conf.osmdroidBasePath = base
        conf.osmdroidTileCache = File(base, "tiles")
        conf.tileFileSystemCacheMaxBytes = 300L * 1024 * 1024
        conf.tileFileSystemCacheTrimBytes = 250L * 1024 * 1024
        done = true
    }
}

/** Marges (px) à laisser autour d'un cadrage, pour ne pas cacher la trace sous le verre. */
data class MapInsets(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0)

/** Permet de piloter la carte depuis Compose. */
@Stable
class MapController {
    internal var mapView: MapView? = null
    private var pending: (() -> Unit)? = null

    internal fun attach(view: MapView) {
        mapView = view
        if (view.width > 0) {
            pending?.invoke(); pending = null
        } else {
            view.addOnFirstLayoutListener { _, _, _, _, _ -> pending?.invoke(); pending = null }
        }
    }

    internal fun detach() { mapView = null }

    private fun whenReady(block: (MapView) -> Unit) {
        val v = mapView
        if (v != null && v.width > 0 && v.height > 0) block(v) else pending = { mapView?.let(block) }
    }

    fun fit(bounds: GeoBounds?, insets: MapInsets = MapInsets(), animate: Boolean = true) = whenReady { v ->
        if (bounds == null) return@whenReady
        val w = (v.width - insets.left - insets.right).coerceAtLeast(64)
        val h = (v.height - insets.top - insets.bottom).coerceAtLeast(64)
        val tile = TileSystem.getTileSize().toDouble()
        val x0 = lonTo01(bounds.minLon)
        val x1 = lonTo01(bounds.maxLon)
        val y0 = latTo01(bounds.maxLat)
        val y1 = latTo01(bounds.minLat)
        val spanX = (x1 - x0).coerceAtLeast(1e-9)
        val spanY = (y1 - y0).coerceAtLeast(1e-9)
        val pad = 0.88
        var zoom = min(log2(w * pad / (spanX * tile)), log2(h * pad / (spanY * tile)))
        zoom = zoom.coerceIn(3.0, if (spanX < 1e-6 && spanY < 1e-6) 16.0 else 18.0)
        val world = tile * Math.pow(2.0, zoom)
        // Centre de l'emprise placé au centre de la zone visible (hors marges).
        val dx = (insets.left - insets.right) / 2.0
        val dy = (insets.top - insets.bottom) / 2.0
        val cx = (x0 + x1) / 2 - dx / world
        val cy = (y0 + y1) / 2 - dy / world
        val center = GeoPoint(y01ToLat(cy), x01ToLon(cx))
        if (animate) {
            v.controller.animateTo(center, zoom, 650L)
        } else {
            v.controller.setZoom(zoom)
            v.controller.setCenter(center)
        }
    }

    fun centerOn(lat: Double, lon: Double, zoom: Double? = null) = whenReady { v ->
        v.controller.animateTo(GeoPoint(lat, lon), zoom ?: v.zoomLevelDouble.coerceAtLeast(14.0), 600L)
    }

    fun zoomBy(delta: Double) = whenReady { v ->
        v.controller.animateTo(v.mapCenter, v.zoomLevelDouble + delta, 300L)
    }

    val zoom: Double get() = mapView?.zoomLevelDouble ?: 0.0

    companion object {
        private fun lonTo01(lon: Double) = (lon + 180.0) / 360.0
        private fun x01ToLon(x: Double) = x * 360.0 - 180.0
        private fun latTo01(lat: Double): Double {
            val r = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
            return (1.0 - ln(tan(r) + 1 / kotlin.math.cos(r)) / PI) / 2.0
        }
        private fun y01ToLat(y: Double): Double = Math.toDegrees(atan(kotlin.math.sinh(PI * (1 - 2 * y))))
    }
}
