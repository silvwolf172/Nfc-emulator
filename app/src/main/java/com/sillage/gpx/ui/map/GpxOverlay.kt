package com.sillage.gpx.ui.map

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.sillage.gpx.gpx.GpxPoint
import com.sillage.gpx.gpx.LineRef
import com.sillage.gpx.gpx.PointRef
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.atan2
import kotlin.math.hypot

/** Ligne prête à dessiner (les GeoPoint sont mutables pour l'aperçu pendant le glisser). */
class RenderLine(
    val ref: LineRef,
    val geo: List<GeoPoint>,
    val color: Int,
    val dashed: Boolean,
)

class MapColors(
    val casing: Int,
    val handleFill: Int,
    val handleStroke: Int,
    val activeHandle: Int,
    val waypoint: Int,
    val selectedWaypoint: Int,
    val onWaypoint: Int,
    val label: Int,
    val labelHalo: Int,
    val highlight: Int,
    val start: Int,
    val end: Int,
)

/**
 * Overlay osmdroid qui dessine traces, itinéraires, repères et poignées d'édition, et gère les gestes
 * d'édition (toucher, glisser un point).
 */
class GpxOverlay(private val density: Float) : Overlay() {

    interface Listener {
        fun onMapTap(lat: Double, lon: Double) {}
        fun onPointTap(ref: PointRef) {}
        fun onLineTap(line: LineRef, insertIndex: Int, lat: Double, lon: Double) {}
        fun onWaypointTap(index: Int) {}
        fun onPointDragged(ref: PointRef, lat: Double, lon: Double) {}
        fun onWaypointDragged(index: Int, lat: Double, lon: Double) {}
        fun onHandlesHiddenChanged(hidden: Boolean) {}
    }

    var listener: Listener? = null
    var lines: List<RenderLine> = emptyList()
    var waypoints: List<GpxPoint> = emptyList()
    private var waypointGeo: List<GeoPoint> = emptyList()
    var editMode = false
    var activeLine: LineRef? = null
    var dragPoints = false
    var dragWaypoints = false
    var highlight: GpxPoint? = null
    var selectedWaypoint: Int? = null
    var colors: MapColors? = null
    var showLabels = true

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12.5f * density; isFakeBoldText = true; textAlign = Paint.Align.CENTER
    }
    private val dash = DashPathEffect(floatArrayOf(10f * density, 8f * density), 0f)
    private val path = Path()
    private val arrowPath = Path()
    private val pt = Point()
    private val viewRect = Rect()
    private var handlesHidden = false

    private var dragLine: RenderLine? = null
    private var dragIndex = -1
    private var dragWaypoint = -1
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    fun setWaypointList(list: List<GpxPoint>) {
        waypoints = list
        waypointGeo = list.map { GeoPoint(it.lat, it.lon) }
    }

    private fun dp(v: Float) = v * density

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val c = colors ?: return
        val proj = mapView.projection
        viewRect.set(0, 0, mapView.width, mapView.height)
        val margin = dp(40f).toInt()
        viewRect.inset(-margin, -margin)

        // Lignes : d'abord les inactives, puis la ligne active par-dessus.
        val ordered = if (editMode && activeLine != null) lines.sortedBy { it.ref == activeLine } else lines
        for (line in ordered) {
            if (line.geo.size < 2) continue
            buildPath(proj, line.geo)
            val dim = editMode && activeLine != null && line.ref != activeLine
            linePaint.pathEffect = null
            linePaint.color = c.casing
            linePaint.alpha = if (dim) 120 else 235
            linePaint.strokeWidth = dp(8.5f)
            canvas.drawPath(path, linePaint)
            linePaint.color = line.color
            linePaint.alpha = if (dim) 140 else 255
            linePaint.strokeWidth = dp(5f)
            if (line.dashed) linePaint.pathEffect = dash
            canvas.drawPath(path, linePaint)
            linePaint.pathEffect = null
            if (!dim) drawArrows(canvas, proj, line.geo, c)
        }

        // Départ / arrivée de chaque ligne.
        if (!editMode) {
            for (line in lines) {
                if (line.geo.size < 2) continue
                drawEndpoint(canvas, proj, line.geo.first(), c.start, c)
                drawEndpoint(canvas, proj, line.geo.last(), c.end, c)
            }
        } else {
            drawHandles(canvas, proj, c)
        }

        // Repères.
        waypointGeo.forEachIndexed { i, g ->
            proj.toPixels(g, pt)
            if (!viewRect.contains(pt.x, pt.y)) return@forEachIndexed
            drawPin(canvas, pt.x.toFloat(), pt.y.toFloat(), if (selectedWaypoint == i) c.selectedWaypoint else c.waypoint, c,
                label = waypoints[i].name?.takeIf { showLabels && mapView.zoomLevelDouble >= 12.5 })
        }

        // Point mis en évidence depuis le profil altimétrique.
        highlight?.let { h ->
            proj.toPixels(GeoPoint(h.lat, h.lon), pt)
            fillPaint.color = c.highlight
            fillPaint.alpha = 70
            canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), dp(16f), fillPaint)
            fillPaint.alpha = 255
            fillPaint.color = c.casing
            canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), dp(8.5f), fillPaint)
            fillPaint.color = c.highlight
            canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), dp(6f), fillPaint)
        }
    }

    private fun buildPath(proj: Projection, geo: List<GeoPoint>) {
        path.rewind()
        var lastX = Int.MIN_VALUE
        var lastY = Int.MIN_VALUE
        for (i in geo.indices) {
            proj.toPixels(geo[i], pt)
            if (i == 0) {
                path.moveTo(pt.x.toFloat(), pt.y.toFloat())
            } else if (i == geo.lastIndex || kotlin.math.abs(pt.x - lastX) + kotlin.math.abs(pt.y - lastY) >= 2) {
                path.lineTo(pt.x.toFloat(), pt.y.toFloat())
            } else continue
            lastX = pt.x; lastY = pt.y
        }
    }

    /** Petits chevrons indiquant le sens de parcours. */
    private fun drawArrows(canvas: Canvas, proj: Projection, geo: List<GeoPoint>, c: MapColors) {
        val spacing = dp(90f)
        var acc = spacing * 0.5f
        var px = Float.NaN
        var py = Float.NaN
        fillPaint.color = c.casing
        fillPaint.alpha = 230
        val s = dp(3.2f)
        for (g in geo) {
            proj.toPixels(g, pt)
            val x = pt.x.toFloat()
            val y = pt.y.toFloat()
            if (!px.isNaN()) {
                val seg = hypot(x - px, y - py)
                if (seg > 0f) {
                    var t = spacing - acc
                    while (t <= seg) {
                        val ax = px + (x - px) * t / seg
                        val ay = py + (y - py) * t / seg
                        if (viewRect.contains(ax.toInt(), ay.toInt())) {
                            val angle = Math.toDegrees(atan2((y - py).toDouble(), (x - px).toDouble())).toFloat()
                            canvas.save()
                            canvas.rotate(angle, ax, ay)
                            arrowPath.rewind()
                            arrowPath.moveTo(ax + s, ay)
                            arrowPath.lineTo(ax - s, ay - s)
                            arrowPath.lineTo(ax - s * 0.4f, ay)
                            arrowPath.lineTo(ax - s, ay + s)
                            arrowPath.close()
                            canvas.drawPath(arrowPath, fillPaint)
                            canvas.restore()
                        }
                        t += spacing
                    }
                    acc = (acc + seg) % spacing
                }
            }
            px = x; py = y
        }
        fillPaint.alpha = 255
    }

    private fun drawEndpoint(canvas: Canvas, proj: Projection, g: GeoPoint, color: Int, c: MapColors) {
        proj.toPixels(g, pt)
        fillPaint.color = c.casing
        canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), dp(8f), fillPaint)
        fillPaint.color = color
        canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), dp(5.5f), fillPaint)
    }

    private fun drawHandles(canvas: Canvas, proj: Projection, c: MapColors) {
        var visible = 0
        for (line in lines) for (g in line.geo) {
            proj.toPixels(g, pt)
            if (viewRect.contains(pt.x, pt.y)) visible++
        }
        val hide = visible > MAX_HANDLES
        if (hide != handlesHidden) {
            handlesHidden = hide
            listener?.onHandlesHiddenChanged(hide)
        }
        if (hide) {
            // On garde au moins les extrémités de la ligne active, pour savoir où l'on dessine.
            lines.firstOrNull { it.ref == activeLine }?.geo?.lastOrNull()?.let {
                proj.toPixels(it, pt)
                drawHandle(canvas, pt.x.toFloat(), pt.y.toFloat(), c, active = true, big = true)
            }
            return
        }
        for (line in lines) {
            val active = line.ref == activeLine
            line.geo.forEachIndexed { i, g ->
                proj.toPixels(g, pt)
                if (!viewRect.contains(pt.x, pt.y)) return@forEachIndexed
                val big = i == 0 || i == line.geo.lastIndex || (line === dragLine && i == dragIndex)
                drawHandle(canvas, pt.x.toFloat(), pt.y.toFloat(), c, active, big)
            }
        }
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float, c: MapColors, active: Boolean, big: Boolean) {
        val r = if (big) dp(8f) else dp(5.5f)
        fillPaint.color = c.handleFill
        canvas.drawCircle(x, y, r, fillPaint)
        strokePaint.color = if (active) c.activeHandle else c.handleStroke
        strokePaint.strokeWidth = if (big) dp(3f) else dp(2.2f)
        canvas.drawCircle(x, y, r, strokePaint)
    }

    private fun drawPin(canvas: Canvas, x: Float, y: Float, color: Int, c: MapColors, label: String?) {
        val r = dp(11f)
        val cy = y - dp(19f)
        path.rewind()
        path.moveTo(x, y)
        path.lineTo(x - r * 0.62f, cy + r * 0.72f)
        path.lineTo(x + r * 0.62f, cy + r * 0.72f)
        path.close()
        fillPaint.color = c.casing
        canvas.drawCircle(x, cy, r + dp(2.5f), fillPaint)
        strokePaint.color = c.casing
        strokePaint.strokeWidth = dp(5f)
        canvas.drawPath(path, strokePaint)
        fillPaint.color = color
        canvas.drawPath(path, fillPaint)
        canvas.drawCircle(x, cy, r, fillPaint)
        fillPaint.color = c.onWaypoint
        canvas.drawCircle(x, cy, r * 0.36f, fillPaint)
        if (label != null) {
            val text = if (label.length > 24) label.take(23) + "…" else label
            textPaint.style = Paint.Style.STROKE
            textPaint.strokeWidth = dp(3.5f)
            textPaint.color = c.labelHalo
            canvas.drawText(text, x, y + dp(15f), textPaint)
            textPaint.style = Paint.Style.FILL
            textPaint.color = c.label
            canvas.drawText(text, x, y + dp(15f), textPaint)
        }
    }

    // ---------------------------------------------------------------- Gestes

    private fun hitWaypoint(x: Float, y: Float, proj: Projection): Int {
        var best = -1
        var bestD = dp(26f)
        waypointGeo.forEachIndexed { i, g ->
            proj.toPixels(g, pt)
            // La tête de l'épingle est au-dessus du point.
            val d = minOf(hypot(pt.x - x, pt.y - y), hypot(pt.x - x, pt.y - dp(19f) - y))
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    private fun hitPoint(x: Float, y: Float, proj: Projection): Pair<RenderLine, Int>? {
        if (handlesHidden) return null
        var best: Pair<RenderLine, Int>? = null
        var bestD = dp(22f)
        // La ligne active est prioritaire en cas d'égalité.
        for (line in lines.sortedByDescending { it.ref == activeLine }) {
            line.geo.forEachIndexed { i, g ->
                proj.toPixels(g, pt)
                val d = hypot(pt.x - x, pt.y - y)
                if (d < bestD - 0.01f) { bestD = d; best = line to i }
            }
        }
        return best
    }

    /** Segment le plus proche du toucher : renvoie la ligne et l'index où insérer. */
    private fun hitSegment(x: Float, y: Float, proj: Projection): Pair<RenderLine, Int>? {
        var best: Pair<RenderLine, Int>? = null
        var bestD = dp(16f)
        for (line in lines) {
            var px = 0f
            var py = 0f
            line.geo.forEachIndexed { i, g ->
                proj.toPixels(g, pt)
                val cx = pt.x.toFloat()
                val cy = pt.y.toFloat()
                if (i > 0) {
                    val d = segDist(x, y, px, py, cx, cy)
                    if (d < bestD) { bestD = d; best = line to i }
                }
                px = cx; py = cy
            }
        }
        return best
    }

    private fun segDist(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((x - ax) * dx + (y - ay) * dy) / len2).coerceIn(0f, 1f)
        return hypot(ax + t * dx - x, ay + t * dy - y)
    }

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        if (!editMode) return false
        val proj = mapView.projection
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; moved = false
                if (dragWaypoints) {
                    val w = hitWaypoint(event.x, event.y, proj)
                    if (w >= 0) { dragWaypoint = w; return true }
                }
                if (dragPoints) {
                    hitPoint(event.x, event.y, proj)?.let { (line, i) ->
                        dragLine = line; dragIndex = i
                        mapView.invalidate()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragLine == null && dragWaypoint < 0) return false
                val slop = ViewConfiguration.get(mapView.context).scaledTouchSlop
                if (!moved && hypot(event.x - downX, event.y - downY) < slop) return true
                moved = true
                val g = proj.fromPixels(event.x.toInt(), event.y.toInt())
                dragLine?.let { line -> line.geo[dragIndex].setCoords(g.latitude, g.longitude) }
                if (dragWaypoint >= 0) waypointGeo.getOrNull(dragWaypoint)?.setCoords(g.latitude, g.longitude)
                mapView.invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val line = dragLine
                val i = dragIndex
                val wp = dragWaypoint
                dragLine = null; dragIndex = -1; dragWaypoint = -1
                if (line == null && wp < 0) return false
                if (event.actionMasked == MotionEvent.ACTION_CANCEL) { mapView.invalidate(); return true }
                if (line != null && i in line.geo.indices) {
                    if (moved) {
                        val g = line.geo[i]
                        listener?.onPointDragged(PointRef(line.ref, i), g.latitude, g.longitude)
                    } else {
                        listener?.onPointTap(PointRef(line.ref, i))
                    }
                } else if (wp >= 0 && wp in waypointGeo.indices) {
                    if (moved) {
                        val g = waypointGeo[wp]
                        listener?.onWaypointDragged(wp, g.latitude, g.longitude)
                    } else listener?.onWaypointTap(wp)
                }
                mapView.invalidate()
                return true
            }
        }
        return false
    }

    override fun onSingleTapUp(e: MotionEvent, mapView: MapView): Boolean {
        if (!editMode) return false
        handleEditTap(e, mapView)
        return true
    }

    override fun onDoubleTap(e: MotionEvent, mapView: MapView): Boolean {
        // En édition, un double appui ajoute un point au lieu de zoomer.
        if (!editMode) return false
        handleEditTap(e, mapView)
        return true
    }

    private fun handleEditTap(e: MotionEvent, mapView: MapView) {
        val proj = mapView.projection
        val w = hitWaypoint(e.x, e.y, proj)
        if (w >= 0) { listener?.onWaypointTap(w); return }
        hitPoint(e.x, e.y, proj)?.let { (line, i) -> listener?.onPointTap(PointRef(line.ref, i)); return }
        val g = proj.fromPixels(e.x.toInt(), e.y.toInt())
        hitSegment(e.x, e.y, proj)?.let { (line, i) ->
            listener?.onLineTap(line.ref, i, g.latitude, g.longitude); return
        }
        listener?.onMapTap(g.latitude, g.longitude)
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        if (editMode) return false
        val w = hitWaypoint(e.x, e.y, mapView.projection)
        if (w >= 0) { listener?.onWaypointTap(w); return true }
        val g = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt())
        listener?.onMapTap(g.latitude, g.longitude)
        return false
    }

    companion object {
        const val MAX_HANDLES = 450
    }
}
