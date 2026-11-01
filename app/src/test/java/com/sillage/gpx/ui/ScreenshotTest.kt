package com.sillage.gpx.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.sillage.gpx.ui.theme.SillageTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Captures d'écran de contrôle (Robolectric + Roborazzi), enregistrées dans app/build/screenshots.
 * Lancer : ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun vmWithSamples(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AppViewModel(app)
        runBlocking {
            vm.repo.list().forEach { vm.repo.delete(it.id) }
            val samples = File("src/main/assets/samples").listFiles().orEmpty().filter { it.extension == "gpx" }
            samples.forEach { vm.repo.importText(com.sillage.gpx.gpx.GpxParser.parse(it.readText()).displayName ?: it.nameWithoutExtension, it.readText()) }
        }
        vm.refresh()
        return vm
    }

    private fun shoot(name: String) {
        if (compose.mainClock.autoAdvance) compose.waitForIdle() else compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test
    fun library_light() {
        val vm = vmWithSamples()
        compose.mainClock.autoAdvance = false
        compose.setContent { SillageTheme(darkTheme = false, dynamicColor = false) { App(vm) } }
        compose.waitUntil(10_000) { compose.mainClock.advanceTimeBy(100); vm.entries.isNotEmpty() }
        shoot("library_light")
    }

    @Test
    fun library_dark() {
        val vm = vmWithSamples()
        compose.mainClock.autoAdvance = false
        compose.setContent { SillageTheme(darkTheme = true, dynamicColor = false) { App(vm) } }
        compose.waitUntil(10_000) { compose.mainClock.advanceTimeBy(100); vm.entries.isNotEmpty() }
        shoot("library_dark")
    }

    @Test
    fun library_empty() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AppViewModel(app)
        runBlocking { vm.repo.list().forEach { vm.repo.delete(it.id) } }
        vm.refresh()
        compose.mainClock.autoAdvance = false
        compose.setContent { SillageTheme(darkTheme = false, dynamicColor = false) { App(vm) } }
        compose.waitUntil(10_000) { compose.mainClock.advanceTimeBy(100); !vm.libraryLoading }
        shoot("library_empty")
    }

    private fun openHike(dark: Boolean): AppViewModel {
        val vm = vmWithSamples()
        val hikeId = runBlocking { vm.repo.list() }.first { it.name.contains("Chamonix") }.id
        val doc = runBlocking { vm.repo.load(hikeId) }
        compose.setContent { SillageTheme(darkTheme = dark, dynamicColor = false) { App(vm) } }
        compose.runOnUiThread { vm.showLoaded(hikeId, doc) }
        // Laisse le temps au cadrage et aux tuiles de carte d'arriver (si le réseau est disponible).
        repeat(60) { compose.mainClock.advanceTimeBy(200); compose.waitForIdle(); Thread.sleep(150) }
        return vm
    }

    @Test
    fun viewer_and_editor() {
        val vm = openHike(dark = false)
        shoot("viewer_summary")

        compose.onNodeWithText("Profil").performClick()
        repeat(6) { compose.waitForIdle(); Thread.sleep(50) }
        shoot("viewer_profile")

        compose.runOnUiThread { vm.selectedWaypoint = 2 }
        shoot("viewer_waypoint")

        compose.runOnUiThread { vm.selectedWaypoint = null; vm.startEditing() }
        repeat(10) { compose.waitForIdle(); Thread.sleep(50) }
        shoot("editor")
    }

    @Test
    fun viewer_dark() {
        openHike(dark = true)
        shoot("viewer_dark")
    }

    /** Simule un enregistrement en cours à partir de l'exemple de randonnée (sans GPS). */
    private fun fakeRecording(vm: AppViewModel) {
        val hikeId = runBlocking { vm.repo.list() }.first { it.name.contains("Chamonix") }.id
        val pts = runBlocking { vm.repo.load(hikeId) }.tracks[0].segments[0].points.take(420)
        val last = pts.last()
        val now = System.currentTimeMillis()
        com.sillage.gpx.record.RecordingState.update {
            com.sillage.gpx.record.RecordingSnapshot(
                status = com.sillage.gpx.record.RecordingStatus.RECORDING,
                name = "Parcours du jour",
                startTime = now - 5_400_000,
                segments = listOf(pts),
                distanceM = com.sillage.gpx.gpx.Geo.length(pts),
                accumulatedMs = 5_000_000,
                activeSince = now - 125_000,
                lastFix = com.sillage.gpx.record.Fix(last.lat, last.lon, 4f, 1.1f, last.ele, now),
                satellitesUsed = 14,
                satellitesVisible = 27,
            )
        }
    }

    @Test
    fun recording() {
        val vm = vmWithSamples()
        fakeRecording(vm)
        compose.setContent { SillageTheme(darkTheme = false, dynamicColor = false) { App(vm) } }
        compose.waitUntil(10_000) { vm.entries.isNotEmpty() }
        repeat(5) { compose.waitForIdle(); Thread.sleep(50) }
        shoot("library_recording_banner")
        compose.runOnUiThread { vm.openRecording() }
        repeat(30) { compose.mainClock.advanceTimeBy(200); compose.waitForIdle(); Thread.sleep(100) }
        shoot("recording")
        com.sillage.gpx.record.RecordingState.update { com.sillage.gpx.record.RecordingSnapshot() }
    }

    @Test
    fun offline_maps() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val mapFile = File("build/test-maps/monaco.map")
        org.junit.Assume.assumeTrue(mapFile.exists())
        mapFile.copyTo(File(com.sillage.gpx.offline.OfflineMaps.dir(app), "monaco.map"), overwrite = true)
        val vm = vmWithSamples()
        compose.setContent { SillageTheme(darkTheme = false, dynamicColor = false) { App(vm) } }
        compose.runOnUiThread { vm.openOfflineMaps(); vm.browse("europe/france/") }
        repeat(40) { compose.mainClock.advanceTimeBy(200); compose.waitForIdle(); Thread.sleep(100) }
        shoot("offline_maps")

        // Une balade dans Monaco, affichée avec la carte hors ligne.
        val doc = com.sillage.gpx.gpx.GpxDocument(
            metadata = com.sillage.gpx.gpx.GpxMetadata(name = "Tour du port de Monaco"),
            tracks = listOf(com.sillage.gpx.gpx.GpxTrack(name = "Port", segments = listOf(com.sillage.gpx.gpx.GpxSegment(listOf(
                com.sillage.gpx.gpx.GpxPoint(43.7365, 7.4180), com.sillage.gpx.gpx.GpxPoint(43.7347, 7.4213),
                com.sillage.gpx.gpx.GpxPoint(43.7357, 7.4250), com.sillage.gpx.gpx.GpxPoint(43.7385, 7.4262),
                com.sillage.gpx.gpx.GpxPoint(43.7400, 7.4285), com.sillage.gpx.gpx.GpxPoint(43.7420, 7.4268),
            ))))),
        )
        compose.runOnUiThread {
            vm.changeMapStyle(com.sillage.gpx.ui.map.MapStyle.OFFLINE)
            vm.showLoaded("monaco-test.gpx", doc)
        }
        // Le rendu vectoriel se fait en tâche de fond : on lui laisse le temps.
        repeat(120) { compose.mainClock.advanceTimeBy(100); compose.waitForIdle(); Thread.sleep(100) }
        shoot("viewer_offline")
        compose.runOnUiThread { vm.changeMapStyle(com.sillage.gpx.ui.map.MapStyle.AUTO) }
    }

    @androidx.compose.runtime.Composable
    private fun App(vm: AppViewModel) {
        SillageApp(
            vm = vm,
            onImport = {},
            onExport = {},
            onShare = { _, _ -> },
            onRecord = {},
            hasLocationPermission = { false },
            requestLocation = {},
        )
    }
}
