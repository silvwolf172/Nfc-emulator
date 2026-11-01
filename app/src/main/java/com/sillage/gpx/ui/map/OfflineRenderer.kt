package com.sillage.gpx.ui.map

import android.app.Application
import android.content.Context
import org.osmdroid.mapsforge.MapsForgeTileProvider
import org.osmdroid.mapsforge.MapsForgeTileSource
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import java.io.File

/** Rendu sur le téléphone des cartes vectorielles mapsforge (aucun réseau nécessaire). */
object OfflineRenderer {
    private var initialized = false

    fun provider(context: Context, files: List<File>): MapsForgeTileProvider? = runCatching {
        if (!initialized) {
            MapsForgeTileSource.createInstance(context.applicationContext as Application)
            initialized = true
        }
        val source = MapsForgeTileSource.createFromFiles(files.toTypedArray())
        MapsForgeTileProvider(SimpleRegisterReceiver(context.applicationContext), source, null)
    }.getOrNull()
}
