package com.sillage.gpx.gpx

/** Référence vers une ligne éditable : un segment de trace ou un itinéraire. */
sealed interface LineRef {
    data class Track(val track: Int, val segment: Int) : LineRef
    data class Route(val route: Int) : LineRef
}

data class PointRef(val line: LineRef, val index: Int)

/** Opérations d'édition pures : chaque fonction renvoie un nouveau document. */
object GpxEdit {

    fun lineRefs(doc: GpxDocument): List<LineRef> =
        doc.tracks.flatMapIndexed { t, track -> track.segments.indices.map { LineRef.Track(t, it) } } +
            doc.routes.indices.map { LineRef.Route(it) }

    fun points(doc: GpxDocument, line: LineRef): List<GpxPoint> = when (line) {
        is LineRef.Track -> doc.tracks.getOrNull(line.track)?.segments?.getOrNull(line.segment)?.points.orEmpty()
        is LineRef.Route -> doc.routes.getOrNull(line.route)?.points.orEmpty()
    }

    fun exists(doc: GpxDocument, line: LineRef): Boolean = when (line) {
        is LineRef.Track -> doc.tracks.getOrNull(line.track)?.segments?.getOrNull(line.segment) != null
        is LineRef.Route -> doc.routes.getOrNull(line.route) != null
    }

    fun setPoints(doc: GpxDocument, line: LineRef, points: List<GpxPoint>): GpxDocument = when (line) {
        is LineRef.Track -> doc.copy(tracks = doc.tracks.mapIndexed { t, track ->
            if (t != line.track) track else track.copy(segments = track.segments.mapIndexed { s, seg ->
                if (s != line.segment) seg else seg.copy(points = points)
            })
        })
        is LineRef.Route -> doc.copy(routes = doc.routes.mapIndexed { r, route ->
            if (r != line.route) route else route.copy(points = points)
        })
    }

    private inline fun editPoints(doc: GpxDocument, line: LineRef, block: (MutableList<GpxPoint>) -> Unit): GpxDocument {
        val list = points(doc, line).toMutableList()
        block(list)
        return setPoints(doc, line, list)
    }

    /** Garantit qu'il existe une ligne où dessiner, et la renvoie. */
    fun ensureLine(doc: GpxDocument, preferred: LineRef?): Pair<GpxDocument, LineRef> {
        if (preferred != null && exists(doc, preferred)) return doc to preferred
        lineRefs(doc).firstOrNull()?.let { return doc to it }
        val track = GpxTrack(name = doc.metadata.name ?: "Trace 1", segments = listOf(GpxSegment()))
        val newDoc = doc.copy(tracks = doc.tracks + track)
        return newDoc to LineRef.Track(newDoc.tracks.lastIndex, 0)
    }

    fun append(doc: GpxDocument, line: LineRef, newPoints: List<GpxPoint>): GpxDocument =
        editPoints(doc, line) { it.addAll(newPoints) }

    fun insert(doc: GpxDocument, ref: PointRef, point: GpxPoint): GpxDocument =
        editPoints(doc, ref.line) { it.add(ref.index.coerceIn(0, it.size), point) }

    fun move(doc: GpxDocument, ref: PointRef, lat: Double, lon: Double): GpxDocument =
        editPoints(doc, ref.line) { list ->
            if (ref.index in list.indices) list[ref.index] = list[ref.index].copy(lat = lat, lon = lon, ele = null)
        }

    fun delete(doc: GpxDocument, ref: PointRef): GpxDocument =
        editPoints(doc, ref.line) { if (ref.index in it.indices) it.removeAt(ref.index) }

    fun reverse(doc: GpxDocument, line: LineRef): GpxDocument = editPoints(doc, line) { it.reverse() }

    fun addWaypoint(doc: GpxDocument, point: GpxPoint): GpxDocument = doc.copy(waypoints = doc.waypoints + point)

    fun updateWaypoint(doc: GpxDocument, index: Int, point: GpxPoint): GpxDocument =
        doc.copy(waypoints = doc.waypoints.mapIndexed { i, p -> if (i == index) point else p })

    fun moveWaypoint(doc: GpxDocument, index: Int, lat: Double, lon: Double): GpxDocument =
        doc.copy(waypoints = doc.waypoints.mapIndexed { i, p -> if (i == index) p.copy(lat = lat, lon = lon, ele = null) else p })

    fun deleteWaypoint(doc: GpxDocument, index: Int): GpxDocument =
        doc.copy(waypoints = doc.waypoints.filterIndexed { i, _ -> i != index })

    fun newTrack(doc: GpxDocument, name: String): Pair<GpxDocument, LineRef> {
        val newDoc = doc.copy(tracks = doc.tracks + GpxTrack(name = name, segments = listOf(GpxSegment())))
        return newDoc to LineRef.Track(newDoc.tracks.lastIndex, 0)
    }

    fun newSegment(doc: GpxDocument, track: Int): Pair<GpxDocument, LineRef> {
        if (track !in doc.tracks.indices) return newTrack(doc, "Trace ${doc.tracks.size + 1}")
        val newDoc = doc.copy(tracks = doc.tracks.mapIndexed { i, t ->
            if (i == track) t.copy(segments = t.segments + GpxSegment()) else t
        })
        return newDoc to LineRef.Track(track, newDoc.tracks[track].segments.lastIndex)
    }

    fun renameTrack(doc: GpxDocument, track: Int, name: String): GpxDocument =
        doc.copy(tracks = doc.tracks.mapIndexed { i, t -> if (i == track) t.copy(name = name.ifBlank { null }) else t })

    fun renameRoute(doc: GpxDocument, route: Int, name: String): GpxDocument =
        doc.copy(routes = doc.routes.mapIndexed { i, r -> if (i == route) r.copy(name = name.ifBlank { null }) else r })

    fun deleteLine(doc: GpxDocument, line: LineRef): GpxDocument = when (line) {
        is LineRef.Track -> {
            val track = doc.tracks.getOrNull(line.track)
            if (track == null) doc
            else if (track.segments.size <= 1) doc.copy(tracks = doc.tracks.filterIndexed { i, _ -> i != line.track })
            else doc.copy(tracks = doc.tracks.mapIndexed { i, t ->
                if (i == line.track) t.copy(segments = t.segments.filterIndexed { s, _ -> s != line.segment }) else t
            })
        }
        is LineRef.Route -> doc.copy(routes = doc.routes.filterIndexed { i, _ -> i != line.route })
    }

    /** Convertit un itinéraire en trace (et inversement), pour les applis qui n'acceptent qu'un des deux. */
    fun routeToTrack(doc: GpxDocument, route: Int): GpxDocument {
        val r = doc.routes.getOrNull(route) ?: return doc
        return doc.copy(
            routes = doc.routes.filterIndexed { i, _ -> i != route },
            tracks = doc.tracks + GpxTrack(r.name, r.desc, r.type, listOf(GpxSegment(r.points))),
        )
    }

    fun trackToRoute(doc: GpxDocument, track: Int): GpxDocument {
        val t = doc.tracks.getOrNull(track) ?: return doc
        return doc.copy(
            tracks = doc.tracks.filterIndexed { i, _ -> i != track },
            routes = doc.routes + GpxRoute(t.name, t.desc, t.type, t.allPoints),
        )
    }

    /** Simplification Douglas-Peucker (tolérance en mètres). */
    fun simplify(doc: GpxDocument, line: LineRef, toleranceM: Double): GpxDocument =
        setPoints(doc, line, douglasPeucker(points(doc, line), toleranceM))

    fun simplifyAll(doc: GpxDocument, toleranceM: Double): GpxDocument =
        lineRefs(doc).fold(doc) { d, l -> simplify(d, l, toleranceM) }

    fun douglasPeucker(points: List<GpxPoint>, toleranceM: Double): List<GpxPoint> {
        if (points.size < 3) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(0, points.lastIndex))
        val refLat = Math.toRadians(points[0].lat)
        val mPerDegLat = 111_320.0
        val mPerDegLon = 111_320.0 * kotlin.math.cos(refLat)
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast().let { it[0] to it[1] }
            if (b <= a + 1) continue
            val ax = points[a].lon * mPerDegLon; val ay = points[a].lat * mPerDegLat
            val bx = points[b].lon * mPerDegLon; val by = points[b].lat * mPerDegLat
            var maxD = -1.0
            var idx = -1
            for (i in a + 1 until b) {
                val px = points[i].lon * mPerDegLon; val py = points[i].lat * mPerDegLat
                val d = segmentDistance(px, py, ax, ay, bx, by)
                if (d > maxD) { maxD = d; idx = i }
            }
            if (maxD > toleranceM && idx > 0) {
                keep[idx] = true
                stack.addLast(intArrayOf(a, idx))
                stack.addLast(intArrayOf(idx, b))
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    fun segmentDistance(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + t * dx - px
        val cy = ay + t * dy - py
        return kotlin.math.sqrt(cx * cx + cy * cy)
    }

    /** Applique des altitudes (même ordre que [missingElevationPoints]). */
    fun withElevations(doc: GpxDocument, elevations: Map<Pair<Double, Double>, Double>): GpxDocument {
        fun fix(p: GpxPoint) = if (p.ele == null) elevations[p.lat to p.lon]?.let { p.copy(ele = it) } ?: p else p
        return doc.copy(
            waypoints = doc.waypoints.map(::fix),
            routes = doc.routes.map { r -> r.copy(points = r.points.map(::fix)) },
            tracks = doc.tracks.map { t -> t.copy(segments = t.segments.map { s -> s.copy(points = s.points.map(::fix)) }) },
        )
    }

    fun missingElevationPoints(doc: GpxDocument): List<Pair<Double, Double>> =
        doc.allPoints.filter { it.ele == null }.map { it.lat to it.lon }.distinct().toList()
}
