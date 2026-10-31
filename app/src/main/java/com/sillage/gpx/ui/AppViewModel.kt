package com.sillage.gpx.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sillage.gpx.data.GpxEntry
import com.sillage.gpx.data.GpxRepository
import com.sillage.gpx.data.OnlineServices
import com.sillage.gpx.data.RoutingProfile
import com.sillage.gpx.gpx.GpxDocument
import com.sillage.gpx.gpx.GpxEdit
import com.sillage.gpx.gpx.GpxPoint
import com.sillage.gpx.gpx.LineRef
import com.sillage.gpx.gpx.PointRef
import com.sillage.gpx.ui.map.GpxOverlay
import com.sillage.gpx.ui.map.MapStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class EditTool(val label: String, val hint: String) {
    DRAW("Tracer", "Touchez la carte pour ajouter des points"),
    MOVE("Ajuster", "Glissez un point · touchez la ligne pour insérer"),
    ERASE("Gommer", "Touchez un point ou un repère pour l'effacer"),
    WAYPOINT("Repère", "Touchez la carte pour poser un repère"),
}

sealed interface Screen {
    data object Library : Screen
    data class Viewer(val id: String) : Screen
}

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class Share(val uri: Uri, val name: String) : UiEvent
}

/** Repère en cours de création ou de modification. */
data class WaypointDraft(val index: Int?, val point: GpxPoint)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = GpxRepository(app)
    private val prefs = app.getSharedPreferences("sillage", Context.MODE_PRIVATE)

    private val events = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvents = events.receiveAsFlow()

    // ------------------------------------------------------------------ Bibliothèque
    var entries by mutableStateOf<List<GpxEntry>>(emptyList()); private set
    var libraryLoading by mutableStateOf(true); private set
    var query by mutableStateOf("")

    // ------------------------------------------------------------------ Navigation
    var screen by mutableStateOf<Screen>(Screen.Library); private set

    // ------------------------------------------------------------------ Document ouvert
    var openId by mutableStateOf<String?>(null); private set
    var doc by mutableStateOf<GpxDocument?>(null); private set
    var docLoading by mutableStateOf(false); private set
    var selectedWaypoint by mutableStateOf<Int?>(null)
    var highlight by mutableStateOf<GpxPoint?>(null)

    // ------------------------------------------------------------------ Édition
    var editing by mutableStateOf(false); private set
    var tool by mutableStateOf(EditTool.DRAW)
    var activeLine by mutableStateOf<LineRef?>(null)
    var dirty by mutableStateOf(false); private set
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set
    var handlesHidden by mutableStateOf(false)
    var waypointDraft by mutableStateOf<WaypointDraft?>(null)
    var busy by mutableStateOf<String?>(null); private set

    var snapToPaths by mutableStateOf(prefs.getBoolean("snap", false))
        private set
    var routingProfile by mutableStateOf(
        runCatching { RoutingProfile.valueOf(prefs.getString("profile", null) ?: "") }.getOrDefault(RoutingProfile.FOOT),
    )
        private set
    var mapStyle by mutableStateOf(
        runCatching { MapStyle.valueOf(prefs.getString("mapStyle", null) ?: "") }.getOrDefault(MapStyle.AUTO),
    )
        private set

    private var original: GpxDocument? = null
    private val undoStack = ArrayDeque<GpxDocument>()
    private val redoStack = ArrayDeque<GpxDocument>()
    private var routingJob: Job? = null
    /** Fichier à ouvrir en mode édition dès son chargement (nouvelle trace). */
    private var editOnOpen = false

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            entries = runCatching { repo.list() }.getOrDefault(emptyList())
            libraryLoading = false
        }
    }

    fun message(text: String) { events.trySend(UiEvent.Message(text)) }

    // ------------------------------------------------------------------ Actions bibliothèque

    fun importUris(uris: List<Uri>, openSingle: Boolean = true) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var ok = 0
            var lastId: String? = null
            val errors = ArrayList<String>()
            for (uri in uris) {
                runCatching { repo.import(uri) }
                    .onSuccess { ok++; lastId = it }
                    .onFailure { errors.add(it.message ?: "Fichier illisible") }
            }
            refresh()
            when {
                // On n'interrompt pas une édition en cours pour ouvrir le fichier reçu.
                ok == 1 && uris.size == 1 && openSingle && !(editing && dirty) -> lastId?.let { open(it) }
                ok > 0 -> message(if (ok == 1) "1 fichier importé" else "$ok fichiers importés")
            }
            if (errors.isNotEmpty()) message(errors.first())
        }
    }

    fun createNew(name: String) {
        viewModelScope.launch {
            val id = repo.create(name.ifBlank { "Nouvelle trace" })
            refresh()
            editOnOpen = true
            open(id)
        }
    }

    fun openForEdit(id: String) {
        editOnOpen = true
        open(id)
    }

    fun loadSample() {
        viewModelScope.launch {
            val assets = getApplication<Application>().assets
            val names = assets.list("samples").orEmpty().filter { it.endsWith(".gpx") }
            var count = 0
            for (n in names) {
                val text = assets.open("samples/$n").bufferedReader().use { it.readText() }
                val name = runCatching { com.sillage.gpx.gpx.GpxParser.parse(text).displayName }.getOrNull() ?: n.removeSuffix(".gpx")
                runCatching { repo.importText(name, text) }.onSuccess { count++ }
            }
            refresh()
            if (count > 0) message("Exemples ajoutés")
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repo.delete(id)
            if (openId == id) closeViewer(force = true)
            refresh()
            message("Fichier supprimé")
        }
    }

    fun rename(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { repo.rename(id, name.trim()) }.onFailure { message(it.message ?: "Erreur") }
            if (openId == id && !editing) doc = repo.load(id)
            else if (openId == id) doc?.let { d -> doc = d.copy(metadata = d.metadata.copy(name = name.trim())); dirty = true }
            refresh()
        }
    }

    fun duplicate(id: String) {
        viewModelScope.launch {
            runCatching { repo.duplicate(id) }.onSuccess { message("Copie créée") }.onFailure { message(it.message ?: "Erreur") }
            refresh()
        }
    }

    fun share(id: String) {
        viewModelScope.launch {
            val name = entries.firstOrNull { it.id == id }?.name ?: id.removeSuffix(".gpx")
            runCatching { repo.shareUri(id, name) }
                .onSuccess { events.send(UiEvent.Share(it, name)) }
                .onFailure { message(it.message ?: "Partage impossible") }
        }
    }

    fun exportTo(id: String, uri: Uri) {
        viewModelScope.launch {
            runCatching { repo.exportTo(id, uri) }
                .onSuccess { message("Fichier enregistré") }
                .onFailure { message(it.message ?: "Enregistrement impossible") }
        }
    }

    fun displayName(id: String): String =
        entries.firstOrNull { it.id == id }?.name ?: id.removeSuffix(".gpx")

    // ------------------------------------------------------------------ Lecture

    fun open(id: String) {
        openId = id
        screen = Screen.Viewer(id)
        doc = null
        docLoading = true
        selectedWaypoint = null
        highlight = null
        editing = false
        viewModelScope.launch {
            runCatching { repo.load(id) }
                .onSuccess {
                    doc = it
                    if (editOnOpen) { editOnOpen = false; startEditing() }
                }
                .onFailure {
                    message(it.message ?: "Fichier illisible")
                    screen = Screen.Library
                }
            docLoading = false
        }
    }

    /** Affiche directement un document déjà chargé (tests). */
    @androidx.annotation.VisibleForTesting
    internal fun showLoaded(id: String, document: GpxDocument) {
        openId = id
        screen = Screen.Viewer(id)
        doc = document
        docLoading = false
        selectedWaypoint = null
        highlight = null
        editing = false
    }

    /** Ferme la visionneuse ; renvoie false si des modifications non enregistrées bloquent. */
    fun closeViewer(force: Boolean = false): Boolean {
        if (editing && dirty && !force) return false
        editing = false
        screen = Screen.Library
        openId = null
        doc = null
        highlight = null
        selectedWaypoint = null
        clearHistory()
        return true
    }

    fun changeMapStyle(style: MapStyle) {
        mapStyle = style
        prefs.edit().putString("mapStyle", style.name).apply()
    }

    // ------------------------------------------------------------------ Édition

    fun startEditing() {
        val d = doc ?: return
        original = d
        clearHistory()
        val (withLine, line) = GpxEdit.ensureLine(d, activeLine)
        doc = withLine
        activeLine = defaultActiveLine(withLine) ?: line
        editing = true
        dirty = withLine !== d
        selectedWaypoint = null
        highlight = null
        tool = EditTool.DRAW
    }

    private fun defaultActiveLine(d: GpxDocument): LineRef? =
        GpxEdit.lineRefs(d).maxByOrNull { GpxEdit.points(d, it).size }

    fun finishEditing() {
        val id = openId ?: return
        val d = doc ?: return
        routingJob?.cancel()
        if (!dirty) { editing = false; clearHistory(); return }
        viewModelScope.launch {
            runCatching { repo.save(id, d) }
                .onSuccess {
                    editing = false
                    dirty = false
                    clearHistory()
                    message("Modifications enregistrées")
                    refresh()
                }
                .onFailure { message(it.message ?: "Enregistrement impossible") }
        }
    }

    fun cancelEditing() {
        routingJob?.cancel()
        original?.let { doc = it }
        editing = false
        dirty = false
        clearHistory()
    }

    private fun clearHistory() {
        undoStack.clear(); redoStack.clear()
        canUndo = false; canRedo = false
        busy = null
    }

    private fun apply(newDoc: GpxDocument) {
        val current = doc ?: return
        if (newDoc == current) return
        undoStack.addLast(current)
        if (undoStack.size > 200) undoStack.removeFirst()
        redoStack.clear()
        doc = newDoc
        dirty = true
        canUndo = true
        canRedo = false
        if (!editing) saveNow()
    }

    /** Hors mode édition (ex. ajout d'altitudes), on enregistre directement. */
    private fun saveNow() {
        val id = openId ?: return
        val d = doc ?: return
        viewModelScope.launch {
            runCatching { repo.save(id, d) }.onSuccess { dirty = false; refresh() }
        }
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        doc?.let { redoStack.addLast(it) }
        doc = prev
        dirty = true
        canUndo = undoStack.isNotEmpty()
        canRedo = true
        fixActiveLine()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        doc?.let { undoStack.addLast(it) }
        doc = next
        dirty = true
        canUndo = true
        canRedo = redoStack.isNotEmpty()
        fixActiveLine()
    }

    private fun fixActiveLine() {
        val d = doc ?: return
        val line = activeLine
        if (line == null || !GpxEdit.exists(d, line)) activeLine = defaultActiveLine(d)
    }

    fun toggleSnap() {
        snapToPaths = !snapToPaths
        prefs.edit().putBoolean("snap", snapToPaths).apply()
        message(if (snapToPaths) "Suivi des chemins activé (${routingProfile.label.lowercase()})" else "Tracé en ligne droite")
    }

    fun selectProfile(p: RoutingProfile) {
        routingProfile = p
        prefs.edit().putString("profile", p.name).apply()
        if (!snapToPaths) toggleSnap()
    }

    fun newSegment() {
        val d = doc ?: return
        val track = (activeLine as? LineRef.Track)?.track ?: d.tracks.lastIndex
        val (nd, line) = GpxEdit.newSegment(d, track)
        apply(nd); activeLine = line; tool = EditTool.DRAW
        message("Nouvelle section : touchez la carte pour la tracer")
    }

    fun newTrack() {
        val d = doc ?: return
        val (nd, line) = GpxEdit.newTrack(d, "Trace ${d.tracks.size + 1}")
        apply(nd); activeLine = line; tool = EditTool.DRAW
        message("Nouvelle trace : touchez la carte pour la tracer")
    }

    fun reverseActive() {
        val d = doc ?: return
        val line = activeLine ?: return
        apply(GpxEdit.reverse(d, line))
        message("Sens de parcours inversé")
    }

    fun deleteLine(line: LineRef) {
        val d = doc ?: return
        apply(GpxEdit.deleteLine(d, line))
        fixActiveLine()
    }

    fun renameLine(line: LineRef, name: String) {
        val d = doc ?: return
        apply(when (line) {
            is LineRef.Track -> GpxEdit.renameTrack(d, line.track, name)
            is LineRef.Route -> GpxEdit.renameRoute(d, line.route, name)
        })
    }

    fun convertLine(line: LineRef) {
        val d = doc ?: return
        apply(when (line) {
            is LineRef.Track -> GpxEdit.trackToRoute(d, line.track)
            is LineRef.Route -> GpxEdit.routeToTrack(d, line.route)
        })
        fixActiveLine()
    }

    fun simplify(toleranceM: Double) {
        val d = doc ?: return
        val before = d.lines.sumOf { it.size }
        val nd = GpxEdit.simplifyAll(d, toleranceM)
        val after = nd.lines.sumOf { it.size }
        apply(nd)
        message("${before - after} points supprimés ($after restants)")
    }

    fun fillElevations() {
        val d = doc ?: return
        val missing = GpxEdit.missingElevationPoints(d)
        if (missing.isEmpty()) { message("Tous les points ont déjà une altitude"); return }
        if (missing.size > 20_000) { message("Trop de points (${missing.size}) : simplifiez d'abord la trace"); return }
        viewModelScope.launch {
            busy = "Altitudes… 0 %"
            runCatching {
                OnlineServices.elevations(missing) { p -> busy = "Altitudes… ${(p * 100).toInt()} %" }
            }.onSuccess { map ->
                doc?.let { current -> apply(GpxEdit.withElevations(current, map)) }
                message("${map.size} altitudes ajoutées")
            }.onFailure { message("Altitudes indisponibles : ${it.message}") }
            busy = null
        }
    }

    fun saveWaypoint(draft: WaypointDraft) {
        val d = doc ?: return
        apply(if (draft.index == null) GpxEdit.addWaypoint(d, draft.point) else GpxEdit.updateWaypoint(d, draft.index, draft.point))
        waypointDraft = null
    }

    fun deleteWaypoint(index: Int) {
        val d = doc ?: return
        apply(GpxEdit.deleteWaypoint(d, index))
        waypointDraft = null
        if (selectedWaypoint == index) selectedWaypoint = null
    }

    fun updateMetadata(name: String, desc: String, author: String) {
        val d = doc ?: return
        apply(d.copy(metadata = d.metadata.copy(
            name = name.ifBlank { null }, desc = desc.ifBlank { null }, author = author.ifBlank { null },
        )))
    }

    private fun appendPoint(lat: Double, lon: Double) {
        val d0 = doc ?: return
        val (d, line) = GpxEdit.ensureLine(d0, activeLine)
        activeLine = line
        val target = GpxPoint(lat, lon)
        val last = GpxEdit.points(d, line).lastOrNull()
        if (!snapToPaths || last == null) {
            apply(GpxEdit.append(d, line, listOf(target)))
            return
        }
        if (routingJob?.isActive == true) return
        routingJob = viewModelScope.launch {
            busy = "Recherche du chemin…"
            val result = runCatching { OnlineServices.route(last, target, routingProfile) }
            busy = null
            val current = doc ?: return@launch
            val pts = result.getOrNull()?.drop(1)?.takeIf { it.isNotEmpty() }
            if (pts == null) message("Chemin introuvable, segment tracé en ligne droite")
            apply(GpxEdit.append(current, line, pts ?: listOf(target)))
        }
    }

    /** Réactions aux gestes sur la carte. */
    val mapListener = object : GpxOverlay.Listener {
        override fun onMapTap(lat: Double, lon: Double) {
            if (!editing) { selectedWaypoint = null; return }
            when (tool) {
                EditTool.DRAW -> appendPoint(lat, lon)
                EditTool.WAYPOINT -> waypointDraft = WaypointDraft(null, GpxPoint(lat, lon, name = "Repère ${(doc?.waypoints?.size ?: 0) + 1}"))
                else -> Unit
            }
        }

        override fun onLineTap(line: LineRef, insertIndex: Int, lat: Double, lon: Double) {
            val d = doc ?: return
            when (tool) {
                EditTool.MOVE -> {
                    apply(GpxEdit.insert(d, PointRef(line, insertIndex), GpxPoint(lat, lon)))
                    activeLine = line
                }
                else -> onMapTap(lat, lon)
            }
        }

        override fun onPointTap(ref: PointRef) {
            val d = doc ?: return
            when (tool) {
                EditTool.ERASE -> apply(GpxEdit.delete(d, ref))
                EditTool.DRAW, EditTool.MOVE -> {
                    if (activeLine != ref.line) {
                        activeLine = ref.line
                        message("Ligne sélectionnée : ${lineName(d, ref.line)}")
                    }
                }
                EditTool.WAYPOINT -> Unit
            }
        }

        override fun onWaypointTap(index: Int) {
            val d = doc ?: return
            if (!editing) { selectedWaypoint = index; return }
            when (tool) {
                EditTool.ERASE -> apply(GpxEdit.deleteWaypoint(d, index))
                else -> d.waypoints.getOrNull(index)?.let { waypointDraft = WaypointDraft(index, it) }
            }
        }

        override fun onPointDragged(ref: PointRef, lat: Double, lon: Double) {
            val d = doc ?: return
            apply(GpxEdit.move(d, ref, lat, lon))
            activeLine = ref.line
        }

        override fun onWaypointDragged(index: Int, lat: Double, lon: Double) {
            val d = doc ?: return
            apply(GpxEdit.moveWaypoint(d, index, lat, lon))
        }

        override fun onHandlesHiddenChanged(hidden: Boolean) { handlesHidden = hidden }
    }

    companion object {
        fun lineName(d: GpxDocument, line: LineRef): String = when (line) {
            is LineRef.Track -> {
                val t = d.tracks.getOrNull(line.track)
                val base = t?.name?.takeIf { it.isNotBlank() } ?: "Trace ${line.track + 1}"
                if ((t?.segments?.size ?: 0) > 1) "$base · section ${line.segment + 1}" else base
            }
            is LineRef.Route -> d.routes.getOrNull(line.route)?.name?.takeIf { it.isNotBlank() } ?: "Itinéraire ${line.route + 1}"
        }
    }
}
