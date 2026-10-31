package com.sillage.gpx.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.sillage.gpx.ui.theme.LocalDarkTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fond vivant aux couleurs Material You : des taches de couleur dérivent lentement, l'une suit le doigt,
 * et des courbes de niveau rappellent une carte topographique. C'est ce fond que le verre réfracte.
 */
@Composable
fun LiquidBackground(touch: Offset?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val transition = rememberInfiniteTransition(label = "liquid")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val follower = remember { Animatable(Offset(-1f, -1f), Offset.VectorConverter) }
    LaunchedEffect(touch) {
        if (touch != null) {
            if (follower.value.x < 0f) follower.snapTo(touch)
            follower.animateTo(touch, spring(dampingRatio = 0.45f, stiffness = 60f))
        }
    }
    val a = if (dark) 0.55f else 0.85f
    val blobs = listOf(
        Blob(cs.primaryContainer.copy(alpha = a), 0.15f, 0.12f, 0.75f, 0.0f),
        Blob(cs.tertiaryContainer.copy(alpha = a), 0.95f, 0.30f, 0.70f, 0.33f),
        Blob(cs.secondaryContainer.copy(alpha = a), 0.20f, 0.62f, 0.80f, 0.66f),
        Blob(cs.primary.copy(alpha = if (dark) 0.30f else 0.32f), 0.85f, 0.85f, 0.65f, 0.15f),
        Blob(cs.tertiary.copy(alpha = if (dark) 0.25f else 0.24f), 0.45f, 0.40f, 0.45f, 0.5f),
    )
    val contour = cs.onSurface.copy(alpha = if (dark) 0.07f else 0.075f)

    Canvas(modifier) {
        drawRect(cs.surface)
        val w = size.width
        val h = size.height
        val phase = t * 2f * PI.toFloat()
        blobs.forEach { b ->
            val p = phase + b.phase * 2f * PI.toFloat()
            val c = Offset(w * (b.x + 0.16f * sin(p)), h * (b.y + 0.08f * cos(p * 1.3f)))
            val r = w * b.r
            drawCircle(Brush.radialGradient(listOf(b.color, b.color.copy(alpha = 0f)), center = c, radius = r), radius = r, center = c)
        }
        val f = follower.value
        if (f.x >= 0f) {
            val r = w * 0.42f
            val col = cs.primary.copy(alpha = if (dark) 0.35f else 0.30f)
            drawCircle(Brush.radialGradient(listOf(col, col.copy(alpha = 0f)), center = f, radius = r), radius = r, center = f)
        }
        // Courbes de niveau
        val stroke = Stroke(width = 1.2.dp.toPx())
        val lines = 14
        for (k in 0 until lines) {
            val baseY = h * (k + 0.5f) / lines
            val path = Path()
            val steps = 48
            for (s in 0..steps) {
                val x = w * s / steps
                val u = x / w * 2f * PI.toFloat()
                val y = baseY + 18.dp.toPx() * sin(u * 1.5f + phase + k * 0.6f) + 10.dp.toPx() * cos(u * 2.7f - phase * 0.7f + k)
                if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, contour, style = stroke)
        }
    }
}

private class Blob(val color: Color, val x: Float, val y: Float, val r: Float, val phase: Float)
