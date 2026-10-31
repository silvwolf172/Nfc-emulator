package com.sillage.gpx.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Bouton rond en verre. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    enabled: Boolean = true,
    style: GlassStyle = GlassDefaults.style(),
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onLongClick: (() -> Unit)? = null,
) {
    GlassSurface(
        modifier = modifier.size(size),
        cornerRadius = size,
        style = style,
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        pressedScale = 1.16f,
        maxPull = 12.dp,
        semanticLabel = contentDescription,
        contentColor = tint,
    ) {
        Icon(icon, contentDescription, Modifier.size(size * 0.46f).alpha(if (enabled) 1f else 0.38f))
    }
}

/** Pilule en verre avec icône et libellé. */
@Composable
fun GlassPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: GlassStyle = GlassDefaults.style(),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    height: Dp = 48.dp,
    enabled: Boolean = true,
) {
    GlassSurface(
        modifier = modifier.height(height),
        cornerRadius = height,
        style = style,
        onClick = onClick,
        enabled = enabled,
        pressedScale = 1.08f,
        maxPull = 12.dp,
        semanticLabel = text,
        contentColor = contentColor,
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp).alpha(if (enabled) 1f else 0.38f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Bouton d'action flottant en verre teinté. */
@Composable
fun GlassFab(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    size: Dp = 64.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    iconRotation: Float = 0f,
) {
    GlassSurface(
        modifier = modifier.height(size).then(if (text == null) Modifier.width(size) else Modifier),
        cornerRadius = size * 0.36f,
        style = GlassDefaults.tinted(color),
        onClick = onClick,
        pressedScale = 1.12f,
        maxPull = 14.dp,
        semanticLabel = contentDescription ?: text,
        contentColor = contentColor,
    ) {
        if (text == null) {
            Icon(icon, contentDescription, Modifier.size(28.dp).rotate(iconRotation))
        } else {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(text, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/** Barre de verre horizontale accueillant des boutons. */
@Composable
fun GlassBar(
    modifier: Modifier = Modifier,
    height: Dp = 60.dp,
    style: GlassStyle = GlassDefaults.panel(),
    content: @Composable RowScope.() -> Unit,
) {
    GlassSurface(modifier = modifier.height(height), cornerRadius = height, style = style, interactive = false) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            content = content,
        )
    }
}

/** Bouton sans fond, à placer dans une [GlassBar] : rebondit lui aussi. */
@Composable
fun BarButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    label: String? = null,
) {
    val selectedStyle = GlassDefaults.tinted(MaterialTheme.colorScheme.primary, 0.85f).copy(shadow = false)
    val idle = GlassDefaults.style().copy(
        tint = Color.Transparent, fallbackTint = Color.Transparent, rim = 0f, shadow = false, refraction = 0.dp, blur = 0.dp,
    )
    GlassSurface(
        modifier = modifier.height(48.dp).then(if (label == null) Modifier.width(48.dp) else Modifier),
        cornerRadius = 24.dp,
        style = if (selected) selectedStyle else idle,
        backdrop = if (selected) LocalGlassBackdrop.current else null,
        onClick = onClick,
        enabled = enabled,
        pressedScale = 1.18f,
        maxPull = 10.dp,
        semanticLabel = contentDescription,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            Modifier.padding(horizontal = if (label != null) 14.dp else 0.dp).alpha(if (enabled) 1f else 0.35f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription, Modifier.size(22.dp))
            if (label != null) {
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

data class LiquidTab(val label: String, val icon: ImageVector? = null)

/**
 * Sélecteur à onglets dont l'indicateur est une goutte de verre : elle s'étire en se déplaçant, suit le doigt
 * quand on glisse et gonfle à l'appui.
 */
@Composable
fun LiquidTabs(
    tabs: List<LiquidTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val currentOnSelect = rememberUpdatedState(onSelect)
    val indicatorBounce = rememberBounceState()
    val left = remember { Animatable(0f) }
    val right = remember { Animatable(0f) }
    val inset = with(density) { 4.dp.toPx() }

    GlassSurface(modifier = modifier.height(height), cornerRadius = height, style = GlassDefaults.panel(), interactive = false) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val total = constraints.maxWidth.toFloat()
            val itemW = if (tabs.isEmpty()) total else total / tabs.size

            fun animateTo(index: Int, fast: Boolean = false) {
                val targetL = index * itemW + inset
                val targetR = (index + 1) * itemW - inset
                val movingRight = targetL > left.value
                val lead = spring<Float>(dampingRatio = 0.62f, stiffness = if (fast) 1400f else 820f)
                val trail = spring<Float>(dampingRatio = 0.7f, stiffness = if (fast) 700f else 260f)
                scope.launch { left.animateTo(targetL, if (movingRight) trail else lead) }
                scope.launch { right.animateTo(targetR, if (movingRight) lead else trail) }
            }

            LaunchedEffect(total, tabs.size) {
                if (right.value == 0f) {
                    left.snapTo(selected * itemW + inset)
                    right.snapTo((selected + 1) * itemW - inset)
                } else animateTo(selected)
            }
            LaunchedEffect(selected) { animateTo(selected) }

            // Goutte de verre (indicateur)
            val w = (right.value - left.value).coerceAtLeast(0f)
            GlassSurface(
                modifier = Modifier
                    .offset { IntOffset(left.value.roundToInt(), 0) }
                    .width(with(density) { w.toDp() })
                    .fillMaxHeight()
                    .padding(vertical = 4.dp),
                cornerRadius = height,
                style = GlassDefaults.tinted(MaterialTheme.colorScheme.primaryContainer, 0.72f).copy(
                    shadow = false, refraction = 10.dp, refractionBand = 14.dp, blur = 2.dp,
                ),
                interactive = false,
                bounceState = indicatorBounce,
                stretch = 1.6f,
            ) {}

            Row(
                Modifier
                    .fillMaxSize()
                    .pointerInput(tabs.size, itemW) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            indicatorBounce.onPress(down.position, 1.1f)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            var dragging = false
                            var lastX = down.position.x
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                lastX = change.position.x
                                if (change.changedToUpIgnoreConsumed()) break
                                if (!dragging && abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) dragging = true
                                if (dragging) {
                                    change.consume()
                                    val center = change.position.x.coerceIn(itemW / 2, total - itemW / 2)
                                    val l = center - itemW / 2 + inset
                                    val r = center + itemW / 2 - inset
                                    scope.launch { left.animateTo(l, spring(0.8f, 1600f)) }
                                    scope.launch { right.animateTo(r, spring(0.8f, 1600f)) }
                                }
                            }
                            indicatorBounce.onRelease()
                            val index = (lastX / itemW).toInt().coerceIn(0, tabs.lastIndex)
                            if (index != selected) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnSelect.value(index)
                            } else {
                                animateTo(index, fast = true)
                            }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { i, tab ->
                    val isSel = i == selected
                    Row(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics { role = Role.Tab; this.selected = isSel },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        val color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else LocalContentColor.current.copy(alpha = 0.8f)
                        if (tab.icon != null) {
                            Icon(tab.icon, null, Modifier.size(18.dp), tint = color)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(tab.label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Puce en verre non interactive (statistiques compactes). */
@Composable
fun GlassChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    GlassSurface(
        modifier = modifier.height(32.dp),
        cornerRadius = 16.dp,
        style = GlassDefaults.style().copy(refraction = 6.dp, refractionBand = 10.dp, shadow = false),
        interactive = false,
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

