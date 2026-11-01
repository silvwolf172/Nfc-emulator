package com.sillage.gpx.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sillage.gpx.data.GpxEntry
import com.sillage.gpx.record.RecordingSnapshot
import com.sillage.gpx.record.RecordingState
import com.sillage.gpx.record.RecordingStatus
import com.sillage.gpx.ui.record.RecordingDot
import kotlinx.coroutines.delay
import com.sillage.gpx.ui.AppViewModel
import com.sillage.gpx.ui.components.ConfirmDialog
import com.sillage.gpx.ui.components.LiquidBackground
import com.sillage.gpx.ui.components.TextInputDialog
import com.sillage.gpx.ui.components.TrackThumbnail
import com.sillage.gpx.ui.glass.GlassDefaults
import com.sillage.gpx.ui.glass.GlassFab
import com.sillage.gpx.ui.glass.GlassIconButton
import com.sillage.gpx.ui.glass.GlassPillButton
import com.sillage.gpx.ui.glass.GlassSurface
import com.sillage.gpx.ui.glass.LocalGlassBackdrop
import com.sillage.gpx.ui.glass.glassSource
import com.sillage.gpx.ui.glass.rememberGlassBackdrop
import com.sillage.gpx.util.Format

@Composable
fun LibraryScreen(
    vm: AppViewModel,
    onImport: () -> Unit,
    onExport: (String) -> Unit,
    onRecord: () -> Unit,
) {
    val backdrop = rememberGlassBackdrop()
    val recording by RecordingState.state.collectAsStateWithLifecycle()
    var touch by remember { mutableStateOf<Offset?>(null) }
    var fabOpen by rememberSaveable { mutableStateOf(false) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var renameId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }

    val filtered = remember(vm.entries, vm.query) {
        val q = vm.query.trim()
        if (q.isEmpty()) vm.entries else vm.entries.filter { it.name.contains(q, ignoreCase = true) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Le fond « liquide » suit le doigt, sans gêner les gestes des éléments.
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        e.changes.firstOrNull()?.let { if (it.pressed) touch = it.position }
                    }
                }
            },
    ) {
        LiquidBackground(touch, Modifier.fillMaxSize().glassSource(backdrop))

        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 20.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") { Header(vm) }
                if (recording.isActive) {
                    item(key = "recording") { RecordingBanner(recording, onClick = vm::openRecording) }
                }
                item(key = "search") {
                    SearchField(vm.query, { vm.query = it })
                    Spacer(Modifier.height(4.dp))
                }
                if (!vm.libraryLoading && vm.entries.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(onImport = onImport, onCreate = { showCreate = true }, onSample = vm::loadSample)
                    }
                } else if (filtered.isEmpty() && vm.query.isNotBlank()) {
                    item(key = "nores") {
                        Text(
                            "Aucune trace ne correspond à « ${vm.query} »",
                            Modifier.fillMaxWidth().padding(24.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(filtered, key = { it.id }) { entry ->
                    TrackCard(
                        entry = entry,
                        modifier = Modifier.animateItem(),
                        onOpen = { vm.open(entry.id) },
                        onEdit = { vm.openForEdit(entry.id) },
                        onRename = { renameId = entry.id },
                        onDuplicate = { vm.duplicate(entry.id) },
                        onShare = { vm.share(entry.id) },
                        onExport = { onExport(entry.id) },
                        onDelete = { deleteId = entry.id },
                    )
                }
            }

            FabMenu(
                open = fabOpen,
                onToggle = { fabOpen = !fabOpen },
                onImport = { fabOpen = false; onImport() },
                onCreate = { fabOpen = false; showCreate = true },
                onSample = { fabOpen = false; vm.loadSample() },
                onRecord = { fabOpen = false; if (recording.isActive) vm.openRecording() else onRecord() },
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
            )
        }
    }

    if (showCreate) {
        TextInputDialog(
            title = "Nouvelle trace",
            initial = "Ma balade",
            confirmLabel = "Créer",
            icon = Icons.Rounded.Draw,
            onConfirm = { showCreate = false; vm.createNew(it) },
            onDismiss = { showCreate = false },
        )
    }
    renameId?.let { id ->
        TextInputDialog(
            title = "Renommer",
            initial = vm.displayName(id),
            confirmLabel = "Renommer",
            icon = Icons.Rounded.DriveFileRenameOutline,
            onConfirm = { renameId = null; vm.rename(id, it) },
            onDismiss = { renameId = null },
        )
    }
    deleteId?.let { id ->
        ConfirmDialog(
            title = "Supprimer « ${vm.displayName(id)} » ?",
            text = "Le fichier sera définitivement retiré de la bibliothèque.",
            confirmLabel = "Supprimer",
            icon = Icons.Rounded.Delete,
            destructive = true,
            onConfirm = { deleteId = null; vm.delete(id) },
            onDismiss = { deleteId = null },
        )
    }
}

@Composable
private fun RecordingBanner(rec: RecordingSnapshot, onClick: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 28.dp,
        style = GlassDefaults.tinted(MaterialTheme.colorScheme.errorContainer, 0.7f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        onClick = onClick,
        pressedScale = 1.04f,
        semanticLabel = "Ouvrir l'enregistrement en cours",
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            RecordingDot(rec.status, 14.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (rec.status == RecordingStatus.PAUSED) "Enregistrement en pause" else "Enregistrement en cours",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    Format.distance(rec.distanceM) + " · " + Format.duration(rec.elapsedMs(now)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun Header(vm: AppViewModel) {
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text("Sillage", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurface)
        val total = vm.entries.sumOf { it.stats.distanceM }
        val subtitle = when {
            vm.libraryLoading -> "Chargement…"
            vm.entries.isEmpty() -> "Lire, créer et modifier vos traces GPX"
            else -> Format.count(vm.entries.size, "trace") + " · " + Format.distance(total) + " au total"
        }
        Text(subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        cornerRadius = 28.dp,
        style = GlassDefaults.panel(),
        interactive = false,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text("Rechercher une trace", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (value.isNotEmpty()) {
                GlassIconButton(Icons.Rounded.Close, "Effacer", onClick = { onChange("") }, size = 36.dp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackCard(
    entry: GpxEntry,
    modifier: Modifier,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 30.dp,
        style = GlassDefaults.style(),
        onClick = onOpen,
        onLongClick = { menu = true },
        pressedScale = 1.03f,
        maxPull = 6.dp,
        stretch = 0.6f,
        semanticLabel = "Ouvrir ${entry.name}",
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(78.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(cs.primaryContainer.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                if (entry.error != null) {
                    Icon(Icons.Rounded.Warning, null, tint = cs.error)
                } else if (entry.preview.isEmpty()) {
                    Icon(Icons.Rounded.Map, null, tint = cs.onPrimaryContainer.copy(alpha = 0.6f))
                } else {
                    TrackThumbnail(entry.preview, Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                val date = entry.stats.startTime ?: entry.modified
                val details = if (entry.error != null) entry.error else buildString {
                    append(Format.date(date))
                    if (entry.stats.waypointCount > 0) append(" · ").append(Format.count(entry.stats.waypointCount, "repère"))
                }
                Text(details, style = MaterialTheme.typography.bodySmall, color = if (entry.error != null) cs.error else cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (entry.error == null) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MiniStat(Icons.Rounded.Straighten, Format.distance(entry.stats.distanceM))
                        if (entry.stats.ascentM > 0) MiniStat(Icons.AutoMirrored.Rounded.TrendingUp, Format.elevation(entry.stats.ascentM))
                        entry.stats.durationMs?.let { MiniStat(Icons.Rounded.Timer, Format.duration(it)) }
                    }
                }
            }
            Box {
                GlassIconButton(
                    Icons.Rounded.MoreVert, "Options", onClick = { menu = true }, size = 40.dp,
                    style = GlassDefaults.style().copy(shadow = false),
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp)) {
                    MenuItem("Modifier", Icons.Rounded.Edit) { menu = false; onEdit() }
                    MenuItem("Renommer", Icons.Rounded.DriveFileRenameOutline) { menu = false; onRename() }
                    MenuItem("Dupliquer", Icons.Rounded.ContentCopy) { menu = false; onDuplicate() }
                    HorizontalDivider()
                    MenuItem("Partager", Icons.Rounded.Share) { menu = false; onShare() }
                    MenuItem("Enregistrer sous…", Icons.Rounded.SaveAlt) { menu = false; onExport() }
                    HorizontalDivider()
                    MenuItem("Supprimer", Icons.Rounded.Delete, destructive = true) { menu = false; onDelete() }
                }
            }
        }
    }
}

@Composable
fun MenuItem(text: String, icon: ImageVector, destructive: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val color = if (destructive) MaterialTheme.colorScheme.error else LocalContentColor.current
    DropdownMenuItem(
        text = { Text(text, color = color) },
        leadingIcon = { Icon(icon, null, tint = color) },
        onClick = onClick,
        enabled = enabled,
    )
}

@Composable
private fun MiniStat(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun EmptyState(onImport: () -> Unit, onCreate: () -> Unit, onSample: () -> Unit) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        cornerRadius = 36.dp,
        style = GlassDefaults.panel(),
        interactive = false,
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(88.dp).clip(RoundedCornerShape(30.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Explore, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(16.dp))
            Text("Aucune trace pour l'instant", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Importez un fichier GPX, dessinez votre propre parcours sur la carte, ou découvrez l'app avec des exemples.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            GlassPillButton("Importer un GPX", onImport, Modifier.fillMaxWidth(), icon = Icons.Rounded.FileOpen,
                style = GlassDefaults.tinted(), contentColor = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.height(10.dp))
            GlassPillButton("Créer une trace", onCreate, Modifier.fillMaxWidth(), icon = Icons.Rounded.Draw)
            Spacer(Modifier.height(10.dp))
            GlassPillButton("Charger des exemples", onSample, Modifier.fillMaxWidth(), icon = Icons.Rounded.Explore)
        }
    }
}

@Composable
private fun FabMenu(
    open: Boolean,
    onToggle: () -> Unit,
    onImport: () -> Unit,
    onCreate: () -> Unit,
    onSample: () -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(if (open) 135f else 0f, spring(dampingRatio = 0.5f, stiffness = 300f), label = "fab")
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val items = listOf(
            Triple("Exemples", Icons.Rounded.Explore, onSample),
            Triple("Nouvelle trace", Icons.Rounded.Draw, onCreate),
            Triple("Importer un GPX", Icons.Rounded.FileOpen, onImport),
            Triple("Enregistrer un parcours", Icons.Rounded.FiberManualRecord, onRecord),
        )
        items.forEachIndexed { i, (label, icon, action) ->
            AnimatedVisibility(
                visible = open,
                enter = fadeIn() + scaleIn(spring(dampingRatio = 0.45f, stiffness = 380f - i * 60f), initialScale = 0.4f) +
                    slideInVertically(spring(dampingRatio = 0.6f, stiffness = 300f)) { it * (items.size - i) / 2 },
                exit = fadeOut() + scaleOut(targetScale = 0.6f) + slideOutVertically { it / 2 },
            ) {
                GlassPillButton(label, action, icon = icon, height = 52.dp, style = GlassDefaults.panel())
            }
        }
        GlassFab(
            icon = Icons.Rounded.Add,
            contentDescription = if (open) "Fermer" else "Ajouter",
            onClick = onToggle,
            iconRotation = rotation,
        )
    }
}
