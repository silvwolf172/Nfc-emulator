package com.sillage.gpx.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

/**
 * Ce qui se trouve « derrière » le verre. Le contenu source (carte, fond animé…) est enregistré dans un
 * [GraphicsLayer] ; chaque surface de verre le redessine ensuite, floutée et réfractée, à sa propre position.
 *
 * Le calque est une RenderNode : quand la source est redessinée (la carte bouge), toutes les surfaces de
 * verre qui la référencent sont mises à jour sans être réenregistrées.
 */
@Stable
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    /** Position de la source dans la racine de la fenêtre. */
    internal var origin by mutableStateOf(Offset.Zero)

    /** Incrémenté à chaque enregistrement de la source (écriture seule, sans lecture observée). */
    private val versionState = mutableIntStateOf(0)
    private var counter = 0
    internal val version: Int get() = versionState.intValue

    internal fun bump() {
        counter++
        versionState.intValue = counter
    }
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** Marque ce composable comme source du verre. Les surfaces de verre ne doivent pas en faire partie. */
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
        backdrop.bump()
    }

val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }
