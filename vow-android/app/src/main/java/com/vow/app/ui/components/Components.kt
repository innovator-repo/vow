package com.vow.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vow.app.ui.theme.AuroraBrush
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Springy press feedback: shrinks slightly while held, bounces back on release. */
fun Modifier.pressScale(pressed: Boolean, scaleDown: Float = com.vow.app.designsystem.LumeMotion.PRESSED_SCALE): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = com.vow.app.designsystem.LumeMotion.spatial(),
        label = "pressScale"
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Fades and slides content up on first composition, delayed by [index] for a staggered list. */
@Composable
fun StaggeredItem(index: Int, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 55L)
        progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
    }
    Box(
        Modifier.graphicsLayer {
            alpha = progress.value.coerceIn(0f, 1f)
            translationY = (1f - progress.value) * 40.dp.toPx()
        }
    ) { content() }
}

@Composable
fun VowCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    borderBrush: Brush? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable ColumnScope.() -> Unit
) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val animatedColor by animateColorAsState(containerColor, tween(300), label = "cardColor")
    val border = borderBrush
        ?: Brush.linearGradient(listOf(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.outlineVariant))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(pressed && onClick != null)
            .clip(shape)
            .background(animatedColor)
            .border(1.dp, border, shape)
            .then(
                if (onClick != null) Modifier.clickable(interaction, LocalIndication()) {
                    haptics.click(); onClick()
                } else Modifier
            ),
        content = content
    )
}

@Composable
private fun LocalIndication() = androidx.compose.material.ripple.rememberRipple()

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    brush: Brush = AuroraBrush,
    contentColor: Color = Color(0xFF071318),
    enabled: Boolean = true
) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .pressScale(pressed)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .clip(CircleShape)
            .background(brush)
            .clickable(interaction, LocalIndication(), enabled = enabled) {
                haptics.confirm(); onClick()
            }
            .padding(horizontal = 28.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, color = contentColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .pressScale(pressed)
            .clip(CircleShape)
            .clickable(interaction, LocalIndication()) { haptics.tick(); onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Must be held for [holdMillis] to fire. Fills left-to-right with ticks that speed up,
 * a confirm buzz on success, and a soft reject if released early.
 */
@Composable
fun HoldToConfirmButton(
    text: String,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    holdMillis: Int = 2400,
    brush: Brush = AuroraBrush,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var pressed by remember { mutableStateOf(false) }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline

    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        var lastStep = 0
        val ticker = launch {
            while (true) {
                val step = (progress.value * 12).toInt()
                if (step > lastStep) { haptics.tick(); lastStep = step }
                delay(16)
            }
        }
        progress.animateTo(1f, tween(((1f - progress.value) * holdMillis).toInt(), easing = LinearEasing))
        ticker.cancel()
        haptics.confirm()
        progress.snapTo(0f)
        onConfirmed()
        pressed = false
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .pressScale(pressed, 0.97f)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .clip(CircleShape)
            .background(track)
            .border(1.dp, outline, CircleShape)
            .drawBehind {
                drawRoundRect(
                    brush = brush,
                    size = Size(size.width * progress.value, size.height),
                    cornerRadius = CornerRadius(size.height / 2)
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    haptics.click()
                    pressed = true
                    val up = waitForUpOrCancellation()
                    if (pressed) {
                        pressed = false
                        if (progress.value < 1f) {
                            haptics.reject()
                            scope.launch { progress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                        }
                    }
                    if (up == null) pressed = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val textColor = if (progress.value > 0.5f) Color(0xFF071318) else MaterialTheme.colorScheme.onSurface
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                if (pressed) "Keep holding…" else text,
                color = textColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun HapticSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, accent: Color = MaterialTheme.colorScheme.primary) {
    val haptics = rememberHaptics()
    Switch(
        checked = checked,
        onCheckedChange = { haptics.toggle(it); onCheckedChange(it) },
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = accent,
            checkedBorderColor = accent,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedTrackColor = MaterialTheme.colorScheme.surface,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
fun IconBubble(icon: ImageVector, tint: Color, size: Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.25f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Soft aurora glow behind the top of a screen. */
fun Modifier.auroraGlow(color: Color = Color(0xFF2DD4BF), alpha: Float = 0.10f): Modifier = drawBehind {
    drawCircle(
        brush = Brush.radialGradient(
            listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center.copy(y = 0f),
            radius = size.width * 0.9f
        ),
        radius = size.width * 0.9f,
        center = center.copy(y = 0f)
    )
}
