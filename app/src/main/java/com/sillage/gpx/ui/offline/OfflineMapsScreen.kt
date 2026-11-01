package com.sillage.gpx.ui.offline

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sillage.gpx.offline.CatalogEntry
import com.sillage.gpx.offline.InstalledMap
import com.sillage.gpx.offline.MapDownload
import com.sillage.gpx.offline.OfflineMaps
import com.sillage.gpx.offline.prettyName
import com.sillage.gpx.ui.AppViewModel
import com.sillage.gpx.ui.components.ConfirmDialog
import com.sillage.gpx.ui.components.LiquidBackground
import com.sillage.gpx.ui.glass.GlassDefaults
import com.sillage.gpx.ui.glass.GlassIconButton
import com.sillage.gpx.ui.glass.GlassSurface
import com.sillage.gpx.ui.glass.LocalGlassBackdrop
import com.sillage.gpx.ui.glass.glassSource
import com.sillage.gpx.ui.glass.rememberGlassBackdrop
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun OfflineMapsScreen(vm: AppViewModel) {
    val backdrop = rememberGlassBackdrop()
    val context = LocalContext.current
    var toDownload by remember { mutableStateOf<CatalogEntry?>(null) }
    var toDelete by remember { mutableStateOf<InstalledMap?>(null) }

    // Suivi des téléchargements tant que l'écran est ouvert.
    LaunchedEffect(Unit) {
        while (true) { vm.refreshOffline(); delay(if (vm.offlineDownloads.isEmpty()) 4_000 else 1_000) }
    }
    BackHandler {
        if (vm.catalogPath.isNotEmpty()) vm.browse(parentOf(vm.catalogPath)) else vm.leaveOfflineMaps()
    }

    Box(Modifier.fillMaxSize()) {
        LiquidBackground(touch = null as Offset?, modifier = Modifier.fillMaxSize().glassSource(backdrop))
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "header") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", onClick = vm::leaveOfflineMaps, size = 52.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Cartes hors ligne", style = MaterialTheme.typography.headlineMedium)
                            val total = vm.offlineMaps.sumOf { it.sizeBytes }
                            Text(
                                if (vm.offlineMaps.isEmpty()) "Aucune carte installée" else "${vm.offlineMaps.size} carte(s) · ${bytes(total)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item(key = "intro") {
                    GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 28.dp, style = GlassDefaults.panel(), interactive = false) {
                        Text(
                            "Téléchargez une région pour voir la carte sans réseau, même en montagne. " +
                                "Choisissez ensuite « Hors ligne » dans le menu des fonds de carte. " +
                                "Espace libre : ${bytes(OfflineMaps.freeSpaceBytes(context))}.",
                            Modifier.padding(18.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                if (vm.offlineMaps.isNotEmpty()) {
                    item(key = "installed-title") { SectionTitle("Sur ce téléphone") }
                    items(vm.offlineMaps, key = { "m-" + it.file.name }) { m ->
                        Row2(icon = Icons.Rounded.Map, title = m.label, subtitle = bytes(m.sizeBytes)) {
                            GlassIconButton(Icons.Rounded.Delete, "Supprimer ${m.label}", onClick = { toDelete = m }, size = 40.dp,
                                style = GlassDefaults.style().copy(shadow = false))
                        }
                    }
                }

                if (vm.offlineDownloads.isNotEmpty()) {
                    item(key = "dl-title") { SectionTitle("Téléchargements") }
                    items(vm.offlineDownloads, key = { "d-" + it.id }) { d -> DownloadRow(d, onCancel = { vm.cancelDownload(d) }) }
                }

                item(key = "catalog-title") {
                    Column {
                        SectionTitle("Télécharger une région")
                        Breadcrumb(vm.catalogPath, onNavigate = vm::browse)
                    }
                }
                val catalog = vm.catalog
                when {
                    vm.catalogError != null -> item(key = "error") {
                        Row2(icon = Icons.Rounded.Refresh, title = "Catalogue indisponible", subtitle = vm.catalogError ?: "", onClick = { vm.browse(vm.catalogPath) }) {}
                    }
                    catalog == null -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    else -> items(catalog, key = { "c-" + it.path }) { e ->
                        val installed = vm.offlineMaps.any { it.file.name == e.name }
                        val downloading = vm.offlineDownloads.any { it.fileName == e.name && !it.failed }
                        if (e.isFolder) {
                            Row2(icon = if (vm.catalogPath.isEmpty()) Icons.Rounded.Public else Icons.Rounded.Folder, title = e.label, subtitle = null, onClick = { vm.browse(e.path) }) {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                            }
                        } else {
                            Row2(
                                icon = Icons.Rounded.Map,
                                title = e.label,
                                subtitle = e.size?.let { "Carte vectorielle · $it" },
                                onClick = if (installed || downloading) null else ({ toDownload = e }),
                            ) {
                                when {
                                    installed -> Icon(Icons.Rounded.CheckCircle, "Installée", tint = MaterialTheme.colorScheme.primary)
                                    downloading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                                    else -> Icon(Icons.Rounded.Download, "Télécharger", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                item(key = "credits") {
                    Text(
                        "Cartes © contributeurs OpenStreetMap, au format mapsforge (download.mapsforge.org).",
                        Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    toDownload?.let { e ->
        AlertDialog(
            onDismissRequest = { toDownload = null },
            icon = { Icon(Icons.Rounded.CloudDownload, null) },
            title = { Text("Télécharger « ${e.label} » ?") },
            text = {
                Text(
                    "Taille : ${e.size ?: "inconnue"}. Une connexion Wi-Fi est conseillée. " +
                        "Le téléchargement continue en arrière-plan.",
                )
            },
            confirmButton = { TextButton(onClick = { toDownload = null; vm.downloadMap(e) }) { Text("Télécharger") } },
            dismissButton = { TextButton(onClick = { toDownload = null }) { Text("Annuler") } },
        )
    }
    toDelete?.let { m ->
        ConfirmDialog(
            title = "Supprimer « ${m.label} » ?",
            text = "La carte (${bytes(m.sizeBytes)}) ne sera plus disponible hors ligne.",
            confirmLabel = "Supprimer",
            icon = Icons.Rounded.Delete,
            destructive = true,
            onConfirm = { toDelete = null; vm.deleteMap(m) },
            onDismiss = { toDelete = null },
        )
    }
}

private fun parentOf(path: String): String =
    path.trimEnd('/').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }

private fun bytes(b: Long): String = when {
    b >= 1L shl 30 -> String.format(Locale.FRANCE, "%.1f Go", b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> String.format(Locale.FRANCE, "%d Mo", b / (1L shl 20))
    else -> String.format(Locale.FRANCE, "%d ko", b / 1024)
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, Modifier.padding(start = 6.dp, top = 8.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Breadcrumb(path: String, onNavigate: (String) -> Unit) {
    val parts = path.trimEnd('/').split('/').filter { it.isNotEmpty() }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crumb("Monde", selected = parts.isEmpty()) { onNavigate("") }
        parts.forEachIndexed { i, p ->
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Crumb(prettyName(p), selected = i == parts.lastIndex) { onNavigate(parts.take(i + 1).joinToString("/") + "/") }
        }
    }
}

@Composable
private fun Crumb(text: String, selected: Boolean, onClick: () -> Unit) {
    GlassSurface(
        modifier = Modifier.height(36.dp),
        cornerRadius = 18.dp,
        style = if (selected) GlassDefaults.tinted(MaterialTheme.colorScheme.primaryContainer, 0.8f) else GlassDefaults.style(),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        onClick = onClick,
        pressedScale = 1.1f,
    ) {
        Text(text, Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun Row2(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        onClick = onClick,
        pressedScale = 1.03f,
        maxPull = 6.dp,
        stretch = 0.6f,
        semanticLabel = title,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            trailing()
        }
    }
}

@Composable
private fun DownloadRow(d: MapDownload, onCancel: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 24.dp, interactive = false) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            d.failed -> "Échec du téléchargement"
                            d.total > 0 -> "${bytes(d.downloaded)} / ${bytes(d.total)}"
                            else -> "En attente…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (d.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GlassIconButton(Icons.Rounded.Close, "Annuler", onClick = onCancel, size = 40.dp, style = GlassDefaults.style().copy(shadow = false))
            }
            Spacer(Modifier.height(8.dp))
            val p = d.progress
            if (p != null) {
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
            }
        }
    }
}
