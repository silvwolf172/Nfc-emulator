package com.sillage.gpx.record

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sillage.gpx.MainActivity
import com.sillage.gpx.R
import com.sillage.gpx.data.GpxRepository
import com.sillage.gpx.data.OnlineServices
import com.sillage.gpx.gpx.GpxDocument
import com.sillage.gpx.gpx.GpxEdit
import com.sillage.gpx.gpx.GpxMetadata
import com.sillage.gpx.gpx.GpxPoint
import com.sillage.gpx.gpx.GpxSegment
import com.sillage.gpx.gpx.GpxTrack
import com.sillage.gpx.util.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Enregistre un parcours en service de premier plan : continue écran éteint, survit à la fermeture de
 * l'interface et reprend depuis son journal si le système le redémarre.
 */
class RecordingService : Service() {

    companion object {
        const val ACTION_START = "com.sillage.gpx.record.START"
        const val ACTION_PAUSE = "com.sillage.gpx.record.PAUSE"
        const val ACTION_RESUME = "com.sillage.gpx.record.RESUME"
        const val ACTION_STOP = "com.sillage.gpx.record.STOP"
        const val ACTION_DISCARD = "com.sillage.gpx.record.DISCARD"
        const val EXTRA_OPEN_RECORDING = "open_recording"

        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 42
        private const val INTERVAL_MS = 2_000L

        fun journalFile(context: Context) = File(context.filesDir, "recording/current.journal")

        fun send(context: Context, action: String) {
            val intent = Intent(context, RecordingService::class.java).setAction(action)
            if (action == ACTION_START) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
        }

        /** Sauve dans la bibliothèque un enregistrement interrompu (service arrêté par le système). */
        suspend fun recoverInterrupted(context: Context): String? {
            if (RecordingState.state.value.isActive) return null
            val journal = RecordingJournal(journalFile(context))
            val content = journal.read() ?: return null
            if (content.segments.sumOf { it.size } < 2) { journal.delete(); return null }
            val doc = buildDocument(content.name + " (récupéré)", content.startTime, content.segments)
            val repo = GpxRepository(context)
            val id = repo.create(doc.metadata.name ?: "Parcours")
            repo.save(id, doc)
            journal.delete()
            return id
        }

        internal fun buildDocument(name: String, start: Long, segments: List<List<GpxPoint>>) = GpxDocument(
            metadata = GpxMetadata(name = name, time = start),
            tracks = listOf(GpxTrack(name = name, type = "Enregistrement", segments = segments.map { GpxSegment(it) })),
            creator = "Sillage",
        )
    }

    private val thread = HandlerThread("sillage-recording").apply { start() }
    private val handler = Handler(thread.looper)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var source: LocationSource
    private lateinit var journal: RecordingJournal
    private val recorder = TrackRecorder()
    private var started = false
    private var paused = false
    private var lastNotification = 0L
    private var gnssCallback: GnssStatus.Callback? = null
    private var altitudeConverter: Any? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        source = LocationSource.create(this)
        journal = RecordingJournal(journalFile(this))
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> handler.post { pause() }
            ACTION_RESUME -> handler.post { resume() }
            ACTION_STOP -> handler.post { finish(save = true) }
            ACTION_DISCARD -> handler.post { finish(save = false) }
            // ACTION_START : nouveau parcours. Intent null : redémarrage par le système, on reprend le journal.
            else -> if (!started) begin(restore = intent == null)
        }
        return START_STICKY
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun begin(restore: Boolean) {
        if (!hasLocationPermission()) { stopSelf(); return }
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(RecordingState.state.value),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        } catch (e: Exception) {
            // Démarrage refusé (ex. redémarrage en arrière-plan) : le journal sera récupéré à la prochaine ouverture.
            stopSelf()
            return
        }
        started = true
        handler.post {
            val restored = if (restore) journal.read() else null
            val now = System.currentTimeMillis()
            val name: String
            val start: Long
            if (restored != null && restored.segments.isNotEmpty()) {
                recorder.restore(restored.segments)
                recorder.newSegment()
                journal.appendSegment()
                name = restored.name
                start = restored.startTime
            } else {
                name = "Parcours du " + Format.dateTime(now)
                start = now
                journal.begin(name, start)
                journal.appendSegment()
            }
            RecordingState.update {
                RecordingSnapshot(
                    status = RecordingStatus.RECORDING,
                    name = name,
                    startTime = start,
                    segments = recorder.segments,
                    distanceM = recorder.distanceM,
                    activeSince = now,
                )
            }
            source.start(INTERVAL_MS, thread.looper, ::onLocation)
            registerGnss()
        }
    }

    private fun onLocation(location: Location) {
        val ele = mslAltitude(location)
        val fix = Fix(
            lat = location.latitude,
            lon = location.longitude,
            accuracyM = if (location.hasAccuracy()) location.accuracy else null,
            speedMs = if (location.hasSpeed()) location.speed else null,
            altitudeM = ele,
            time = location.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
        val point = if (paused) null else recorder.offer(fix.lat, fix.lon, ele, fix.time, fix.accuracyM)
        point?.let(journal::appendPoint)
        RecordingState.update {
            it.copy(
                lastFix = fix,
                segments = if (point != null) recorder.segments else it.segments,
                distanceM = recorder.distanceM,
            )
        }
        val now = System.currentTimeMillis()
        if (now - lastNotification > 5_000) {
            lastNotification = now
            notifyState()
        }
    }

    /**
     * Altitude au-dessus du niveau de la mer. Le GPS donne une hauteur sur l'ellipsoïde (≈ +50 m en France) ;
     * Android 14 sait la convertir. Avant, on laisse vide et les altitudes sont complétées à l'enregistrement.
     */
    @SuppressLint("NewApi")
    private fun mslAltitude(location: Location): Double? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || !location.hasAltitude()) return null
        return try {
            val converter = (altitudeConverter as? android.location.altitude.AltitudeConverter)
                ?: android.location.altitude.AltitudeConverter().also { altitudeConverter = it }
            converter.addMslAltitudeToLocation(this, location)
            if (location.hasMslAltitude()) location.mslAltitudeMeters else null
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerGnss() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val cb = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var used = 0
                for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
                RecordingState.update { it.copy(satellitesUsed = used, satellitesVisible = status.satelliteCount) }
            }
        }
        runCatching { lm.registerGnssStatusCallback(cb, handler) }.onSuccess { gnssCallback = cb }
    }

    private fun pause() {
        if (paused || !started) return
        paused = true
        val now = System.currentTimeMillis()
        RecordingState.update {
            it.copy(status = RecordingStatus.PAUSED, accumulatedMs = it.elapsedMs(now), activeSince = null)
        }
        notifyState()
    }

    private fun resume() {
        if (!paused) return
        paused = false
        recorder.newSegment()
        journal.appendSegment()
        RecordingState.update { it.copy(status = RecordingStatus.RECORDING, activeSince = System.currentTimeMillis()) }
        notifyState()
    }

    private fun finish(save: Boolean) {
        if (!started) { stopSelf(); return }
        source.stop()
        unregisterGnss()
        val snapshot = RecordingState.state.value
        val segments = recorder.segments.filter { it.isNotEmpty() }
        RecordingState.update { it.copy(status = RecordingStatus.SAVING, activeSince = null) }
        scope.launch {
            if (save && segments.sumOf { it.size } >= 2) {
                var doc = buildDocument(snapshot.name, snapshot.startTime ?: System.currentTimeMillis(), segments)
                // Sans altitude GPS fiable (Android < 14), on complète avec le modèle de terrain si le réseau le permet.
                val missing = GpxEdit.missingElevationPoints(doc)
                if (missing.isNotEmpty() && missing.size <= 20_000) {
                    withTimeoutOrNull(30_000) { runCatching { OnlineServices.elevations(missing) }.getOrNull() }
                        ?.let { doc = GpxEdit.withElevations(doc, it) }
                }
                runCatching {
                    val repo = GpxRepository(this@RecordingService)
                    val id = repo.create(snapshot.name)
                    repo.save(id, doc)
                    id
                }.onSuccess { id ->
                    journal.delete()
                    RecordingState.emit(RecordingEvent.Saved(id))
                }.onFailure {
                    RecordingState.emit(RecordingEvent.Message("Enregistrement impossible : ${it.message}"))
                }
            } else {
                journal.delete()
                RecordingState.emit(RecordingEvent.Message(if (save) "Parcours trop court, rien n'a été enregistré" else "Parcours supprimé"))
            }
            RecordingState.update { RecordingSnapshot() }
            started = false
            ServiceCompat.stopForeground(this@RecordingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun unregisterGnss() {
        val cb = gnssCallback ?: return
        (getSystemService(Context.LOCATION_SERVICE) as LocationManager).unregisterGnssStatusCallback(cb)
        gnssCallback = null
    }

    override fun onDestroy() {
        source.stop()
        unregisterGnss()
        journal.close()
        if (RecordingState.state.value.status != RecordingStatus.IDLE && RecordingState.state.value.status != RecordingStatus.SAVING) {
            // Arrêt inattendu : l'état est remis à zéro, le journal sera récupéré.
            RecordingState.update { RecordingSnapshot() }
        }
        scope.cancel()
        thread.quitSafely()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- Notification

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, "Enregistrement de parcours", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Affiche le parcours en cours d'enregistrement"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun notifyState() {
        val nm = getSystemService(NotificationManager::class.java)
        runCatching { nm.notify(NOTIFICATION_ID, buildNotification(RecordingState.state.value)) }
    }

    private fun buildNotification(s: RecordingSnapshot): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra(EXTRA_OPEN_RECORDING, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(action: String, code: Int) = PendingIntent.getService(
            this, code, Intent(this, RecordingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val paused = s.status == RecordingStatus.PAUSED
        val now = System.currentTimeMillis()
        val text = "${Format.distance(s.distanceM)} · ${Format.duration(s.elapsedMs(now))} · ${SignalQuality.of(s.lastFix, now).label}"
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentTitle(if (paused) "Enregistrement en pause" else "Enregistrement en cours")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, if (paused) "Reprendre" else "Pause", action(if (paused) ACTION_RESUME else ACTION_PAUSE, 1))
            .addAction(0, "Terminer", action(ACTION_STOP, 2))
            .build()
    }
}
