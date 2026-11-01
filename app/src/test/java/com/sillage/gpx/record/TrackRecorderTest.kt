package com.sillage.gpx.record

import com.sillage.gpx.gpx.GpxPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrackRecorderTest {
    // 0,0001° de latitude ≈ 11,1 m
    private val step = 0.0001

    @Test
    fun ignoresInaccurateFixes() {
        val r = TrackRecorder()
        assertNull(r.offer(45.0, 6.0, null, 1_000, accuracyM = 80f))
        assertNotNull(r.offer(45.0, 6.0, null, 2_000, accuracyM = 6f))
        assertEquals(1, r.pointCount)
    }

    @Test
    fun skipsJitterWhileStandingStill() {
        val r = TrackRecorder()
        r.offer(45.0, 6.0, null, 0, 5f)
        // 2 m de dérive toutes les 2 s pendant 20 s : rien d'ajouté.
        for (i in 1..10) assertNull(r.offer(45.0 + 0.00002 * (i % 2), 6.0, null, i * 2_000L, 5f))
        // Au-delà de 30 s, un point est ajouté pour garder la continuité temporelle.
        assertNotNull(r.offer(45.00001, 6.0, null, 31_000, 5f))
        assertEquals(2, r.pointCount)
        assertEquals(1.1, r.distanceM, 0.5)
    }

    @Test
    fun recordsWalkAndAccumulatesDistance() {
        val r = TrackRecorder()
        for (i in 0..10) r.offer(45.0 + i * step, 6.0, 1000.0 + i, i * 8_000L, 4f)
        assertEquals(11, r.pointCount)
        assertEquals(111.2, r.distanceM, 1.0)
    }

    @Test
    fun rejectsJumpsUnlessConfirmed() {
        val r = TrackRecorder()
        r.offer(45.0, 6.0, null, 0, 5f)
        // Saut de ~11 km en 2 s : rejeté…
        assertNull(r.offer(45.1, 6.0, null, 2_000, 5f))
        assertNull(r.offer(45.1, 6.0, null, 4_000, 5f))
        assertNull(r.offer(45.1, 6.0, null, 6_000, 5f))
        assertNull(r.offer(45.1, 6.0, null, 8_000, 5f))
        // …mais s'il se confirme (ex. trajet en train pendant une pause oubliée), on finit par l'accepter.
        assertNotNull(r.offer(45.1, 6.0, null, 10_000, 5f))
    }

    @Test
    fun pauseStartsNewSegment() {
        val r = TrackRecorder()
        r.offer(45.0, 6.0, null, 0, 5f)
        r.offer(45.0 + step, 6.0, null, 5_000, 5f)
        r.newSegment()
        r.newSegment() // une section vide n'est pas dupliquée
        r.offer(45.0 + 5 * step, 6.0, null, 60_000, 5f)
        assertEquals(listOf(2, 1), r.segments.map { it.size })
        // La distance ne compte pas le trajet effectué pendant la pause.
        assertEquals(11.1, r.distanceM, 0.5)
    }
}

class RecordingJournalTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun roundTripsSegmentsAndPoints() {
        val file = File(tmp.root, "rec/current.journal")
        val j = RecordingJournal(file)
        j.begin("Balade | test", 1234L)
        j.appendSegment()
        j.appendPoint(GpxPoint(45.1234567, 6.7654321, 1001.5, 10_000))
        j.appendPoint(GpxPoint(45.2, 6.8, null, 12_000))
        j.appendSegment()
        j.appendPoint(GpxPoint(45.3, 6.9, 999.0, 20_000))
        j.close()

        val c = RecordingJournal(file).read()!!
        assertEquals("Balade   test", c.name)
        assertEquals(1234L, c.startTime)
        assertEquals(2, c.segments.size)
        assertEquals(GpxPoint(45.1234567, 6.7654321, 1001.5, 10_000), c.segments[0][0])
        assertNull(c.segments[0][1].ele)
        assertEquals(20_000L, c.segments[1][0].time)
    }

    @Test
    fun appendsAfterReopening() {
        val file = File(tmp.root, "j.journal")
        RecordingJournal(file).apply { begin("A", 1); appendSegment(); appendPoint(GpxPoint(1.0, 2.0, null, 1)); close() }
        RecordingJournal(file).apply { appendSegment(); appendPoint(GpxPoint(1.5, 2.5, null, 2)); close() }
        val c = RecordingJournal(file).read()!!
        assertEquals(listOf(1, 1), c.segments.map { it.size })
    }
}
