package com.sillage.gpx.ui.map

import android.app.Application
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.sillage.gpx.gpx.GeoBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xxhdpi")
class MapFitTest {

    @Test
    fun fitKeepsBoundsInsideVisibleArea() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        MapConfig.init(ctx)
        val map = MapView(ctx).apply { isTilesScaledToDpi = true }
        map.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY))
        map.layout(0, 0, 1080, 2400)
        val controller = MapController()
        controller.attach(map)
        val bounds = GeoBounds(45.938, 6.865, 45.973, 6.892)
        val insets = MapInsets(left = 60, top = 300, right = 60, bottom = 1000)
        controller.fit(bounds, insets, animate = false)

        val p = map.projection
        val topLeft = p.toPixels(GeoPoint(bounds.maxLat, bounds.minLon), null)
        val bottomRight = p.toPixels(GeoPoint(bounds.minLat, bounds.maxLon), null)
        println("FIT zoom=${map.zoomLevelDouble} tl=$topLeft br=$bottomRight")
        assertTrue("haut ${topLeft.y}", topLeft.y >= insets.top)
        assertTrue("bas ${bottomRight.y}", bottomRight.y <= 2400 - insets.bottom)
        assertTrue("gauche ${topLeft.x}", topLeft.x >= insets.left)
        assertTrue("droite ${bottomRight.x}", bottomRight.x <= 1080 - insets.right)
        // Centré dans la zone visible.
        val visibleCenterY = (insets.top + 2400 - insets.bottom) / 2.0
        assertEquals(visibleCenterY, (topLeft.y + bottomRight.y) / 2.0, 6.0)
    }
}
