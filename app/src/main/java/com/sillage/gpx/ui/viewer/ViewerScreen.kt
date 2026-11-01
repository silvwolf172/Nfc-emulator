package com.sillage.gpx.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Polyline
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sillage.gpx.data.RoutingProfile
import com.sillage.gpx.gpx.ElevationProfile
import com.sillage.gpx.gpx.GeoBounds
import com.sillage.gpx.gpx.GpxEdit
import com.sillage.gpx.gpx.GpxStats
import com.sillage.gpx.gpx.LineRef
import com.sillage.gpx.ui.AppViewModel
import com.sillage.gpx.ui.EditTool
import com.sillage.gpx.ui.components.ConfirmDialog
import com.sillage.gpx.ui.components.FieldsDialog
import com.sillage.gpx.ui.components.TextInputDialog
import com.sillage.gpx.ui.glass.BarButton
import com.sillage.gpx.ui.glass.GlassBar
import com.sillage.gpx.ui.glass.GlassDefaults
import com.sillage.gpx.ui.glass.GlassFab
import com.sillage.gpx.ui.glass.GlassIconButton
import com.sillage.gpx.ui.glass.GlassSurface
import com.sillage.gpx.ui.glass.LiquidTab
import com.sillage.gpx.ui.glass.LiquidTabs
import com.sillage.gpx.ui.glass.LocalGlassBackdrop
import com.sillage.gpx.ui.glass.glassSource
import com.sillage.gpx.ui.glass.rememberBounceState
import com.sillage.gpx.ui.glass.rememberGlassBackdrop
import com.sillage.gpx.ui.library.MenuItem
import com.sillage.gpx.ui.map.GpxMap
import com.sillage.gpx.ui.map.MapController
import com.sillage.gpx.ui.map.MapEditState
import com.sillage.gpx.ui.map.MapInsets
import com.sillage.gpx.ui.map.MapStyle
import com.sillage.gpx.ui.map.TileSources
import com.sillage.gpx.ui.map.myLocation
import com.sillage.gpx.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

@Composable
fun ViewerScreen(
    vm: AppViewModel,
    onExport: (String) -> Unit,
    hasLocationPermission: () -> Boolean,
    requestLocation: (onGranted: () -> Unit) -> Unit,
) {
    val doc = vm.doc
    val backdrop = rememberGlassBackdrop()
    val map = remember { MapController() }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val stats = remember(doc) { doc?.let(GpxStats::of) }
    val profile = remember(doc) { doc?.let(ElevationProfile::of) }

    var panelExpanded by rememberSaveable { mutableStateOf(true) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showMyLocation by rememberSaveable { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showMeta by remember { mutableStateOf(false) }
    var showSimplify by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var renameFile by remember { mutableStateOf(false) }
    var lineAction by remember { mutableStateOf<LineRef?>(null) }
    var renameLine by remember { mutableStateOf<LineRef?>(null) }

    var topHeight by remember { mutableIntStateOf(0) }
    var topBarBottom by remember { mutableIntStateOf(0) }
    var bottomAreaTop by remember { mutableIntStateOf(0) }
    var screenHeight by remember { mutableIntStateOf(0) }
    val insets = MapInsets(
        left = with(density) { 24.dp.roundToPx() },
        // Marge supplémentaire en haut pour la tête des épingles de repère.
        top = topBarBottom + with(density) { 40.dp.roundToPx() },
        right = with(density) { 24.dp.roundToPx() },
        bottom = (screenHeight - bottomAreaTop).coerceAtLeast(0) + with(density) { 16.dp.roundToPx() },
    )
    val currentInsets by rememberUpdatedState(insets)

    // Cadrage initial : immédiat avec une estimation, puis affiné quand la mise en page s'est stabilisée
    // (le panneau du bas arrive avec une animation à ressort).
    var fitted by remember(vm.openId) { mutableStateOf(false) }
    LaunchedEffect(doc != null) {
        if (doc != null && !fitted) {
            fitted = true
            val estimate = currentInsets.copy(
                top = maxOf(currentInsets.top, with(density) { 110.dp.roundToPx() }),
                bottom = maxOf(currentInsets.bottom, (screenHeight * 0.5f).toInt()),
            )
            map.fit(doc.bounds(), estimate, animate = false)
            var last = currentInsets
            var stableMs = 0
            withTimeoutOrNull(2500) {
                while (stableMs < 300) {
                    delay(50)
                    if (currentInsets == last) stableMs += 50 else { stableMs = 0; last = currentInsets }
                }
            }
            map.fit(doc.bounds(), currentInsets, animate = true)
        }
    }

    fun back() {
        when {
            vm.editing && vm.dirty -> showDiscard = true
            vm.editing -> vm.cancelEditing()
            else -> vm.closeViewer()
        }
    }
    BackHandler { back() }

    fun locate() {
        val go = {
            showMyLocation = true
            scope.launch {
                repeat(40) {
                    map.myLocation()?.let { map.centerOn(it.latitude, it.longitude, 15.0); return@launch }
                    delay(250)
                }
                vm.message("Position introuvable pour le moment")
            }
            Unit
        }
        if (hasLocationPermission()) go() else requestLocation(go)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).onSizeChanged { screenHeight = it.height }) {
        if (doc != null) {
            GpxMap(
                doc = doc,
                controller = map,
                style = vm.mapStyle,
                offlineFiles = vm.offlineFiles,
                modifier = Modifier.fillMaxSize().glassSource(backdrop),
                edit = MapEditState(
                    editMode = vm.editing,
                    activeLine = vm.activeLine,
                    dragPoints = vm.editing && (vm.tool == EditTool.DRAW || vm.tool == EditTool.MOVE),
                    dragWaypoints = vm.editing && vm.tool != EditTool.ERASE,
                ),
                highlight = vm.highlight,
                selectedWaypoint = vm.selectedWaypoint,
                showMyLocation = showMyLocation,
                listener = vm.mapListener,
            )
        } else {
            Box(Modifier.fillMaxSize().glassSource(backdrop), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            // ------------------------------------------------------------ Barre du haut
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .onSizeChanged { topHeight = it.height }
                    .onGloballyPositioned { topBarBottom = (it.positionInRoot().y + it.size.height).roundToInt() },
            ) {
                AnimatedContent(
                    targetState = vm.editing,
                    transitionSpec = {
                        (fadeIn() + scaleIn(spring(dampingRatio = 0.55f, stiffness = 400f), initialScale = 0.85f))
                            .togetherWith(fadeOut() + scaleOut(targetScale = 0.9f))
                    },
                    label = "topbar",
                ) { editing ->
                    if (!editing) {
                        ViewTopBar(
                            title = doc?.displayName ?: vm.openId?.let(vm::displayName) ?: "",
                            subtitle = stats?.let { s ->
                                listOfNotNull(
                                    Format.distance(s.distanceM),
                                    s.maxEle?.let { "↗ " + Format.elevation(s.ascentM) },
                                    s.durationMs?.let(Format::duration),
                                ).joinToString(" · ")
                            } ?: "",
                            mapStyle = vm.mapStyle,
                            onBack = ::back,
                            onTitle = { panelExpanded = !panelExpanded },
                            onStyle = vm::changeMapStyle,
                            onOfflineMaps = vm::openOfflineMaps,
                            onEdit = vm::startEditing,
                            onRename = { renameFile = true },
                            onInfo = { showMeta = true },
                            onShare = { vm.openId?.let(vm::share) },
                            onExport = { vm.openId?.let(onExport) },
                            onElevation = vm::fillElevations,
                            onDelete = { showDelete = true },
                        )
                    } else {
                        EditTopBar(
                            vm = vm,
                            onCancel = { if (vm.dirty) showDiscard = true else vm.cancelEditing() },
                            onDone = vm::finishEditing,
                            onInfo = { showMeta = true },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                // Indicateur d'activité réseau
                AnimatedVisibility(
                    visible = vm.busy != null,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    enter = fadeIn() + scaleIn(spring(dampingRatio = 0.4f, stiffness = 500f), initialScale = 0.5f),
                    exit = fadeOut() + scaleOut(),
                ) {
                    GlassSurface(Modifier.height(40.dp), cornerRadius = 20.dp, interactive = false) {
                        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.5.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(vm.busy ?: "", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // ------------------------------------------------------------ Boutons latéraux
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, topHeight) }
                    .padding(end = 12.dp, top = 52.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassIconButton(Icons.Rounded.ZoomOutMap, "Recentrer sur la trace", onClick = {
                    map.fit(doc?.bounds(), insets)
                })
                GlassIconButton(
                    Icons.Rounded.MyLocation, "Ma position", onClick = ::locate,
                    tint = if (showMyLocation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }

            Text(
                TileSources.attribution(vm.mapStyle),
                Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(0, topHeight) }
                    .padding(start = 20.dp, top = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ------------------------------------------------------------ Bas de l'écran
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(12.dp)
                    .onGloballyPositioned { bottomAreaTop = it.positionInRoot().y.roundToInt() },
            ) {
                // Fiche du repère sélectionné
                val wpIndex = vm.selectedWaypoint
                val wp = wpIndex?.let { doc?.waypoints?.getOrNull(it) }
                AnimatedVisibility(
                    visible = wp != null && !vm.editing,
                    enter = fadeIn() + slideInVertically(spring(dampingRatio = 0.6f, stiffness = 380f)) { it / 2 } +
                        scaleIn(spring(dampingRatio = 0.5f, stiffness = 380f), initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.9f),
                ) {
                    if (wp != null && wpIndex != null) {
                        WaypointCard(
                            name = wp.name ?: "Repère ${wpIndex + 1}",
                            details = listOfNotNull(wp.desc, wp.ele?.let { "Altitude " + Format.elevation(it) }, Format.coordinates(wp.lat, wp.lon)),
                            onCenter = { map.centerOn(wp.lat, wp.lon, 16.0) },
                            onClose = { vm.selectedWaypoint = null },
                        )
                    }
                }

                AnimatedContent(
                    targetState = vm.editing,
                    transitionSpec = {
                        (fadeIn() + slideInVertically(spring(dampingRatio = 0.6f, stiffness = 350f)) { it / 3 })
                            .togetherWith(fadeOut() + slideOutVertically { it / 3 })
                            .using(SizeTransform(clip = false))
                    },
                    label = "bottom",
                ) { editing ->
                    if (editing) {
                        EditToolbar(
                            vm = vm,
                            onSimplify = { showSimplify = true },
                            onLines = { panelExpanded = true; tab = 2 },
                        )
                    } else if (doc != null && stats != null) {
                        Column {
                            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.End) {
                                GlassFab(Icons.Rounded.Edit, "Modifier la trace", onClick = vm::startEditing, text = "Modifier", size = 56.dp)
                            }
                            InfoPanel(
                                expanded = panelExpanded,
                                onExpandedChange = { panelExpanded = it },
                                tab = tab,
                                onTab = { tab = it },
                                stats = stats,
                                profile = profile,
                                vm = vm,
                                onLineClick = { ref ->
                                    GeoBounds.of(GpxEdit.points(doc, ref).asSequence())?.let { map.fit(it, insets) }
                                },
                                onLineLongClick = { lineAction = it },
                                onWaypointClick = { i ->
                                    vm.selectedWaypoint = i
                                    doc.waypoints.getOrNull(i)?.let { map.centerOn(it.lat, it.lon, 16.0) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- Dialogues
    if (showDiscard) {
        ConfirmDialog(
            title = "Abandonner les modifications ?",
            text = "Les changements apportés depuis le début de l'édition seront perdus.",
            confirmLabel = "Abandonner",
            destructive = true,
            onConfirm = { showDiscard = false; vm.cancelEditing() },
            onDismiss = { showDiscard = false },
        )
    }
    if (showDelete) {
        val id = vm.openId
        ConfirmDialog(
            title = "Supprimer ce fichier ?",
            text = "« ${doc?.displayName ?: id} » sera définitivement supprimé.",
            confirmLabel = "Supprimer",
            icon = Icons.Rounded.Delete,
            destructive = true,
            onConfirm = { showDelete = false; id?.let(vm::delete) },
            onDismiss = { showDelete = false },
        )
    }
    if (renameFile) {
        val id = vm.openId
        TextInputDialog(
            title = "Renommer",
            initial = doc?.displayName ?: "",
            confirmLabel = "Renommer",
            onConfirm = { renameFile = false; id?.let { i -> vm.rename(i, it) } },
            onDismiss = { renameFile = false },
        )
    }
    if (showMeta && doc != null) {
        val s = stats
        FieldsDialog(
            title = "Informations du fichier",
            icon = Icons.Rounded.Info,
            fields = listOf(
                "Nom" to (doc.metadata.name ?: doc.displayName ?: ""),
                "Description" to (doc.metadata.desc ?: ""),
                "Auteur" to (doc.metadata.author ?: ""),
            ),
            confirmLabel = if (vm.editing) "Appliquer" else "Enregistrer",
            footer = listOfNotNull(
                doc.creator?.let { "Créé avec : $it" },
                s?.let { "${Format.count(it.trackCount, "trace")} · ${Format.count(it.segmentCount, "section")} · ${Format.count(it.routeCount, "itinéraire")}" },
            ).joinToString("\n"),
            onConfirm = { (name, desc, author) -> showMeta = false; vm.updateMetadata(name, desc, author) },
            onDismiss = { showMeta = false },
        )
    }
    if (showSimplify && doc != null) {
        SimplifyDialog(
            pointCount = doc.lines.sumOf { it.size },
            preview = { tol -> GpxEdit.simplifyAll(doc, tol).lines.sumOf { it.size } },
            onConfirm = { showSimplify = false; vm.simplify(it) },
            onDismiss = { showSimplify = false },
        )
    }
    vm.waypointDraft?.let { draft ->
        FieldsDialog(
            title = if (draft.index == null) "Nouveau repère" else "Modifier le repère",
            icon = Icons.Rounded.Place,
            fields = listOf("Nom" to (draft.point.name ?: ""), "Description" to (draft.point.desc ?: "")),
            confirmLabel = if (draft.index == null) "Ajouter" else "Enregistrer",
            footer = Format.coordinates(draft.point.lat, draft.point.lon),
            extraAction = draft.index?.let { i -> { TextButton(onClick = { vm.deleteWaypoint(i) }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } } },
            onConfirm = { (name, desc) ->
                vm.saveWaypoint(draft.copy(point = draft.point.copy(name = name.ifBlank { null }, desc = desc.ifBlank { null })))
            },
            onDismiss = { vm.waypointDraft = null },
        )
    }
    lineAction?.let { line ->
        if (doc != null) {
            LineActionsDialog(
                title = AppViewModel.lineName(doc, line),
                isRoute = line is LineRef.Route,
                editing = vm.editing,
                onRename = { lineAction = null; renameLine = line },
                onSelect = { lineAction = null; vm.activeLine = line },
                onReverse = { lineAction = null; vm.activeLine = line; vm.reverseActive() },
                onConvert = { lineAction = null; vm.convertLine(line) },
                onDelete = { lineAction = null; vm.deleteLine(line) },
                onDismiss = { lineAction = null },
            )
        }
    }
    renameLine?.let { line ->
        if (doc != null) {
            TextInputDialog(
                title = "Renommer la ligne",
                initial = AppViewModel.lineName(doc, line),
                confirmLabel = "Renommer",
                onConfirm = { renameLine = null; vm.renameLine(line, it) },
                onDismiss = { renameLine = null },
            )
        }
    }
}

@Composable
private fun ViewTopBar(
    title: String,
    subtitle: String,
    mapStyle: MapStyle,
    onBack: () -> Unit,
    onTitle: () -> Unit,
    onStyle: (MapStyle) -> Unit,
    onOfflineMaps: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onInfo: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onElevation: () -> Unit,
    onDelete: () -> Unit,
) {
    var layers by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", onClick = onBack, size = 52.dp)
        GlassSurface(
            modifier = Modifier.weight(1f).height(52.dp),
            cornerRadius = 26.dp,
            onClick = onTitle,
            pressedScale = 1.04f,
            contentAlignment = Alignment.CenterStart,
            semanticLabel = "Afficher ou masquer le panneau",
        ) {
            Column(Modifier.padding(horizontal = 18.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        Box {
            GlassIconButton(Icons.Rounded.Layers, "Fond de carte", onClick = { layers = true }, size = 52.dp)
            DropdownMenu(expanded = layers, onDismissRequest = { layers = false }, shape = RoundedCornerShape(24.dp)) {
                Text("Fond de carte", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                MapStyle.entries.forEach { s ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .androidxClickable { onStyle(s); layers = false }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .width(240.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s == mapStyle, onClick = { onStyle(s); layers = false })
                        Column {
                            Text(s.label, style = MaterialTheme.typography.bodyLarge)
                            Text(s.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                HorizontalDivider()
                MenuItem("Gérer les cartes hors ligne…", Icons.Rounded.CloudDownload) { layers = false; onOfflineMaps() }
            }
        }
        Box {
            GlassIconButton(Icons.Rounded.MoreVert, "Plus d'options", onClick = { menu = true }, size = 52.dp)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp)) {
                MenuItem("Modifier la trace", Icons.Rounded.Edit) { menu = false; onEdit() }
                MenuItem("Renommer", Icons.Rounded.Draw) { menu = false; onRename() }
                MenuItem("Informations", Icons.Rounded.Info) { menu = false; onInfo() }
                MenuItem("Ajouter les altitudes", Icons.Rounded.CloudDownload) { menu = false; onElevation() }
                HorizontalDivider()
                MenuItem("Partager", Icons.Rounded.Share) { menu = false; onShare() }
                MenuItem("Enregistrer sous…", Icons.Rounded.SaveAlt) { menu = false; onExport() }
                HorizontalDivider()
                MenuItem("Supprimer", Icons.Rounded.Delete, destructive = true) { menu = false; onDelete() }
            }
        }
    }
}

@Composable
private fun EditTopBar(vm: AppViewModel, onCancel: () -> Unit, onDone: () -> Unit, onInfo: () -> Unit) {
    val doc = vm.doc
    var lines by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassIconButton(Icons.Rounded.Close, "Annuler", onClick = onCancel, size = 52.dp)
        Box(Modifier.weight(1f)) {
            GlassSurface(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                cornerRadius = 26.dp,
                onClick = { lines = true },
                pressedScale = 1.04f,
                contentAlignment = Alignment.CenterStart,
                semanticLabel = "Choisir la ligne à modifier",
            ) {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    Text("Modification" + if (vm.dirty) " •" else "", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    val active = vm.activeLine
                    Text(
                        if (doc != null && active != null) AppViewModel.lineName(doc, active) + " · " + Format.count(GpxEdit.points(doc, active).size, "point") else "Touchez la carte",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            DropdownMenu(expanded = lines, onDismissRequest = { lines = false }, shape = RoundedCornerShape(20.dp)) {
                if (doc != null) {
                    GpxEdit.lineRefs(doc).forEach { ref ->
                        MenuItem(
                            AppViewModel.lineName(doc, ref) + " (${GpxEdit.points(doc, ref).size})",
                            if (ref is LineRef.Route) Icons.Rounded.Route else Icons.Rounded.Timeline,
                        ) { lines = false; vm.activeLine = ref }
                    }
                }
                HorizontalDivider()
                MenuItem("Nouvelle section", Icons.Rounded.Add) { lines = false; vm.newSegment() }
                MenuItem("Nouvelle trace", Icons.Rounded.Polyline) { lines = false; vm.newTrack() }
                MenuItem("Informations du fichier", Icons.Rounded.Info) { lines = false; onInfo() }
            }
        }
        GlassIconButton(
            Icons.Rounded.Check, "Enregistrer", onClick = onDone, size = 52.dp,
            style = GlassDefaults.tinted(), tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun EditToolbar(vm: AppViewModel, onSimplify: () -> Unit, onLines: () -> Unit) {
    var more by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val hint = if (vm.handlesHidden && vm.tool != EditTool.DRAW && vm.tool != EditTool.WAYPOINT) "Zoomez pour faire apparaître les points" else vm.tool.hint
        AnimatedContent(
            targetState = hint,
            transitionSpec = {
                (fadeIn() + scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f), initialScale = 0.9f)).togetherWith(fadeOut())
            },
            modifier = Modifier.align(Alignment.CenterHorizontally),
            label = "hint",
        ) { text ->
            GlassSurface(cornerRadius = 20.dp, interactive = false, style = GlassDefaults.style().copy(shadow = false)) {
                Text(text, Modifier.padding(horizontal = 16.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
        LiquidTabs(
            tabs = listOf(
                LiquidTab(EditTool.DRAW.label, Icons.Rounded.Draw),
                LiquidTab(EditTool.MOVE.label, Icons.Rounded.OpenWith),
                LiquidTab(EditTool.ERASE.label, Icons.AutoMirrored.Rounded.Backspace),
                LiquidTab(EditTool.WAYPOINT.label, Icons.Rounded.AddLocationAlt),
            ),
            selected = vm.tool.ordinal,
            onSelect = { vm.tool = EditTool.entries[it] },
            modifier = Modifier.fillMaxWidth(),
            height = 54.dp,
        )
        GlassBar(Modifier.fillMaxWidth(), height = 64.dp) {
            BarButton(Icons.AutoMirrored.Rounded.Undo, "Annuler l'action", onClick = vm::undo, enabled = vm.canUndo)
            BarButton(Icons.AutoMirrored.Rounded.Redo, "Rétablir", onClick = vm::redo, enabled = vm.canRedo)
            BarButton(
                when (vm.routingProfile) {
                    RoutingProfile.FOOT -> Icons.AutoMirrored.Rounded.DirectionsWalk
                    RoutingProfile.BIKE -> Icons.AutoMirrored.Rounded.DirectionsBike
                    RoutingProfile.CAR -> Icons.Rounded.DirectionsCar
                }.let { if (vm.snapToPaths) it else Icons.Rounded.Route },
                "Suivre les chemins",
                onClick = vm::toggleSnap,
                selected = vm.snapToPaths,
            )
            BarButton(Icons.Rounded.SwapHoriz, "Inverser le sens", onClick = vm::reverseActive, enabled = vm.activeLine != null)
            Box {
                BarButton(Icons.Rounded.Add, "Ajouter une ligne", onClick = { add = true })
                DropdownMenu(expanded = add, onDismissRequest = { add = false }, shape = RoundedCornerShape(20.dp)) {
                    MenuItem("Nouvelle section", Icons.Rounded.Add) { add = false; vm.newSegment() }
                    MenuItem("Nouvelle trace", Icons.Rounded.Polyline) { add = false; vm.newTrack() }
                }
            }
            Box {
                BarButton(Icons.Rounded.MoreHoriz, "Plus d'outils", onClick = { more = true })
                DropdownMenu(expanded = more, onDismissRequest = { more = false }, shape = RoundedCornerShape(20.dp)) {
                    Text("Suivre les chemins", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    RoutingProfile.entries.forEach { p ->
                        MenuItem(
                            p.label + if (vm.snapToPaths && vm.routingProfile == p) "  ✓" else "",
                            when (p) {
                                RoutingProfile.FOOT -> Icons.AutoMirrored.Rounded.DirectionsWalk
                                RoutingProfile.BIKE -> Icons.AutoMirrored.Rounded.DirectionsBike
                                RoutingProfile.CAR -> Icons.Rounded.DirectionsCar
                            },
                        ) { more = false; vm.selectProfile(p) }
                    }
                    HorizontalDivider()
                    MenuItem("Ajouter les altitudes", Icons.Rounded.CloudDownload, enabled = vm.busy == null) { more = false; vm.fillElevations() }
                    MenuItem("Simplifier…", Icons.Rounded.AutoFixHigh) { more = false; onSimplify() }
                    MenuItem("Lignes et repères", Icons.Rounded.ViewList) { more = false; onLines() }
                    val active = vm.activeLine
                    if (active != null) {
                        HorizontalDivider()
                        MenuItem(if (active is LineRef.Route) "Convertir en trace" else "Convertir en itinéraire", Icons.Rounded.Route) { more = false; vm.convertLine(active) }
                        MenuItem("Supprimer la ligne active", Icons.Rounded.Delete, destructive = true) { more = false; vm.deleteLine(active) }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPanel(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    tab: Int,
    onTab: (Int) -> Unit,
    stats: GpxStats,
    profile: ElevationProfile?,
    vm: AppViewModel,
    onLineClick: (LineRef) -> Unit,
    onLineLongClick: (LineRef) -> Unit,
    onWaypointClick: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val bounce = rememberBounceState()
    val doc = vm.doc ?: return
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, drag.value.roundToInt()) },
        cornerRadius = 34.dp,
        style = GlassDefaults.panel(),
        interactive = false,
        bounceState = bounce,
    ) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
            // Poignée : toucher ou glisser pour replier / déplier, avec un rebond élastique.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .pointerInput(expanded) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                if (total > 60f && expanded) onExpandedChange(false)
                                else if (total < -60f && !expanded) onExpandedChange(true)
                                bounce.wobble(0.03f)
                                scope.launch { drag.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 400f)) }
                            },
                            onDragCancel = { scope.launch { drag.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 400f)) } },
                        ) { change, dy ->
                            change.consume()
                            total += dy
                            scope.launch { drag.snapTo(rubber(total)) }
                        }
                    }
                    .androidxClickable { onExpandedChange(!expanded); bounce.wobble(0.025f) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 40.dp, height = 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
            }
            AnimatedContent(
                targetState = expanded,
                transitionSpec = {
                    fadeIn().togetherWith(fadeOut()).using(SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.62f, stiffness = 380f) })
                },
                label = "panel",
            ) { open ->
                if (!open) {
                    CompactSummary(stats, Modifier.fillMaxWidth().padding(bottom = 4.dp))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LiquidTabs(
                            tabs = listOf(
                                LiquidTab("Résumé", Icons.Rounded.Info),
                                LiquidTab("Profil", Icons.AutoMirrored.Rounded.ShowChart),
                                LiquidTab("Éléments", Icons.Rounded.ViewList),
                            ),
                            selected = tab,
                            onSelect = onTab,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = {
                                val dir = if (targetState > initialState) 1 else -1
                                (fadeIn() + androidx.compose.animation.slideInHorizontally(spring(dampingRatio = 0.7f, stiffness = 380f)) { it / 4 * dir })
                                    .togetherWith(fadeOut() + androidx.compose.animation.slideOutHorizontally { -it / 4 * dir })
                                    .using(SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.65f, stiffness = 380f) })
                            },
                            label = "tab",
                        ) { t ->
                            when (t) {
                                0 -> StatsGrid(stats)
                                1 -> ProfileTab(profile, vm) { vm.highlight = it }
                                else -> ElementsTab(doc, vm.activeLine, vm.editing, onLineClick, onLineLongClick, onWaypointClick)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun rubber(d: Float): Float {
    val max = 90f
    return kotlin.math.sign(d) * max * (1f - kotlin.math.exp(-kotlin.math.abs(d) / (max * 1.6f)))
}

@Composable
private fun WaypointCard(name: String, details: List<String>, onCenter: () -> Unit, onClose: () -> Unit) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        cornerRadius = 28.dp,
        style = GlassDefaults.panel(),
        onClick = onCenter,
        pressedScale = 1.03f,
        semanticLabel = "Centrer sur $name",
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Place, null, tint = MaterialTheme.colorScheme.onTertiaryContainer) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                details.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            GlassIconButton(Icons.Rounded.Close, "Fermer", onClick = onClose, size = 40.dp, style = GlassDefaults.style().copy(shadow = false))
        }
    }
}

@Composable
private fun SimplifyDialog(pointCount: Int, preview: (Double) -> Int, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var tol by remember { mutableFloatStateOf(5f) }
    val remaining = remember(tol) { preview(tol.toDouble()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AutoFixHigh, null) },
        title = { Text("Simplifier la trace") },
        text = {
            Column {
                Text("Supprime les points superflus en gardant la forme du parcours (tolérance ${tol.roundToInt()} m).")
                Spacer(Modifier.height(12.dp))
                Slider(value = tol, onValueChange = { tol = it }, valueRange = 1f..50f)
                Text(
                    "$remaining points conservés sur $pointCount",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(tol.toDouble()) }) { Text("Simplifier") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun LineActionsDialog(
    title: String,
    isRoute: Boolean,
    editing: Boolean,
    onRename: () -> Unit,
    onSelect: () -> Unit,
    onReverse: () -> Unit,
    onConvert: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (isRoute) Icons.Rounded.Route else Icons.Rounded.Timeline, null) },
        title = { Text(title) },
        text = {
            Column {
                if (editing) MenuItem("Modifier cette ligne", Icons.Rounded.Edit, onClick = onSelect)
                MenuItem("Renommer", Icons.Rounded.Draw, onClick = onRename)
                MenuItem("Inverser le sens", Icons.Rounded.SwapHoriz, onClick = onReverse)
                MenuItem(if (isRoute) "Convertir en trace" else "Convertir en itinéraire", Icons.Rounded.Route, onClick = onConvert)
                MenuItem("Supprimer", Icons.Rounded.Delete, destructive = true, onClick = onDelete)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

private fun Modifier.androidxClickable(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
