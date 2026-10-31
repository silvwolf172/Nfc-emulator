package com.sillage.gpx.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Silhouette de la trace à partir de l'aperçu normalisé de la bibliothèque. */
@Composable
fun TrackThumbnail(preview: List<FloatArray>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val start = Color(0xFF2FB36E)
    val end = Color(0xFFE5484D)
    Canvas(modifier) {
        val pad = 10.dp.toPx()
        val s = minOf(size.width, size.height) - pad * 2
        val ox = (size.width - s) / 2
        val oy = (size.height - s) / 2
        preview.forEach { arr ->
            if (arr.size < 4) return@forEach
            val path = Path()
            path.moveTo(ox + arr[0] * s, oy + arr[1] * s)
            var i = 2
            while (i < arr.size) { path.lineTo(ox + arr[i] * s, oy + arr[i + 1] * s); i += 2 }
            drawPath(path, Color.White.copy(alpha = 0.7f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, color, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        preview.firstOrNull()?.takeIf { it.size >= 2 }?.let { drawCircle(start, 3.2.dp.toPx(), Offset(ox + it[0] * s, oy + it[1] * s)) }
        preview.lastOrNull()?.takeIf { it.size >= 2 }?.let {
            drawCircle(end, 3.2.dp.toPx(), Offset(ox + it[it.size - 2] * s, oy + it[it.size - 1] * s))
        }
    }
}
