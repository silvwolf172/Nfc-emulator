package com.sillage.gpx.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sillage.gpx.R
import com.sillage.gpx.gpx.GpxDocument
import com.sillage.gpx.gpx.GpxPoint
import com.sillage.gpx.gpx.LineRef
import com.sillage.gpx.ui.theme.LocalDarkTheme
import com.sillage.gpx.ui.theme.trackPalette
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/** Paramètres d'édition transmis à l'overlay. */
data class MapEditState(
    val editMode: Boolean = false,
    val activeLine: LineRef? = null,
    val dragPoints: Boolean = false,
    val dragWaypoints: Boolean = false,
)

@Composable
fun GpxMap(
    doc: GpxDocument,
    controller: MapController,
    style: MapStyle,
    modifier: Modifier = Modifier,
    edit: MapEditState = MapEditState(),
    highlight: GpxPoint? = null,
    selectedWaypoint: Int? = null,
    showMyLocation: Boolean = false,
    followLocation: Boolean = false,
    offlineFiles: List<java.io.File> = emptyList(),
    listener: GpxOverlay.Listener,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val dark = LocalDarkTheme.current
    val cs = MaterialTheme.colorScheme
    val palette = trackPalette().map { it.toArgb() }
    val colors = MapColors(
        casing = if (dark) Color(0xFF101418).toArgb() else Color.White.toArgb(),
        handleFill = if (dark) Color(0xFF1B1F23).toArgb() else Color.White.toArgb(),
        handleStroke = cs.outline.toArgb(),
        activeHandle = cs.primary.toArgb(),
        waypoint = cs.tertiary.toArgb(),
        selectedWaypoint = cs.primary.toArgb(),
        onWaypoint = cs.onTertiary.toArgb(),
        label = cs.onSurface.toArgb(),
        labelHalo = cs.surface.toArgb(),
        highlight = cs.primary.toArgb(),
        start = Color(0xFF2FB36E).toArgb(),
        end = Color(0xFFE5484D).toArgb(),
    )
    val currentListener = rememberUpdatedState(listener)

    // Conversion en GeoPoint mise en cache tant que le document ne change pas.
    val lines = remember(doc, palette) {
        val list = ArrayList<RenderLine>()
        var colorIndex = 0
        doc.tracks.forEachIndexed { t, track ->
            val color = palette[colorIndex++ % palette.size]
            track.segments.forEachIndexed { s, seg ->
                list.add(RenderLine(LineRef.Track(t, s), seg.points.map { GeoPoint(it.lat, it.lon) }, color, dashed = false))
            }
        }
        doc.routes.forEachIndexed { r, route ->
            list.add(RenderLine(LineRef.Route(r), route.points.map { GeoPoint(it.lat, it.lon) }, palette[colorIndex++ % palette.size], dashed = true))
        }
        list
    }

    val overlay = remember { GpxOverlay(density) }
    val locationHolder = remember { arrayOfNulls<MyLocationNewOverlay>(1) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapConfig.init(ctx)
            MapView(ctx).apply {
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                isTilesScaledToDpi = true
                isHorizontalMapRepetitionEnabled = false
                isVerticalMapRepetitionEnabled = false
                minZoomLevel = 3.0
                maxZoomLevel = 20.0
                setScrollableAreaLimitLatitude(MapView.getTileSystem().maxLatitude, MapView.getTileSystem().minLatitude, 0)
                this.controller.setZoom(5.5)
                this.controller.setCenter(GeoPoint(46.6, 2.4))
                overlays.add(overlay)
            }.also { controller.attach(it) }
        },
        update = { map ->
            val source = TileSources.forStyle(style)
            val offline = style == MapStyle.OFFLINE && offlineFiles.isNotEmpty()
            val providerKey = if (offline) "offline:" + offlineFiles.joinToString { it.name + ":" + it.length() } else "online"
            if (map.getTag(R.id.map_provider_key) != providerKey) {
                val wasOffline = (map.getTag(R.id.map_provider_key) as? String)?.startsWith("offline") == true
                if (offline) {
                    OfflineRenderer.provider(context, offlineFiles)?.let { map.setTileProvider(it) }
                } else if (wasOffline) {
                    map.setTileProvider(org.osmdroid.tileprovider.MapTileProviderBasic(context.applicationContext, source))
                }
                map.setTag(R.id.map_provider_key, providerKey)
                // Le nouveau fournisseur crée un nouveau calque de tuiles : il faut réappliquer le filtre.
                map.setTag(R.id.map_filter_key, null)
            }
            if (!offline && map.tileProvider.tileSource.name() != source.name()) map.setTileSource(source)
            val filterKey = TileSources.filterKey(style, dark)
            if (map.getTag(R.id.map_filter_key) != filterKey) {
                map.overlayManager.tilesOverlay.setColorFilter(TileSources.filterFor(style, dark))
                map.setTag(R.id.map_filter_key, filterKey)
            }
            // La grille d'attente passe elle aussi par le filtre : en mode nuit, on la choisit claire pour qu'elle ressorte sombre.
            val night = filterKey == "night"
            map.overlayManager.tilesOverlay.loadingBackgroundColor = if (night) 0xFFD9DDE1.toInt() else cs.surfaceContainer.toArgb()
            map.overlayManager.tilesOverlay.loadingLineColor = if (night) 0xFFC9CDD2.toInt() else cs.surfaceContainerHigh.toArgb()

            overlay.listener = object : GpxOverlay.Listener by currentListener.value {}
            overlay.colors = colors
            overlay.lines = lines
            if (overlay.waypoints !== doc.waypoints) overlay.setWaypointList(doc.waypoints)
            overlay.editMode = edit.editMode
            overlay.activeLine = edit.activeLine
            overlay.dragPoints = edit.dragPoints
            overlay.dragWaypoints = edit.dragWaypoints
            overlay.highlight = highlight
            overlay.selectedWaypoint = selectedWaypoint

            val existing = locationHolder[0]
            if (showMyLocation && existing == null) {
                val loc = MyLocationNewOverlay(FusedMyLocationProvider(context), map).apply {
                    val (person, arrow) = locationIcons(density, cs.primary.toArgb())
                    setDirectionArrow(person, arrow)
                    setPersonAnchor(0.5f, 0.5f)
                    setDirectionAnchor(0.5f, 0.5f)
                    enableMyLocation()
                    if (followLocation) enableFollowLocation()
                }
                map.overlays.add(0, loc)
                locationHolder[0] = loc
            } else if (!showMyLocation && existing != null) {
                existing.disableMyLocation()
                map.overlays.remove(existing)
                locationHolder[0] = null
            }
            map.invalidate()
        },
    )

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            val map = controller.mapView ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> { map.onResume(); locationHolder[0]?.enableMyLocation() }
                Lifecycle.Event.ON_PAUSE -> { map.onPause(); locationHolder[0]?.disableMyLocation() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            locationHolder[0]?.disableMyLocation()
            controller.mapView?.onDetach()
            controller.detach()
        }
    }
}

/** Recentre la carte sur la position et la suit (jusqu'à ce que l'utilisateur déplace la carte). */
fun MapController.followMyLocation(zoom: Double? = null) {
    val v = mapView ?: return
    val loc = v.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull() ?: return
    zoom?.let { if (v.zoomLevelDouble < it) v.controller.setZoom(it) }
    loc.enableFollowLocation()
}

/** Position actuelle si la localisation est active. */
fun MapController.myLocation(): GeoPoint? =
    mapView?.overlays?.filterIsInstance<MyLocationNewOverlay>()?.firstOrNull()?.myLocation

private fun locationIcons(density: Float, color: Int): Pair<Bitmap, Bitmap> {
    val size = (28 * density).toInt()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val person = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    Canvas(person).apply {
        val c = size / 2f
        paint.color = color; paint.alpha = 60
        drawCircle(c, c, c, paint)
        paint.alpha = 255; paint.color = android.graphics.Color.WHITE
        drawCircle(c, c, c * 0.5f, paint)
        paint.color = color
        drawCircle(c, c, c * 0.36f, paint)
    }
    val arrow = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    Canvas(arrow).apply {
        val c = size / 2f
        val path = android.graphics.Path().apply {
            moveTo(c, c * 0.15f); lineTo(c * 1.6f, c * 1.7f); lineTo(c, c * 1.35f); lineTo(c * 0.4f, c * 1.7f); close()
        }
        paint.color = android.graphics.Color.WHITE
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3 * density
        drawPath(path, paint)
        paint.style = Paint.Style.FILL; paint.color = color
        drawPath(path, paint)
    }
    return person to arrow
}
