package com.sillage.gpx.gpx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxEditTest {
    private val line = LineRef.Track(0, 0)
    private val base = GpxDocument(tracks = listOf(GpxTrack(name = "T", segments = listOf(GpxSegment(listOf(
        GpxPoint(0.0, 0.0, ele = 1.0), GpxPoint(0.0, 1.0, ele = 2.0), GpxPoint(0.0, 2.0, ele = 3.0),
    ))))))

    @Test
    fun appendInsertMoveDelete() {
        var d = GpxEdit.append(base, line, listOf(GpxPoint(0.0, 3.0)))
        assertEquals(4, GpxEdit.points(d, line).size)
        d = GpxEdit.insert(d, PointRef(line, 1), GpxPoint(0.5, 0.5))
        assertEquals(0.5, GpxEdit.points(d, line)[1].lat, 0.0)
        d = GpxEdit.move(d, PointRef(line, 0), 1.0, 1.0)
        assertEquals(1.0, GpxEdit.points(d, line)[0].lat, 0.0)
        assertNull("l'altitude d'un point déplacé n'est plus fiable", GpxEdit.points(d, line)[0].ele)
        d = GpxEdit.delete(d, PointRef(line, 0))
        assertEquals(4, GpxEdit.points(d, line).size)
        // Le document d'origine n'est jamais modifié (indispensable pour annuler/rétablir).
        assertEquals(3, GpxEdit.points(base, line).size)
    }

    @Test
    fun ensureLineCreatesTrackOnEmptyDocument() {
        val (d, ref) = GpxEdit.ensureLine(GpxDocument(), null)
        assertEquals(1, d.tracks.size)
        assertEquals(LineRef.Track(0, 0), ref)
    }

    @Test
    fun reverseAndSegments() {
        val r = GpxEdit.reverse(base, line)
        assertEquals(2.0, GpxEdit.points(r, line)[0].lon, 0.0)
        val (withSeg, seg) = GpxEdit.newSegment(base, 0)
        assertEquals(LineRef.Track(0, 1), seg)
        assertEquals(2, withSeg.tracks[0].segments.size)
        val deleted = GpxEdit.deleteLine(withSeg, seg)
        assertEquals(1, deleted.tracks[0].segments.size)
    }

    @Test
    fun convertTrackToRouteAndBack() {
        val asRoute = GpxEdit.trackToRoute(base, 0)
        assertTrue(asRoute.tracks.isEmpty())
        assertEquals(3, asRoute.routes[0].points.size)
        val back = GpxEdit.routeToTrack(asRoute, 0)
        assertEquals(3, back.tracks[0].pointCount)
        assertEquals("T", back.tracks[0].name)
    }

    @Test
    fun douglasPeuckerDropsCollinearPoints() {
        val pts = (0..100).map { GpxPoint(45.0, 6.0 + it * 0.0001) } + GpxPoint(45.01, 6.01)
        val simplified = GpxEdit.douglasPeucker(pts, 2.0)
        assertEquals(3, simplified.size)
        assertEquals(pts.first(), simplified.first())
        assertEquals(pts.last(), simplified.last())
    }

    @Test
    fun fillsMissingElevations() {
        val d = GpxEdit.append(base, line, listOf(GpxPoint(0.0, 3.0)))
        val missing = GpxEdit.missingElevationPoints(d)
        assertEquals(listOf(0.0 to 3.0), missing)
        val filled = GpxEdit.withElevations(d, mapOf((0.0 to 3.0) to 42.0))
        assertEquals(42.0, GpxEdit.points(filled, line).last().ele!!, 0.0)
        assertEquals(1.0, GpxEdit.points(filled, line).first().ele!!, 0.0)
    }
}
