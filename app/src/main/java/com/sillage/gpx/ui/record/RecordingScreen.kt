package com.sillage.gpx.ui.record

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Polyline
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sillage.gpx.gpx.GpxDocument
import com.sillage.gpx.gpx.GpxSegment
import com.sillage.gpx.gpx.GpxStats
import com.sillage.gpx.gpx.GpxTrack
import com.sillage.gpx.record.RecordingSnapshot
import com.sillage.gpx.record.RecordingState
import com.sillage.gpx.record.RecordingStatus
import com.sillage.gpx.record.SignalQuality
import com.sillage.gpx.ui.AppViewModel
import com.sillage.gpx.ui.glass.GlassDefaults
import com.sillage.gpx.ui.glass.GlassIconButton
import com.sillage.gpx.ui.glass.GlassPillButton
import com.sillage.gpx.ui.glass.GlassSurface
import com.sillage.gpx.ui.glass.LocalGlassBackdrop
import com.sillage.gpx.ui.glass.glassSource
import com.sillage.gpx.ui.glass.rememberGlassBackdrop
import com.sillage.gpx.ui.map.GpxMap
import com.sillage.gpx.ui.map.GpxOverlay
import com.sillage.gpx.ui.map.MapController
import com.sillage.gpx.ui.map.followMyLocation
import com.sillage.gpx.ui.theme.NumberStyle
import com.sillage.gpx.util.Format
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun RecordingScreen(vm: AppViewModel) {
    val rec by RecordingState.state.collectAsStateWithLifecycle()
    val backdrop = rememberGlassBackdrop()
    val map = remember { MapController() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(1_000) }
    }
    val doc = remember(rec.segments) {
        GpxDocument(tracks = listOf(GpxTrack(segments = rec.segments.map { GpxSegment(it) }.ifEmpty { listOf(GpxSegment()) })))
    }
    val stats = remember(doc) { GpxStats.of(doc) }
    var confirmStop by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var topHeight by remember { mutableIntStateOf(0) }
    var panelHeight by remember { mutableIntStateOf(0) }
    // Garde la position courante au centre de la zone visible, au-dessus du panneau.
    LaunchedEffect(topHeight, panelHeight) { map.setCenterOffset(0, (topHeight - panelHeight) / 2) }

    BackHandler { vm.leaveRecording() }
    LaunchedEffect(Unit) {
        delay(300)
        rec.lastFix?.let { map.centerOn(it.lat, it.lon, 16.5) }
        map.followMyLocation(zoom = 16.0)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        GpxMap(
            doc = doc,
            controller = map,
            style = vm.mapStyle,
            offlineFiles = vm.offlineFiles,
            modifier = Modifier.fillMaxSize().glassSource(backdrop),
            showMyLocation = true,
            followLocation = true,
            listener = remember { object : GpxOverlay.Listener {} },
        )

        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .onSizeChanged { topHeight = it.height },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Retour (l'enregistrement continue)", onClick = vm::leaveRecording, size = 52.dp)
                GlassSurface(
                    modifier = Modifier.weight(1f).height(52.dp),
                    cornerRadius = 26.dp,
                    interactive = false,
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        RecordingDot(rec.status)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                when (rec.status) {
                                    RecordingStatus.RECORDING -> "Enregistrement"
                                    RecordingStatus.PAUSED -> "En pause"
                                    RecordingStatus.SAVING -> "Enregistrement du fichier…"
                                    RecordingStatus.IDLE -> "Démarrage…"
                                },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(signalText(rec, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            GlassIconButton(
                Icons.Rounded.MyLocation, "Suivre ma position",
                onClick = { map.followMyLocation(zoom = 16.0) },
                modifier = Modifier.align(Alignment.TopEnd).offset { IntOffset(0, topHeight) }.statusBarsPadding().padding(end = 12.dp, top = 8.dp),
            )

            GlassSurface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(12.dp)
                    .onSizeChanged { panelHeight = it.height },
                cornerRadius = 34.dp,
                style = GlassDefaults.panel(),
                interactive = false,
            ) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SignalBars(SignalQuality.of(rec.lastFix, now))
                        Spacer(Modifier.width(8.dp))
                        Text(SignalQuality.of(rec.lastFix, now).label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(chrono(rec.elapsedMs(now)), style = MaterialTheme.typography.displayMedium.merge(NumberStyle))
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecStat(Icons.Rounded.Straighten, "Distance", Format.distance(rec.distanceM), Modifier.weight(1f))
                        RecStat(Icons.Rounded.Speed, "Vitesse", rec.lastFix?.speedMs?.let { Format.speed(it.toDouble()) } ?: "—", Modifier.weight(1f))
                        RecStat(Icons.Rounded.Landscape, "Altitude", rec.lastFix?.altitudeM?.let { Format.elevation(it) } ?: "—", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecStat(Icons.AutoMirrored.Rounded.TrendingUp, "Dénivelé +", if (stats.maxEle != null) Format.elevation(stats.ascentM) else "—", Modifier.weight(1f))
                        val elapsed = rec.elapsedMs(now)
                        RecStat(Icons.Rounded.Timer, "Moyenne", if (elapsed > 60_000) Format.speed(rec.distanceM / (elapsed / 1000.0)) else "—", Modifier.weight(1f))
                        RecStat(Icons.Rounded.Polyline, "Points", rec.pointCount.toString(), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val paused = rec.status == RecordingStatus.PAUSED
                        GlassPillButton(
                            if (paused) "Reprendre" else "Pause",
                            onClick = { if (paused) vm.resumeRecording() else vm.pauseRecording() },
                            modifier = Modifier.weight(1f),
                            icon = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            height = 56.dp,
                            enabled = rec.status == RecordingStatus.RECORDING || paused,
                        )
                        GlassPillButton(
                            "Terminer",
                            onClick = { confirmStop = true },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Flag,
                            height = 56.dp,
                            style = GlassDefaults.tinted(MaterialTheme.colorScheme.error, 0.75f),
                            contentColor = MaterialTheme.colorScheme.onError,
                            enabled = rec.status == RecordingStatus.RECORDING || paused,
                        )
                    }
                }
            }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            icon = { Icon(Icons.Rounded.Flag, null) },
            title = { Text("Terminer le parcours ?") },
            text = { Text("${Format.distance(rec.distanceM)} en ${Format.duration(rec.elapsedMs(now))}. Le parcours sera ajouté à votre bibliothèque.") },
            confirmButton = { TextButton(onClick = { confirmStop = false; vm.stopRecording(save = true) }) { Text("Enregistrer") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmStop = false; confirmDiscard = true }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { confirmStop = false }) { Text("Continuer") }
                }
            },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text("Supprimer ce parcours ?") },
            text = { Text("Le parcours enregistré sera définitivement perdu.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; vm.stopRecording(save = false) }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Annuler") } },
        )
    }
}

private fun signalText(rec: RecordingSnapshot, now: Long): String {
    val fix = rec.lastFix
    val parts = ArrayList<String>()
    parts.add(SignalQuality.of(fix, now).label)
    fix?.accuracyM?.let { parts.add("±${it.toInt()} m") }
    if (rec.satellitesUsed != null && rec.satellitesVisible != null) parts.add("${rec.satellitesUsed}/${rec.satellitesVisible} satellites")
    return parts.joinToString(" · ")
}

private fun chrono(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

@Composable
private fun RecStat(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.42f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(value, style = MaterialTheme.typography.titleMedium.merge(NumberStyle), maxLines = 1)
    }
}

/** Point rouge qui « respire » pendant l'enregistrement. */
@Composable
fun RecordingDot(status: RecordingStatus, size: androidx.compose.ui.unit.Dp = 12.dp) {
    val t = rememberInfiniteTransition(label = "dot")
    val pulse by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")
    val color = if (status == RecordingStatus.PAUSED) MaterialTheme.colorScheme.outline else Color(0xFFE5484D)
    Box(
        Modifier
            .size(size)
            .alpha(if (status == RecordingStatus.RECORDING) pulse else 1f)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun SignalBars(quality: SignalQuality) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..4) {
            val on = i <= quality.bars
            val color by animateColorAsState(
                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                label = "bar",
            )
            Box(Modifier.size(width = 4.dp, height = (4 + i * 3).dp).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}
