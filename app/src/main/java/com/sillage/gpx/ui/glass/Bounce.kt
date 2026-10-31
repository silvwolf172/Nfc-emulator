package com.sillage.gpx.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

/**
 * État « gélatineux » d'un élément de verre : il gonfle sous le doigt, s'étire dans la direction du geste
 * et rebondit en revenant au repos.
 */
@Stable
class BounceState internal constructor(private val scope: CoroutineScope) {
    internal val press = Animatable(0f)
    internal val scale = Animatable(1f)
    internal val pullX = Animatable(0f)
    internal val pullY = Animatable(0f)

    /** Position du doigt (coordonnées locales), pour le reflet lumineux. */
    var touch by mutableStateOf(Offset.Unspecified)
        internal set

    val pressProgress: Float get() = press.value

    internal fun onPress(position: Offset, targetScale: Float) {
        touch = position
        scope.launch { press.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 900f)) }
        scope.launch { scale.animateTo(targetScale, spring(dampingRatio = 0.55f, stiffness = 520f)) }
    }

    internal fun onDrag(position: Offset, delta: Offset, maxPull: Float) {
        touch = position
        scope.launch { pullX.snapTo(rubberBand(delta.x, maxPull)) }
        scope.launch { pullY.snapTo(rubberBand(delta.y, maxPull)) }
    }

    internal fun onRelease() {
        val bouncy = spring<Float>(dampingRatio = 0.32f, stiffness = 340f)
        scope.launch { press.animateTo(0f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) }
        scope.launch { scale.animateTo(1f, bouncy) }
        scope.launch { pullX.animateTo(0f, bouncy) }
        scope.launch { pullY.animateTo(0f, bouncy) }
    }

    /** Petit coup de « gelée » déclenché par programme (sélection, appui long…). */
    fun wobble(amount: Float = 0.08f) {
        scope.launch {
            scale.snapTo(scale.value + amount)
            scale.animateTo(1f, spring(dampingRatio = 0.28f, stiffness = 420f))
        }
    }

    private fun rubberBand(d: Float, max: Float): Float =
        if (max <= 0f) 0f else sign(d) * max * (1f - exp(-abs(d) / (max * 2.2f)))
}

@Composable
fun rememberBounceState(): BounceState {
    val scope = rememberCoroutineScope()
    return remember(scope) { BounceState(scope) }
}

/**
 * Applique la déformation élastique (échelle + étirement + léger suivi du doigt).
 * [stretch] règle l'intensité de l'étirement directionnel.
 */
fun Modifier.bounceTransform(state: BounceState, stretch: Float = 1f): Modifier = graphicsLayer {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val px = state.pullX.value
    val py = state.pullY.value
    val ax = (abs(px) / w * 2f).coerceAtMost(1f) * stretch
    val ay = (abs(py) / h * 2f).coerceAtMost(1f) * stretch
    val s = state.scale.value
    scaleX = s * (1f + 0.16f * ax - 0.07f * ay)
    scaleY = s * (1f + 0.16f * ay - 0.07f * ax)
    translationX = px
    translationY = py
}

/**
 * Détecte les appuis sans bloquer le défilement des listes parentes : si un parent consomme le geste
 * (défilement), l'appui est annulé et l'élément revient en rebondissant.
 */
fun Modifier.bounceClickable(
    state: BounceState,
    enabled: Boolean = true,
    pressedScale: Float = 1.08f,
    maxPull: Float = 28f,
    haptics: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)?,
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    pointerInput(enabled, pressedScale, maxPull) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val start = down.position
            state.onPress(start, pressedScale)
            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            var longPressed = false
            var longPressJob: Job? = null
            if (currentOnLongClick != null) {
                longPressJob = scope.launch {
                    delay(viewConfiguration.longPressTimeoutMillis)
                    longPressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.wobble(0.05f)
                    currentOnLongClick?.invoke()
                }
            }
            var clicked = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUpIgnoreConsumed()) {
                    val p = change.position
                    val slack = 24f * density
                    val inside = p.x >= -slack && p.y >= -slack &&
                        p.x <= size.width + slack && p.y <= size.height + slack
                    clicked = inside && !longPressed && !change.isConsumed
                    break
                }
                if (change.isConsumed) break
                // Un parent (liste, pager…) a-t-il pris la main ?
                val finalPass = awaitPointerEvent(PointerEventPass.Final)
                if (finalPass.changes.any { it.id == down.id && it.isConsumed }) break
                val delta = change.position - start
                if (delta.getDistance() > viewConfiguration.touchSlop) longPressJob?.cancel()
                state.onDrag(change.position, delta, maxPull)
            }
            longPressJob?.cancel()
            state.onRelease()
            if (clicked) currentOnClick?.invoke()
        }
    }
}

