package com.sillage.gpx.gpx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GpxStatsTest {

    private fun line(vararg pts: GpxPoint) = GpxDocument(tracks = listOf(GpxTrack(segments = listOf(GpxSegment(pts.toList())))))

    @Test
    fun haversineMatchesKnownDistance() {
        // Paris (Notre-Dame) → Lyon (Bellecour) ≈ 391,5 km
        val d = Geo.distance(48.8530, 2.3499, 45.7578, 4.8320)
        assertEquals(391_500.0, d, 2_000.0)
    }

    @Test
    fun computesDistanceElevationAndTime() {
        val t0 = 1_700_000_000_000L
        // 0.001° de latitude ≈ 111,2 m
        val doc = line(
            GpxPoint(45.000, 6.0, ele = 100.0, time = t0),
            GpxPoint(45.001, 6.0, ele = 110.0, time = t0 + 60_000),
            GpxPoint(45.002, 6.0, ele = 101.0, time = t0 + 120_000),
            GpxPoint(45.003, 6.0, ele = 102.0, time = t0 + 180_000), // +1 m : sous le seuil de bruit (5 m)
        )
        val s = GpxStats.of(doc)
        assertEquals(333.6, s.distanceM, 1.0)
        assertEquals(10.0, s.ascentM, 1e-9)
        assertEquals(9.0, s.descentM, 1e-9)
        assertEquals(100.0, s.minEle!!, 0.0)
        assertEquals(110.0, s.maxEle!!, 0.0)
        assertEquals(180_000L, s.durationMs)
        assertEquals(180_000L, s.movingMs)
        assertEquals(4, s.pointCount)
        assertEquals(333.6 / 180.0, s.avgMovingSpeedMs!!, 0.01)
    }

    @Test
    fun noisyElevationDoesNotInflateAscent() {
        // Montée régulière de 100 m sur 1 km, points tous les ~11 m, avec un bruit de ±15 m (lacets sur un MNT).
        val pts = (0..90).map { i ->
            val noise = if (i % 2 == 0) 15.0 else -15.0
            GpxPoint(45.0 + i * 0.0001, 6.0, ele = 1000.0 + i * 100.0 / 90 + if (i in 1 until 90) noise else 0.0)
        }
        val s = GpxStats.of(line(*pts.toTypedArray()))
        assertEquals(100.0, s.ascentM, 15.0)
        assertEquals(0.0, s.descentM, 15.0)
        // Les extrêmes restent ceux des données brutes.
        assertEquals(985.0 + 100.0 / 90 * 1, s.minEle!!, 1.0)
    }

    @Test
    fun pausesAreExcludedFromMovingTime() {
        val t0 = 0L
        val doc = line(
            GpxPoint(45.000, 6.0, time = t0),
            GpxPoint(45.001, 6.0, time = t0 + 60_000),
            GpxPoint(45.001, 6.0, time = t0 + 660_000), // 10 min sur place
            GpxPoint(45.002, 6.0, time = t0 + 720_000),
        )
        val s = GpxStats.of(doc)
        assertEquals(720_000L, s.durationMs)
        assertEquals(120_000L, s.movingMs)
    }

    @Test
    fun routesAreUsedWhenNoTrack() {
        val doc = GpxDocument(routes = listOf(GpxRoute(points = listOf(GpxPoint(0.0, 0.0), GpxPoint(0.0, 0.001)))))
        val s = GpxStats.of(doc)
        assertEquals(111.2, s.distanceM, 0.5)
        assertNull(s.durationMs)
        assertNull(s.maxEle)
    }

    @Test
    fun elevationProfileIsCumulativeAndSearchable() {
        val doc = line(
            GpxPoint(45.000, 6.0, ele = 100.0),
            GpxPoint(45.001, 6.0),
            GpxPoint(45.002, 6.0, ele = 120.0),
        )
        val p = ElevationProfile.of(doc)
        assertNotNull(p)
        p!!
        assertEquals(2, p.size)
        assertEquals(0.0, p.distances[0], 0.0)
        assertEquals(222.4, p.distances[1], 0.5)
        assertEquals(0, p.indexAt(50.0))
        assertEquals(1, p.indexAt(200.0))
        assertEquals(1, p.indexAt(10_000.0))
    }
}
