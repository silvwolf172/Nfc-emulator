package com.sillage.gpx.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sillage.gpx.gpx.ElevationProfile
import com.sillage.gpx.util.Format
import kotlinx.coroutines.launch

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Profil altimétrique : on le parcourt du doigt, et le point correspondant est indiqué sur la carte.
 * La bulle d'information rebondit légèrement quand elle apparaît.
 */
@Composable
fun ElevationChart(
    profile: ElevationProfile,
    modifier: Modifier = Modifier,
    onScrub: (Int?) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentOnScrub by rememberUpdatedState(onScrub)
    var index by remember(profile) { mutableIntStateOf(-1) }
    val bubble = remember { Animatable(0f) }
    val appear = remember(profile) { Animatable(0f) }
    LaunchedEffect(profile) { appear.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 120f)) }

    // Lissage léger pour l'affichage (le calcul des stats utilise les données brutes).
    val smooth = remember(profile) { smoothed(profile.elevations, 3) }
    val minE = remember(profile) { floor(smooth.minOrNull() ?: 0.0) }
    val maxE = remember(profile) { ceil(smooth.maxOrNull() ?: 1.0) }

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(profile) {
                    val left = 40.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun update(x: Float) {
                            val w = size.width - left
                            val t = ((x - left) / w).coerceIn(0f, 1f)
                            val i = profile.indexAt(t * profile.totalDistance)
                            if (i != index) {
                                index = i
                                currentOnScrub(i)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                        down.consume()
                        scope.launch { bubble.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f)) }
                        update(down.position.x)
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUpIgnoreConsumed()) break
                            change.consume()
                            update(change.position.x)
                        }
                        scope.launch { bubble.animateTo(0f, spring(dampingRatio = 1f, stiffness = 400f)) }
                        index = -1
                        currentOnScrub(null)
                    }
                },
        ) {
            val left = 40.dp.toPx()
            val bottom = 18.dp.toPx()
            val top = 8.dp.toPx()
            val w = size.width - left
            val h = size.height - bottom - top
            val range = max(1.0, maxE - minE)
            val total = max(1.0, profile.totalDistance)
            fun x(d: Double) = left + (d / total * w).toFloat()
            fun y(e: Double) = top + h - ((e - minE) / range * h).toFloat() * appear.value

            // Grille
            val labelStyle = TextStyle(color = cs.onSurfaceVariant, fontSize = 10.sp)
            for (k in 0..2) {
                val e = minE + range * k / 2
                val yy = top + h - (h * k / 2f)
                drawLine(cs.outlineVariant.copy(alpha = 0.6f), Offset(left, yy), Offset(size.width, yy), 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                drawLabel(measurer, Format.elevation(e), Offset(0f, yy - 7.dp.toPx()), labelStyle)
            }
            drawLabel(measurer, "0", Offset(left, size.height - bottom + 3.dp.toPx()), labelStyle)
            val totalLabel = Format.distance(profile.totalDistance)
            val tl = measurer.measure(totalLabel, labelStyle)
            drawText(tl, topLeft = Offset(size.width - tl.size.width, size.height - bottom + 3.dp.toPx()))

            // Courbe + remplissage
            val line = Path()
            val step = max(1, profile.size / 600)
            var first = true
            var i = 0
            while (i < profile.size) {
                val px = x(profile.distances[i])
                val py = y(smooth[i])
                if (first) { line.moveTo(px, py); first = false } else line.lineTo(px, py)
                i = if (i + step >= profile.size && i != profile.size - 1) profile.size - 1 else i + step
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(x(profile.totalDistance), top + h)
                lineTo(left, top + h)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(cs.primary.copy(alpha = 0.45f), cs.primary.copy(alpha = 0.02f)), startY = top, endY = top + h))
            drawPath(line, cs.primary, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Curseur
            val idx = index
            if (idx in 0 until profile.size) {
                val px = x(profile.distances[idx])
                val py = y(smooth[idx])
                drawLine(cs.onSurface.copy(alpha = 0.5f), Offset(px, top), Offset(px, top + h), 1.5f)
                drawCircle(cs.surface, 7.dp.toPx(), Offset(px, py))
                drawCircle(cs.primary, 5.dp.toPx(), Offset(px, py))

                val b = bubble.value
                val text = "${Format.elevation(profile.elevations[idx])} · ${Format.distance(profile.distances[idx])}"
                val layout = measurer.measure(text, TextStyle(color = cs.onPrimary, fontSize = 12.sp))
                val bw = layout.size.width + 16.dp.toPx()
                val bh = layout.size.height + 8.dp.toPx()
                val bx = (px - bw / 2).coerceIn(left, size.width - bw)
                val by = (py - bh - 12.dp.toPx()).coerceAtLeast(0f)
                val scale = 0.6f + 0.4f * b
                val cx = bx + bw / 2
                val cy = by + bh / 2
                drawRoundRect(
                    cs.primary.copy(alpha = b.coerceIn(0f, 1f)),
                    topLeft = Offset(cx - bw * scale / 2, cy - bh * scale / 2),
                    size = Size(bw * scale, bh * scale),
                    cornerRadius = CornerRadius(bh / 2),
                )
                if (b > 0.5f) drawText(layout, topLeft = Offset(bx + 8.dp.toPx(), by + 4.dp.toPx()), alpha = ((b - 0.5f) * 2).coerceIn(0f, 1f))
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabel(
    measurer: TextMeasurer, text: String, topLeft: Offset, style: TextStyle,
) {
    drawText(measurer.measure(text, style), topLeft = topLeft)
}

private fun smoothed(values: DoubleArray, radius: Int): DoubleArray {
    if (values.size < 3) return values
    val out = DoubleArray(values.size)
    var sum = 0.0
    var count = 0
    var lo = 0
    var hi = -1
    for (i in values.indices) {
        val a = max(0, i - radius)
        val b = minOf(values.lastIndex, i + radius)
        while (hi < b) { hi++; sum += values[hi]; count++ }
        while (lo < a) { sum -= values[lo]; lo++; count-- }
        out[i] = sum / count
    }
    return out
}

