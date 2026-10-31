package com.sillage.gpx.gpx

import androidx.compose.runtime.Immutable

/** Un point GPX (wpt, rtept ou trkpt). */
@Immutable
data class GpxPoint(
    val lat: Double,
    val lon: Double,
    val ele: Double? = null,
    /** Horodatage en millisecondes UTC. */
    val time: Long? = null,
    val name: String? = null,
    val desc: String? = null,
    val cmt: String? = null,
    val sym: String? = null,
    val type: String? = null,
)

@Immutable
data class GpxSegment(val points: List<GpxPoint> = emptyList())

@Immutable
data class GpxTrack(
    val name: String? = null,
    val desc: String? = null,
    val type: String? = null,
    val segments: List<GpxSegment> = listOf(GpxSegment()),
) {
    val allPoints: List<GpxPoint> get() = segments.flatMap { it.points }
    val pointCount: Int get() = segments.sumOf { it.points.size }
}

@Immutable
data class GpxRoute(
    val name: String? = null,
    val desc: String? = null,
    val type: String? = null,
    val points: List<GpxPoint> = emptyList(),
)

@Immutable
data class GpxMetadata(
    val name: String? = null,
    val desc: String? = null,
    val author: String? = null,
    val time: Long? = null,
    val keywords: String? = null,
)

@Immutable
data class GpxDocument(
    val metadata: GpxMetadata = GpxMetadata(),
    val waypoints: List<GpxPoint> = emptyList(),
    val routes: List<GpxRoute> = emptyList(),
    val tracks: List<GpxTrack> = emptyList(),
    val creator: String? = null,
) {
    val isEmpty: Boolean
        get() = waypoints.isEmpty() && routes.all { it.points.isEmpty() } && tracks.all { it.pointCount == 0 }

    /** Toutes les lignes (segments de trace + itinéraires), utile pour le rendu et l'emprise. */
    val lines: List<List<GpxPoint>>
        get() = tracks.flatMap { t -> t.segments.map { it.points } } + routes.map { it.points }

    val allPoints: Sequence<GpxPoint>
        get() = sequence {
            yieldAll(waypoints)
            routes.forEach { yieldAll(it.points) }
            tracks.forEach { t -> t.segments.forEach { yieldAll(it.points) } }
        }

    val displayName: String?
        get() = metadata.name?.takeIf { it.isNotBlank() }
            ?: tracks.firstNotNullOfOrNull { it.name?.takeIf(String::isNotBlank) }
            ?: routes.firstNotNullOfOrNull { it.name?.takeIf(String::isNotBlank) }

    fun bounds(): GeoBounds? = GeoBounds.of(allPoints)
}

@Immutable
data class GeoBounds(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double) {
    val centerLat: Double get() = (minLat + maxLat) / 2
    val centerLon: Double get() = (minLon + maxLon) / 2

    companion object {
        fun of(points: Sequence<GpxPoint>): GeoBounds? {
            var minLat = Double.POSITIVE_INFINITY
            var minLon = Double.POSITIVE_INFINITY
            var maxLat = Double.NEGATIVE_INFINITY
            var maxLon = Double.NEGATIVE_INFINITY
            var any = false
            for (p in points) {
                any = true
                if (p.lat < minLat) minLat = p.lat
                if (p.lat > maxLat) maxLat = p.lat
                if (p.lon < minLon) minLon = p.lon
                if (p.lon > maxLon) maxLon = p.lon
            }
            return if (any) GeoBounds(minLat, minLon, maxLat, maxLon) else null
        }
    }
}
