package com.sillage.gpx.record

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Source de positions. On privilégie le « Fused Location Provider » de Google Play Services (GPS + Wi-Fi +
 * réseau mobile + capteurs : premier point plus rapide, positions plus stables). Sans Play Services
 * (téléphones « dé-Googlisés »), on se rabat sur le GPS du système.
 */
interface LocationSource {
    fun start(intervalMs: Long, looper: Looper, listener: (Location) -> Unit)
    fun stop()

    companion object {
        fun create(context: Context): LocationSource {
            val ok = runCatching {
                GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
            }.getOrDefault(false)
            return if (ok) FusedSource(context.applicationContext) else PlatformSource(context.applicationContext)
        }
    }
}

private class FusedSource(context: Context) : LocationSource {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    override fun start(intervalMs: Long, looper: Looper, listener: (Location) -> Unit) {
        stop()
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setWaitForAccurateLocation(false)
            .build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach(listener)
            }
        }
        callback = cb
        try {
            client.requestLocationUpdates(request, cb, looper)
        } catch (_: SecurityException) {
            callback = null
        }
    }

    override fun stop() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
    }
}

private class PlatformSource(context: Context) : LocationSource {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var locationListener: LocationListener? = null

    @SuppressLint("MissingPermission")
    override fun start(intervalMs: Long, looper: Looper, listener: (Location) -> Unit) {
        stop()
        val l = LocationListener { listener(it) }
        locationListener = l
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (manager.isProviderEnabled(provider)) manager.requestLocationUpdates(provider, intervalMs, 0f, l, looper)
            } catch (_: SecurityException) {
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    override fun stop() {
        locationListener?.let { manager.removeUpdates(it) }
        locationListener = null
    }
}
