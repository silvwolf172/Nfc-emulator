package com.sillage.gpx.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Moving
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Polyline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sillage.gpx.gpx.ElevationProfile
import com.sillage.gpx.gpx.GpxDocument
import com.sillage.gpx.gpx.GpxEdit
import com.sillage.gpx.gpx.GpxPoint
import com.sillage.gpx.gpx.GpxStats
import com.sillage.gpx.gpx.Geo
import com.sillage.gpx.gpx.LineRef
import com.sillage.gpx.ui.AppViewModel
import com.sillage.gpx.ui.components.ElevationChart
import com.sillage.gpx.ui.glass.GlassDefaults
import com.sillage.gpx.ui.glass.GlassPillButton
import com.sillage.gpx.ui.theme.trackPalette
import com.sillage.gpx.util.Format

private data class Stat(val icon: ImageVector, val label: String, val value: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsGrid(stats: GpxStats, modifier: Modifier = Modifier) {
    val hasEle = stats.maxEle != null
    val all = buildList {
        add(Stat(Icons.Rounded.Straighten, "Distance", Format.distance(stats.distanceM)))
        if (hasEle) {
            add(Stat(Icons.AutoMirrored.Rounded.TrendingUp, "Dénivelé +", Format.elevation(stats.ascentM)))
            add(Stat(Icons.AutoMirrored.Rounded.TrendingDown, "Dénivelé −", Format.elevation(stats.descentM)))
        }
        stats.durationMs?.let { add(Stat(Icons.Rounded.Timer, "Durée", Format.duration(it))) }
        stats.avgMovingSpeedMs?.let { add(Stat(Icons.Rounded.Speed, "Vitesse moy.", Format.speed(it))) }
        stats.maxEle?.let { add(Stat(Icons.Rounded.Landscape, "Alt. max", Format.elevation(it))) }
        stats.movingMs?.takeIf { it > 0 }?.let { add(Stat(Icons.Rounded.Moving, "Temps actif", Format.duration(it))) }
        stats.maxSpeedMs?.let { add(Stat(Icons.Rounded.AvTimer, "Vitesse max", Format.speed(it))) }
        stats.minEle?.let { add(Stat(Icons.Rounded.Height, "Alt. min", Format.elevation(it))) }
        add(Stat(Icons.Rounded.Polyline, "Points", String.format(java.util.Locale.FRANCE, "%,d", stats.pointCount)))
        if (stats.waypointCount > 0) add(Stat(Icons.Rounded.Place, "Repères", stats.waypointCount.toString()))
        stats.startTime?.let { add(Stat(Icons.Rounded.Schedule, "Départ", Format.dateTime(it))) }
    }
    // Six tuiles principales, le reste en ligne compacte pour laisser la carte visible.
    val primary = all.take(6)
    val secondary = all.drop(6)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        primary.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { StatTile(it, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (secondary.isNotEmpty()) {
            FlowRow(
                Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                secondary.forEach { st ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(st.icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text(st.label + " ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(st.value, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(stat: Stat, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(cs.surfaceContainerHighest.copy(alpha = 0.42f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(stat.icon, null, Modifier.size(15.dp), tint = cs.primary)
            Spacer(Modifier.width(5.dp))
            Text(stat.label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(2.dp))
        Text(stat.value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Résumé compact affiché quand le panneau est replié. */
@Composable
fun CompactSummary(stats: GpxStats, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        SummaryItem(Icons.Rounded.Straighten, Format.distance(stats.distanceM))
        if (stats.maxEle != null) SummaryItem(Icons.AutoMirrored.Rounded.TrendingUp, Format.elevation(stats.ascentM))
        stats.durationMs?.let { SummaryItem(Icons.Rounded.Timer, Format.duration(it)) }
        if (stats.durationMs == null) SummaryItem(Icons.Rounded.Polyline, Format.count(stats.pointCount, "point"))
    }
}

@Composable
private fun SummaryItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun ProfileTab(profile: ElevationProfile?, vm: AppViewModel, onScrub: (GpxPoint?) -> Unit) {
    if (profile == null) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Rounded.ShowChart, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text("Pas de données d'altitude dans ce fichier.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            GlassPillButton(
                "Ajouter les altitudes", { vm.fillElevations() }, icon = Icons.Rounded.CloudDownload,
                style = GlassDefaults.tinted(), contentColor = MaterialTheme.colorScheme.onPrimary,
                enabled = vm.busy == null,
            )
        }
        return
    }
    ElevationChart(
        profile = profile,
        modifier = Modifier.fillMaxWidth().height(170.dp),
        onScrub = { i -> onScrub(i?.let { profile.points.getOrNull(it) }) },
    )
}

/** Liste des traces, itinéraires et repères du document. */
@Composable
fun ElementsTab(
    doc: GpxDocument,
    activeLine: LineRef?,
    editing: Boolean,
    onLineClick: (LineRef) -> Unit,
    onLineLongClick: (LineRef) -> Unit,
    onWaypointClick: (Int) -> Unit,
) {
    val palette = trackPalette()
    val lines = GpxEdit.lineRefs(doc)
    val colorFor: (LineRef) -> Color = { ref ->
        when (ref) {
            is LineRef.Track -> palette[ref.track % palette.size]
            is LineRef.Route -> palette[(doc.tracks.size + ref.route) % palette.size]
        }
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
        if (lines.isNotEmpty()) item { SectionLabel("Traces et itinéraires") }
        itemsIndexed(lines) { _, ref ->
            val pts = GpxEdit.points(doc, ref)
            val active = editing && ref == activeLine
            ElementRow(
                color = colorFor(ref),
                title = AppViewModel.lineName(doc, ref),
                subtitle = (if (ref is LineRef.Route) "Itinéraire · " else "") + Format.distance(Geo.length(pts)) + " · " + Format.count(pts.size, "point"),
                highlighted = active,
                onClick = { onLineClick(ref) },
                onLongClick = { onLineLongClick(ref) },
            )
        }
        if (doc.waypoints.isNotEmpty()) item { SectionLabel("Repères") }
        itemsIndexed(doc.waypoints) { i, w ->
            ElementRow(
                color = MaterialTheme.colorScheme.tertiary,
                title = w.name ?: "Repère ${i + 1}",
                subtitle = listOfNotNull(w.desc?.take(60), w.ele?.let { Format.elevation(it) }, Format.coordinates(w.lat, w.lon)).joinToString(" · "),
                highlighted = false,
                pin = true,
                onClick = { onWaypointClick(i) },
                onLongClick = null,
            )
        }
        if (lines.isEmpty() && doc.waypoints.isEmpty()) {
            item {
                Text("Ce fichier est vide.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ElementRow(
    color: Color,
    title: String,
    subtitle: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    pin: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (highlighted) cs.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
            .combinedClickableCompat(onClick, onLongClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(if (pin) 14.dp else 12.dp).clip(if (pin) RoundedCornerShape(50, 50, 50, 10) else CircleShape).background(color),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier =
    if (onLongClick == null) this.clickable(onClick = onClick)
    else this.then(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick))
