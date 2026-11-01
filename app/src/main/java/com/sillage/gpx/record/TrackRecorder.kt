package com.sillage.gpx.record

import com.sillage.gpx.gpx.Geo
import com.sillage.gpx.gpx.GpxPoint
import kotlin.math.max

/**
 * Filtre les positions reçues pour construire une trace propre :
 * - positions trop imprécises ignorées ;
 * - pas de points en rafale quand on est immobile (seuil de distance lié à la précision) ;
 * - sauts aberrants (> 250 km/h) rejetés, sauf s'ils se confirment plusieurs fois de suite ;
 * - une pause démarre une nouvelle section (trkseg).
 */
class TrackRecorder(
    private val maxAccuracyM: Float = 35f,
    private val minDistanceM: Double = 5.0,
    private val maxIntervalMs: Long = 30_000,
    private val maxSpeedMs: Double = 70.0,
) {
    private val segs = ArrayList<ArrayList<GpxPoint>>()
    var distanceM = 0.0
        private set
    private var rejectedJumps = 0

    val segments: List<List<GpxPoint>> get() = segs.map { ArrayList(it) }
    val pointCount: Int get() = segs.sumOf { it.size }
    val lastPoint: GpxPoint? get() = segs.lastOrNull()?.lastOrNull()

    fun newSegment() {
        if (segs.isEmpty() || segs.last().isNotEmpty()) segs.add(ArrayList())
    }

    /** Recharge une trace (reprise après redémarrage du service). */
    fun restore(segments: List<List<GpxPoint>>) {
        segs.clear()
        segments.forEach { segs.add(ArrayList(it)) }
        distanceM = segments.sumOf { Geo.length(it) }
    }

    /** Propose une position ; renvoie le point ajouté, ou null s'il est filtré. */
    fun offer(lat: Double, lon: Double, ele: Double?, time: Long, accuracyM: Float?): GpxPoint? {
        if (accuracyM != null && accuracyM > maxAccuracyM) return null
        if (segs.isEmpty()) segs.add(ArrayList())
        val seg = segs.last()
        val last = seg.lastOrNull()
        var added = 0.0
        if (last != null) {
            val d = Geo.distance(last.lat, last.lon, lat, lon)
            val dt = time - (last.time ?: time)
            if (dt <= 0) return null
            if (d / (dt / 1000.0) > maxSpeedMs && rejectedJumps < 4) {
                rejectedJumps++
                return null
            }
            val threshold = max(minDistanceM, (accuracyM ?: 0f) * 0.5)
            if (d < threshold && dt < maxIntervalMs) return null
            added = d
        }
        rejectedJumps = 0
        val p = GpxPoint(lat, lon, ele, time)
        seg.add(p)
        distanceM += added
        return p
    }
}
