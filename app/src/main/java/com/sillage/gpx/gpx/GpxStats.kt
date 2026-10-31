package com.sillage.gpx.gpx

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    const val EARTH_RADIUS_M = 6_371_008.8

    /** Distance orthodromique (haversine) en mètres. */
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    fun distance(a: GpxPoint, b: GpxPoint) = distance(a.lat, a.lon, b.lat, b.lon)

    fun length(points: List<GpxPoint>): Double {
        var d = 0.0
        for (i in 1 until points.size) d += distance(points[i - 1], points[i])
        return d
    }
}

@Immutable
data class GpxStats(
    val distanceM: Double = 0.0,
    val ascentM: Double = 0.0,
    val descentM: Double = 0.0,
    val minEle: Double? = null,
    val maxEle: Double? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    /** Durée totale (ms) entre le premier et le dernier point horodaté. */
    val durationMs: Long? = null,
    /** Temps en mouvement (ms), pauses exclues. */
    val movingMs: Long? = null,
    val maxSpeedMs: Double? = null,
    val pointCount: Int = 0,
    val waypointCount: Int = 0,
    val trackCount: Int = 0,
    val routeCount: Int = 0,
    val segmentCount: Int = 0,
) {
    /** Vitesse moyenne en mouvement (m/s). */
    val avgMovingSpeedMs: Double?
        get() = movingMs?.takeIf { it > 0 }?.let { distanceM / (it / 1000.0) }

    val avgSpeedMs: Double?
        get() = durationMs?.takeIf { it > 0 }?.let { distanceM / (it / 1000.0) }

    companion object {
        /** En dessous de ce seuil, une variation d'altitude est considérée comme du bruit GPS. */
        private const val ELEVATION_THRESHOLD_M = 5.0
        /** Vitesse (≈ 1 km/h) en dessous de laquelle on considère être à l'arrêt : une montée raide reste « active ». */
        private const val MOVING_SPEED_MS = 0.3
        /** Écart maximal entre deux points pour qu'ils comptent dans le temps en mouvement. */
        private const val MAX_GAP_MS = 5 * 60 * 1000L

        /** Demi-fenêtre (m) de la moyenne glissante appliquée aux altitudes avant le calcul du dénivelé. */
        private const val SMOOTHING_HALF_WINDOW_M = 50.0

        /**
         * Moyenne glissante pondérée par la distance : gomme le bruit des altitudes GPS ou issues d'un modèle
         * numérique de terrain (lacets), sans effet sur les traces peu denses. Les points sans altitude valent NaN.
         */
        internal fun smoothedElevations(line: List<GpxPoint>): DoubleArray {
            val n = line.size
            val dist = DoubleArray(n)
            for (i in 1 until n) dist[i] = dist[i - 1] + Geo.distance(line[i - 1], line[i])
            val out = DoubleArray(n) { Double.NaN }
            var lo = 0
            var hi = -1
            var sum = 0.0
            var cnt = 0
            for (i in 0 until n) {
                while (hi + 1 < n && dist[hi + 1] - dist[i] <= SMOOTHING_HALF_WINDOW_M) {
                    hi++
                    line[hi].ele?.let { sum += it; cnt++ }
                }
                while (dist[i] - dist[lo] > SMOOTHING_HALF_WINDOW_M) {
                    line[lo].ele?.let { sum -= it; cnt-- }
                    lo++
                }
                if (line[i].ele != null && cnt > 0) out[i] = sum / cnt
            }
            return out
        }

        fun of(doc: GpxDocument): GpxStats {
            val lines = doc.tracks.flatMap { t -> t.segments.map { it.points } }.ifEmpty { doc.routes.map { it.points } }
            var distance = 0.0
            var ascent = 0.0
            var descent = 0.0
            var minEle: Double? = null
            var maxEle: Double? = null
            var start: Long? = null
            var end: Long? = null
            var moving = 0L
            var hasTime = false
            var maxSpeed: Double? = null
            var count = 0

            for (line in lines) {
                count += line.size
                val smooth = smoothedElevations(line)
                var ref: Double? = null // altitude de référence pour l'hystérésis
                for (i in line.indices) {
                    val p = line[i]
                    p.ele?.let { raw ->
                        minEle = minEle?.let { min(it, raw) } ?: raw
                        maxEle = maxEle?.let { max(it, raw) } ?: raw
                        val e = smooth[i]
                        val r = ref
                        if (r == null) {
                            ref = e
                        } else {
                            val diff = e - r
                            if (abs(diff) >= ELEVATION_THRESHOLD_M) {
                                if (diff > 0) ascent += diff else descent -= diff
                                ref = e
                            }
                        }
                    }
                    p.time?.let { t ->
                        start = start?.let { min(it, t) } ?: t
                        end = end?.let { max(it, t) } ?: t
                    }
                    if (i > 0) {
                        val prev = line[i - 1]
                        val d = Geo.distance(prev, p)
                        distance += d
                        val t0 = prev.time
                        val t1 = p.time
                        if (t0 != null && t1 != null && t1 > t0) {
                            hasTime = true
                            val dt = t1 - t0
                            val speed = d / (dt / 1000.0)
                            if (speed >= MOVING_SPEED_MS && dt <= MAX_GAP_MS) moving += dt
                            // On ignore les sauts aberrants (> 300 km/h) dans la vitesse max.
                            if (dt >= 1000 && speed < 83.0) maxSpeed = maxSpeed?.let { max(it, speed) } ?: speed
                        }
                    }
                }
            }
            val s = start
            val e = end
            return GpxStats(
                distanceM = distance,
                ascentM = ascent,
                descentM = descent,
                minEle = minEle,
                maxEle = maxEle,
                startTime = s,
                endTime = e,
                durationMs = if (s != null && e != null && e > s) e - s else null,
                movingMs = if (hasTime) moving else null,
                maxSpeedMs = maxSpeed,
                pointCount = count,
                waypointCount = doc.waypoints.size,
                trackCount = doc.tracks.size,
                routeCount = doc.routes.size,
                segmentCount = doc.tracks.sumOf { it.segments.size },
            )
        }
    }
}

/** Profil altimétrique : distance cumulée (m) et altitude (m) pour chaque point des traces. */
@Immutable
class ElevationProfile(
    val distances: DoubleArray,
    val elevations: DoubleArray,
    /** Point d'origine de chaque échantillon (pour le relier à la carte). */
    val points: List<GpxPoint>,
) {
    val size: Int get() = distances.size
    val totalDistance: Double get() = if (distances.isEmpty()) 0.0 else distances.last()
    val minEle: Double get() = elevations.minOrNull() ?: 0.0
    val maxEle: Double get() = elevations.maxOrNull() ?: 0.0

    /** Index de l'échantillon le plus proche d'une distance donnée. */
    fun indexAt(distance: Double): Int {
        if (distances.isEmpty()) return -1
        var lo = 0
        var hi = distances.size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (distances[mid] < distance) lo = mid + 1 else hi = mid
        }
        if (lo > 0 && abs(distances[lo - 1] - distance) < abs(distances[lo] - distance)) return lo - 1
        return lo
    }

    companion object {
        fun of(doc: GpxDocument): ElevationProfile? {
            val lines = doc.tracks.flatMap { t -> t.segments.map { it.points } }.ifEmpty { doc.routes.map { it.points } }
            val pts = ArrayList<GpxPoint>()
            val dist = ArrayList<Double>()
            val ele = ArrayList<Double>()
            var cumulative = 0.0
            var prev: GpxPoint? = null
            for (line in lines) {
                for (p in line) {
                    // Les segments sont mis bout à bout sans compter le saut entre eux.
                    if (prev != null && p !== line.first()) cumulative += Geo.distance(prev, p)
                    prev = p
                    val e = p.ele ?: continue
                    pts.add(p); dist.add(cumulative); ele.add(e)
                }
            }
            if (ele.size < 2) return null
            return ElevationProfile(dist.toDoubleArray(), ele.toDoubleArray(), pts)
        }
    }
}
