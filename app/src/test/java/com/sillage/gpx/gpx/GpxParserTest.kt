package com.sillage.gpx.gpx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GpxParserTest {

    private val gpx11 = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="Garmin Connect" xmlns="http://www.topografix.com/GPX/1/1"
             xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">
          <metadata>
            <name>Sortie du matin</name>
            <desc>Boucle &amp; café</desc>
            <author><name>Camille</name></author>
            <time>2024-05-01T06:00:00Z</time>
          </metadata>
          <wpt lat="45.5" lon="6.1"><ele>1200</ele><name>Col</name><sym>Summit</sym></wpt>
          <rte><name>Plan</name><rtept lat="45.0" lon="6.0"/><rtept lat="45.1" lon="6.1"/></rte>
          <trk>
            <name>Piste</name>
            <type>running</type>
            <trkseg>
              <trkpt lat="45.0000" lon="6.0000"><ele>100</ele><time>2024-05-01T06:00:00Z</time>
                <extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>120</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions>
              </trkpt>
              <trkpt lat="45.0010" lon="6.0000"><ele>110</ele><time>2024-05-01T06:00:30Z</time></trkpt>
              <trkpt lat="45.0020" lon="6.0000"><ele>105.5</ele><time>2024-05-01T06:01:00.500+00:00</time></trkpt>
            </trkseg>
            <trkseg>
              <trkpt lat="45.01" lon="6.01"/>
            </trkseg>
          </trk>
        </gpx>
    """.trimIndent()

    @Test
    fun parsesGpx11() {
        val doc = GpxParser.parse(gpx11)
        assertEquals("Sortie du matin", doc.metadata.name)
        assertEquals("Boucle & café", doc.metadata.desc)
        assertEquals("Camille", doc.metadata.author)
        assertEquals(GpxTime.parse("2024-05-01T06:00:00Z"), doc.metadata.time)
        assertEquals("Garmin Connect", doc.creator)

        assertEquals(1, doc.waypoints.size)
        assertEquals("Col", doc.waypoints[0].name)
        assertEquals("Summit", doc.waypoints[0].sym)
        assertEquals(1200.0, doc.waypoints[0].ele!!, 0.0)

        assertEquals(1, doc.routes.size)
        assertEquals(2, doc.routes[0].points.size)

        assertEquals(1, doc.tracks.size)
        val trk = doc.tracks[0]
        assertEquals("Piste", trk.name)
        assertEquals("running", trk.type)
        assertEquals(2, trk.segments.size)
        assertEquals(3, trk.segments[0].points.size)
        assertEquals(105.5, trk.segments[0].points[2].ele!!, 1e-9)
        assertNotNull(trk.segments[0].points[2].time)
        // Le nom d'un trkpt ne doit pas écraser celui de la trace, et les extensions sont ignorées.
        assertNull(trk.segments[0].points[0].name)
    }

    @Test
    fun parsesGpx10WithRootMetadata() {
        val doc = GpxParser.parse(
            """
            <gpx version="1.0" creator="old" xmlns="http://www.topografix.com/GPX/1/0">
              <name>Ancien</name>
              <time>2010-01-01T00:00:00Z</time>
              <trk><trkseg><trkpt lat="1" lon="2"><ele>3</ele></trkpt></trkseg></trk>
            </gpx>
            """.trimIndent(),
        )
        assertEquals("Ancien", doc.metadata.name)
        assertEquals(1, doc.tracks[0].pointCount)
    }

    @Test
    fun parsesWithoutNamespace() {
        val doc = GpxParser.parse("""<gpx><trk><name>X</name><trkseg><trkpt lat="1" lon="2"/></trkseg></trk></gpx>""")
        assertEquals("X", doc.tracks[0].name)
        assertEquals(1, doc.tracks[0].pointCount)
    }

    @Test
    fun skipsInvalidCoordinates() {
        val doc = GpxParser.parse("""<gpx><wpt lat="abc" lon="2"/><wpt lat="95" lon="2"/><wpt lat="1" lon="2"/></gpx>""")
        assertEquals(1, doc.waypoints.size)
    }

    @Test
    fun rejectsNonGpx() {
        try {
            GpxParser.parse("<kml><Document/></kml>")
            fail("expected exception")
        } catch (e: GpxParseException) {
            assertTrue(e.message!!.contains("GPX"))
        }
        try {
            GpxParser.parse("pas du xml")
            fail("expected exception")
        } catch (_: GpxParseException) {
        }
    }

    @Test
    fun roundTripsThroughWriter() {
        val doc = GpxParser.parse(gpx11)
        val text = GpxWriter.toString(doc)
        val again = GpxParser.parse(text)
        assertEquals(doc.metadata, again.metadata)
        assertEquals(doc.waypoints, again.waypoints)
        assertEquals(doc.routes, again.routes)
        assertEquals(doc.tracks.map { it.name }, again.tracks.map { it.name })
        assertEquals(doc.tracks[0].segments[0].points.map { it.lat to it.lon }, again.tracks[0].segments[0].points.map { it.lat to it.lon })
        assertEquals(doc.tracks[0].segments[0].points.map { it.time }, again.tracks[0].segments[0].points.map { it.time })
        assertTrue(text.contains("&amp; café"))
        assertTrue(text.contains("version=\"1.1\""))
    }

    @Test
    fun writerEscapesSpecialCharacters() {
        val doc = GpxDocument(waypoints = listOf(GpxPoint(1.0, 2.0, name = "<A & \"B\">")))
        val again = GpxParser.parse(GpxWriter.toString(doc))
        assertEquals("<A & \"B\">", again.waypoints[0].name)
    }
}
