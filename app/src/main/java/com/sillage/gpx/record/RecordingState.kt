package com.sillage.gpx.record

import androidx.compose.runtime.Immutable
import com.sillage.gpx.gpx.GpxPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RecordingStatus { IDLE, RECORDING, PAUSED, SAVING }

/** Dernière position reçue (affichage de la qualité du signal). */
@Immutable
data class Fix(
    val lat: Double,
    val lon: Double,
    val accuracyM: Float?,
    val speedMs: Float?,
    val altitudeM: Double?,
    val time: Long,
)

@Immutable
data class RecordingSnapshot(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val name: String = "",
    val startTime: Long? = null,
    val segments: List<List<GpxPoint>> = emptyList(),
    val distanceM: Double = 0.0,
    /** Temps d'enregistrement cumulé hors pauses, avant la période active en cours. */
    val accumulatedMs: Long = 0,
    /** Début de la période active en cours (null en pause). */
    val activeSince: Long? = null,
    val lastFix: Fix? = null,
    val satellitesUsed: Int? = null,
    val satellitesVisible: Int? = null,
) {
    val isActive: Boolean get() = status != RecordingStatus.IDLE
    val pointCount: Int get() = segments.sumOf { it.size }

    fun elapsedMs(now: Long): Long = accumulatedMs + (activeSince?.let { (now - it).coerceAtLeast(0) } ?: 0)
}

sealed interface RecordingEvent {
    data class Saved(val id: String) : RecordingEvent
    data class Message(val text: String) : RecordingEvent
}

/** État partagé entre le service d'enregistrement et l'interface (même processus). */
object RecordingState {
    private val _state = MutableStateFlow(RecordingSnapshot())
    val state: StateFlow<RecordingSnapshot> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RecordingEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<RecordingEvent> = _events.asSharedFlow()

    internal fun update(transform: (RecordingSnapshot) -> RecordingSnapshot) {
        while (true) {
            val prev = _state.value
            if (_state.compareAndSet(prev, transform(prev))) return
        }
    }

    internal fun emit(event: RecordingEvent) { _events.tryEmit(event) }
}

/** Qualité du signal, d'après la précision annoncée par la dernière position. */
enum class SignalQuality(val label: String, val bars: Int) {
    SEARCHING("Recherche du signal…", 0),
    WEAK("Signal faible", 1),
    FAIR("Signal moyen", 2),
    GOOD("Bon signal", 3),
    EXCELLENT("Signal excellent", 4);

    companion object {
        fun of(fix: Fix?, now: Long): SignalQuality {
            if (fix == null || now - fix.time > 15_000) return SEARCHING
            val acc = fix.accuracyM ?: return FAIR
            return when {
                acc <= 5f -> EXCELLENT
                acc <= 10f -> GOOD
                acc <= 25f -> FAIR
                else -> WEAK
            }
        }
    }
}
