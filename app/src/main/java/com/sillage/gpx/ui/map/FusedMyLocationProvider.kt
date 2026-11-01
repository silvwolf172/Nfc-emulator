package com.sillage.gpx.ui.map

import android.content.Context
import android.location.Location
import android.os.Looper
import com.sillage.gpx.record.LocationSource
import org.osmdroid.views.overlay.mylocation.IMyLocationConsumer
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider

/** « Ma position » sur la carte via la localisation combinée de Google (ou le GPS à défaut). */
class FusedMyLocationProvider(context: Context) : IMyLocationProvider {
    private val source = LocationSource.create(context)
    private var last: Location? = null

    override fun startLocationProvider(consumer: IMyLocationConsumer): Boolean {
        source.start(1_500, Looper.getMainLooper()) { loc ->
            last = loc
            consumer.onLocationChanged(loc, this)
        }
        return true
    }

    override fun stopLocationProvider() = source.stop()

    override fun getLastKnownLocation(): Location? = last

    override fun destroy() = source.stop()
}
