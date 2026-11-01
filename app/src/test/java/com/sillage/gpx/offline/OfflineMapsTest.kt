package com.sillage.gpx.offline

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.mapsforge.MapsForgeTileSource
import org.osmdroid.util.MapTileIndex
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.net.URL

class CatalogParserTest {
    private val html = """
        <tr><td valign="top"><img src="/icons/back.gif" alt="[PARENTDIR]"></td><td><a href="/maps/v5/europe/">Parent Directory</a>       </td><td>&nbsp;</td><td align="right">  - </td><td>&nbsp;</td></tr>
        <tr><td valign="top"><img src="/icons/unknown.gif" alt="[   ]"></td><td><a href="alsace.map">alsace.map</a>             </td><td align="right">2026-10-19 19:20  </td><td align="right"> 86M</td><td>&nbsp;</td></tr>
        <tr><td valign="top"><img src="/icons/unknown.gif" alt="[   ]"></td><td><a href="languedoc-roussillon.map">languedoc-roussillon..&gt;</a></td><td align="right">2026-10-20 14:01  </td><td align="right">1.2G</td><td>&nbsp;</td></tr>
        <tr><td valign="top"><img src="/icons/folder.gif" alt="[DIR]"></td><td><a href="france/">france/</a>                </td><td align="right">2026-10-21 09:31  </td><td align="right">  - </td><td>&nbsp;</td></tr>
        <tr><td valign="top"><img src="/icons/text.gif" alt="[TXT]"></td><td><a href="README.txt">README.txt</a>             </td><td align="right">2026-10-21 09:31  </td><td align="right">1.0K</td><td>&nbsp;</td></tr>
    """.trimIndent()

    @Test
    fun parsesFoldersAndMaps() {
        val entries = OfflineMaps.parseListing(html, "europe/")
        assertEquals(listOf("france/", "alsace.map", "languedoc-roussillon.map"), entries.map { it.name })
        assertTrue(entries[0].isFolder)
        assertEquals("europe/france/", entries[0].path)
        assertEquals("86 Mo", entries[1].size)
        assertEquals("1,2 Go", entries[2].size)
        assertEquals("Languedoc Roussillon", entries[2].label)
    }
}

/** Rendu réel d'une petite carte mapsforge (Monaco, 350 ko), téléchargée une fois dans build/. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class OfflineRenderTest {
    @Test
    fun rendersVectorTileWithoutNetwork() {
        val file = File("build/test-maps/monaco.map")
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            runCatching { URL(OfflineMaps.BASE_URL + "europe/monaco.map").openStream().use { i -> file.outputStream().use { i.copyTo(it) } } }
        }
        assumeTrue("carte de test indisponible (pas de réseau)", file.exists() && file.length() > 10_000)

        MapsForgeTileSource.createInstance(ApplicationProvider.getApplicationContext<Application>())
        val source = MapsForgeTileSource.createFromFiles(arrayOf(file))
        val bounds = source.boundsOsmdroid
        assertTrue("l'emprise $bounds contient Monaco", bounds.contains(43.7384, 7.4246))

        // Tuile z15 au centre de Monaco.
        val z = 15
        val n = 1 shl z
        val x = ((7.4246 + 180) / 360 * n).toInt()
        val latRad = Math.toRadians(43.7384)
        val y = ((1 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) / 2 * n).toInt()
        val drawable = source.renderTile(MapTileIndex.getTileIndex(z, x, y))
        assertTrue("tuile rendue", drawable != null)
        val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        drawable!!.setBounds(0, 0, 256, 256)
        drawable.draw(Canvas(bmp))
        val colors = HashSet<Int>()
        for (py in 0 until 256 step 4) for (px in 0 until 256 step 4) colors.add(bmp.getPixel(px, py))
        File("build/screenshots").mkdirs()
        File("build/screenshots/offline_tile.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("la tuile contient un vrai dessin (${colors.size} couleurs)", colors.size > 10)
        source.dispose()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class OfflineProviderTest {
    @Test
    fun providerRendersIntoMapView() {
        val file = File("build/test-maps/monaco.map")
        assumeTrue(file.exists())
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        com.sillage.gpx.ui.map.MapConfig.init(ctx)
        val provider = runCatching {
            MapsForgeTileSource.createInstance(ctx)
            val source = MapsForgeTileSource.createFromFiles(arrayOf(file))
            org.osmdroid.mapsforge.MapsForgeTileProvider(org.osmdroid.tileprovider.util.SimpleRegisterReceiver(ctx), source, null)
        }
        provider.exceptionOrNull()?.printStackTrace()
        assertTrue("provider: ${provider.exceptionOrNull()}", provider.isSuccess)
        val map = org.osmdroid.views.MapView(ctx)
        map.setTileProvider(com.sillage.gpx.ui.map.OfflineRenderer.provider(ctx, listOf(file))!!)
        map.measure(android.view.View.MeasureSpec.makeMeasureSpec(512, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(512, android.view.View.MeasureSpec.EXACTLY))
        map.layout(0, 0, 512, 512)
        map.controller.setZoom(16.0)
        map.controller.setCenter(org.osmdroid.util.GeoPoint(43.7384, 7.4246))
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        var colors = 0
        for (i in 0 until 40) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            bmp.eraseColor(0)
            map.draw(Canvas(bmp))
            val set = HashSet<Int>()
            for (py in 0 until 512 step 8) for (px in 0 until 512 step 8) set.add(bmp.getPixel(px, py))
            colors = set.size
            if (colors > 40) break
            Thread.sleep(250)
        }
        File("build/screenshots").mkdirs()
        File("build/screenshots/offline_mapview.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("MAPVIEW colors=$colors tileSource=${map.tileProvider.tileSource.name()}")
        assertTrue("la carte hors ligne s'affiche dans la MapView ($colors couleurs)", colors > 40)
    }
}
