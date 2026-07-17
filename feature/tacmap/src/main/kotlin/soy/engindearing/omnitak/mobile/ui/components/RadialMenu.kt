package soy.engindearing.omnitak.mobile.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import soy.engindearing.omnitak.mobile.ui.theme.TacticalAccent
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * One slice of a [RadialMenu]. [color] overrides the default neon accent;
 * [enabled] false dims the item but keeps it visible.
 */
data class RadialAction(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val color: Color = TacticalAccent,
    val enabled: Boolean = true,
)

/**
 * ATAK-style radial / wheel menu. Renders a ring of [actions] around
 * [anchor] when [visible] is true. Tapping an item invokes [onSelect];
 * tapping outside the ring invokes [onDismiss]. Items are laid out
 * starting at 12 o'clock and proceeding clockwise.
 *
 * [anchor] is in map-surface pixels, typically from
 * `TacticalMap(onMapLongPress)`. The ring center is clamped inside the
 * overlay bounds so a long-press near a screen edge keeps every item
 * (and its label) tappable.
 *
 * Perf: one spring drives the whole open animation. It is read ONLY
 * inside `offset {}` / `graphicsLayer {}` lambdas, which invalidate
 * placement/draw — never composition — so the bloom stays smooth even
 * while the surrounding MapScreen recomposes (GPS, telemetry).
 */
@Composable
fun RadialMenu(
    visible: Boolean,
    anchor: Offset,
    actions: List<RadialAction>,
    onSelect: (RadialAction) -> Unit,
    onDismiss: () -> Unit,
    radiusDp: Int = 96,
    itemSizeDp: Int = 56,
    modifier: Modifier = Modifier,
) {
    if (!visible || actions.isEmpty()) return

    val density = LocalDensity.current
    val radiusPx = with(density) { radiusDp.dp.toPx() }
    val itemPx = with(density) { itemSizeDp.dp.toPx() }
    // Label strip under each item: fixed slot so clamping can account for it.
    val labelSlotW = with(density) { 88.dp.toPx() }
    val labelSlotH = with(density) { 18.dp.toPx() }

    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "radial-bloom",
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        // Clamp the ring center so items + labels stay on screen.
        val marginX = radiusPx + labelSlotW / 2f
        val marginTop = radiusPx + itemPx / 2f
        val marginBottom = radiusPx + itemPx / 2f + labelSlotH
        val center = Offset(
            x = if (wPx > marginX * 2) anchor.x.coerceIn(marginX, wPx - marginX) else wPx / 2f,
            y = if (hPx > marginTop + marginBottom) {
                anchor.y.coerceIn(marginTop, hPx - marginBottom)
            } else hPx / 2f,
        )

        // Dimming scrim — fades with the same spring, alpha on the layer so
        // it never recomposes.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = min(1f, progress) }
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(indication = null, interactionSource = null, onClick = onDismiss),
        )

        // Ring items, evenly spaced starting from top (−π/2), blooming
        // outward from the center as the spring settles.
        val count = actions.size
        actions.forEachIndexed { index, action ->
            val angle = -Math.PI / 2.0 + (2.0 * Math.PI * index / count)
            val dirX = cos(angle).toFloat()
            val dirY = sin(angle).toFloat()

            val tint = if (action.enabled) action.color else Color(0xFF7A8288)
            Column(
                modifier = Modifier
                    .offset {
                        // progress read here → placement-only invalidation.
                        val r = radiusPx * progress
                        IntOffset(
                            (center.x + dirX * r - labelSlotW / 2f).roundToInt(),
                            (center.y + dirY * r - itemPx / 2f).roundToInt(),
                        )
                    }
                    .width(88.dp)
                    .graphicsLayer {
                        val p = min(1f, progress)
                        scaleX = 0.6f + 0.4f * p
                        scaleY = 0.6f + 0.4f * p
                        alpha = min(1f, p * 1.6f)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(itemSizeDp.dp)
                        .clip(CircleShape)
                        .background(Color(0xF2121A21))
                        .border(1.5.dp, tint.copy(alpha = if (action.enabled) 0.95f else 0.5f), CircleShape)
                        .clickable(enabled = action.enabled) { onSelect(action) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        action.icon,
                        contentDescription = action.label,
                        tint = tint,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    action.label,
                    color = if (action.enabled) Color.White else Color(0xFF9AA3A9),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xB80B0F13))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }

        // Center cancel button on the (clamped) ring center.
        Box(
            modifier = Modifier
                .offset {
                    val half = with(density) { 22.dp.toPx() }
                    IntOffset(
                        (center.x - half).roundToInt(),
                        (center.y - half).roundToInt(),
                    )
                }
                .size(44.dp)
                .graphicsLayer {
                    val p = min(1f, progress)
                    scaleX = p
                    scaleY = p
                }
                .clip(CircleShape)
                .background(Color(0xF2121A21))
                .border(1.5.dp, TacticalAccent, CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Close menu",
                tint = TacticalAccent,
            )
        }
    }
}

/** Small text caption shown under a radial ring for one-tap context. */
@Composable
fun RadialCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = TacticalAccent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}
