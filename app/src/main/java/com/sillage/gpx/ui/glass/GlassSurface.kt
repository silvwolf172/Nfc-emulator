package com.sillage.gpx.ui.glass

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sillage.gpx.ui.theme.LocalDarkTheme
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Réglages optiques d'une surface de verre. */
@Immutable
data class GlassStyle(
    /** Teinte posée sur le verre (souvent la couleur de surface, très transparente). */
    val tint: Color,
    /** Teinte utilisée quand le flou n'est pas disponible (Android < 12). */
    val fallbackTint: Color,
    val blur: Dp = 10.dp,
    /** Déviation maximale de la lumière sur le bord. */
    val refraction: Dp = 16.dp,
    /** Largeur de la zone de bord qui réfracte. */
    val refractionBand: Dp = 22.dp,
    val dispersion: Float = 0.28f,
    val saturation: Float = 1.45f,
    val fresnel: Float = 0.22f,
    /** Intensité du liseré lumineux. */
    val rim: Float = 1f,
    /** Intensité du halo qui suit le doigt. */
    val glow: Float = 1f,
    val shadow: Boolean = true,
)

object GlassDefaults {
    @Composable
    fun style(): GlassStyle {
        val dark = LocalDarkTheme.current
        val surface = MaterialTheme.colorScheme.surface
        return GlassStyle(
            tint = surface.copy(alpha = if (dark) 0.30f else 0.26f),
            fallbackTint = surface.copy(alpha = if (dark) 0.86f else 0.82f),
        )
    }

    /** Verre teinté par la couleur primaire (boutons d'action). */
    @Composable
    fun tinted(color: Color = MaterialTheme.colorScheme.primary, alpha: Float = 0.62f): GlassStyle =
        style().copy(tint = color.copy(alpha = alpha), fallbackTint = color.copy(alpha = 0.94f), saturation = 1.6f)

    /** Verre très clair, pour les grandes surfaces (panneaux). */
    @Composable
    fun panel(): GlassStyle {
        val dark = LocalDarkTheme.current
        val surface = MaterialTheme.colorScheme.surface
        return style().copy(
            tint = surface.copy(alpha = if (dark) 0.46f else 0.44f),
            fallbackTint = surface.copy(alpha = 0.92f),
            blur = 18.dp,
            refraction = 22.dp,
            refractionBand = 30.dp,
        )
    }
}

/**
 * Surface en « verre liquide » : flou et saturation de ce qui est derrière, réfraction de lentille sur les
 * bords (Android 13+), liseré lumineux, halo qui suit le doigt et déformation élastique au toucher.
 *
 * @param cornerRadius rayon des coins ; une grande valeur donne une pilule.
 * @param pressedScale gonflement sous le doigt.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    style: GlassStyle = GlassDefaults.style(),
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    interactive: Boolean = onClick != null || onLongClick != null,
    pressedScale: Float = 1.08f,
    maxPull: Dp = 10.dp,
    stretch: Float = 1f,
    bounceState: BounceState = rememberBounceState(),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    contentAlignment: Alignment = Alignment.Center,
    semanticLabel: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val effectLayer = rememberGraphicsLayer()
    val renderer = remember { GlassRenderer() }
    var position by remember { mutableStateOf(Offset.Zero) }
    val dark = LocalDarkTheme.current

    val click = onClick
    var m = modifier
        .onGloballyPositioned { position = it.positionInRoot() }
    if (interactive) {
        m = m
            .semantics {
                role = Role.Button
                if (click != null) this.onClick(label = semanticLabel) { click(); true }
            }
            .bounceClickable(
                state = bounceState,
                enabled = enabled,
                pressedScale = pressedScale,
                maxPull = with(androidx.compose.ui.platform.LocalDensity.current) { maxPull.toPx() },
                onLongClick = onLongClick,
                onClick = onClick,
            )
    }
    m = m
        .bounceTransform(bounceState, stretch)
        .drawBehind {
            renderer.draw(
                scope = this,
                effectLayer = effectLayer,
                backdrop = backdrop,
                glassPosition = position,
                cornerRadiusPx = cornerRadius.toPx(),
                style = style,
                dark = dark,
                press = bounceState.pressProgress,
                touch = bounceState.touch,
            )
        }

    Box(m, contentAlignment = contentAlignment) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}

/** Dessin du verre, isolé pour mettre en cache formes et effets entre deux images. */
internal class GlassRenderer {
    private val path = Path()
    private var lastSize = IntSize.Zero
    private var lastRadius = -1f
    private var lastKey: List<Any>? = null
    private var lens: Any? = null // LensShader (API 33+), typé Any pour rester chargeable avant Android 13
    private val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    fun draw(
        scope: DrawScope,
        effectLayer: GraphicsLayer,
        backdrop: GlassBackdrop?,
        glassPosition: Offset,
        cornerRadiusPx: Float,
        style: GlassStyle,
        dark: Boolean,
        press: Float,
        touch: Offset,
    ) = with(scope) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@with
        val r = min(cornerRadiusPx, min(w, h) / 2f)
        val intSize = IntSize(ceil(w).toInt(), ceil(h).toInt())
        if (intSize != lastSize || r != lastRadius) {
            path.reset()
            path.addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r)))
            lastSize = intSize
            lastRadius = r
        }

        // 1. Ombre douce, découpée pour ne pas assombrir l'intérieur du verre.
        if (style.shadow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val blur = 14.dp.toPx()
            shadowPaint.color = android.graphics.Color.argb(if (dark) 90 else 46, 0, 0, 0)
            shadowPaint.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                nc.save()
                nc.clipOutPath(path.asAndroidPath())
                val dy = 4.dp.toPx()
                nc.drawRoundRect(0f, dy, w, h + dy, r, r, shadowPaint)
                nc.restore()
            }
        }

        // 2. Ce qui est derrière : flouté, saturé, réfracté.
        val canBlur = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        if (canBlur && backdrop != null) {
            val refraction = style.refraction.toPx()
            val blur = style.blur.toPx()
            val margin = ceil(refraction + blur * 2f + 2f)
            val layerSize = IntSize((intSize.width + 2 * margin).toInt(), (intSize.height + 2 * margin).toInt())
            backdrop.version // relit le verre quand la source est redessinée
            val offset = backdrop.origin - glassPosition
            effectLayer.record(size = layerSize) {
                translate(offset.x + margin, offset.y + margin) {
                    drawLayer(backdrop.layer)
                }
            }
            val key = listOf(intSize, r, style.blur, style.saturation, style.refraction, style.refractionBand, style.dispersion, style.fresnel)
            if (key != lastKey) {
                lastKey = key
                effectLayer.renderEffect = buildEffect(w, h, margin, r, blur, refraction, style.refractionBand.toPx(), style)
            }
            clipPath(path) {
                translate(-margin, -margin) { drawLayer(effectLayer) }
            }
            drawPath(path, style.tint)
        } else {
            drawPath(path, style.fallbackTint)
        }

        // 3. Halo qui suit le doigt.
        if (press > 0.01f && touch != Offset.Unspecified && style.glow > 0f) {
            val radius = max(w, h) * 0.9f
            clipPath(path) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = (if (dark) 0.22f else 0.38f) * press * style.glow),
                            Color.White.copy(alpha = 0f),
                        ),
                        center = touch,
                        radius = radius,
                    ),
                    radius = radius,
                    center = touch,
                )
            }
        }

        // 4. Liseré : lumière venant du haut-gauche, rebond discret en bas-droite.
        if (style.rim > 0f) {
            val a = style.rim * (0.75f + 0.25f * press)
            val rimBrush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color.White.copy(alpha = (if (dark) 0.42f else 0.85f) * a),
                    0.35f to Color.White.copy(alpha = (if (dark) 0.08f else 0.18f) * a),
                    0.65f to Color.White.copy(alpha = 0.02f * a),
                    1f to Color.White.copy(alpha = (if (dark) 0.22f else 0.45f) * a),
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            )
            drawPath(path, rimBrush, style = Stroke(width = 1.2.dp.toPx()))
            if (!dark) {
                // Fine ombre intérieure en bas pour donner du volume.
                drawPath(
                    path,
                    Brush.verticalGradient(
                        0.7f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.06f * a),
                    ),
                    style = Stroke(width = 1.2.dp.toPx()),
                )
            }
        }
    }

    private fun buildEffect(
        w: Float, h: Float, margin: Float, r: Float, blurPx: Float, refraction: Float, band: Float, style: GlassStyle,
    ): androidx.compose.ui.graphics.RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val frost = frostEffect(blurPx, style.saturation)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && refraction > 0f) {
            val shader = (lens as? LensShader) ?: LensShader().also { lens = it }
            return shader.effect(
                width = w, height = h, margin = margin, radius = r,
                band = min(band, min(w, h) / 2f),
                strength = refraction,
                dispersion = style.dispersion,
                fresnel = style.fresnel,
                input = frost,
            ).asComposeRenderEffect()
        }
        return frost?.asComposeRenderEffect()
    }
}
