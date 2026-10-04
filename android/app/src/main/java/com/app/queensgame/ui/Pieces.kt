//
//  Pieces.kt
//  QueensGame (Android)
//
//  Smaller building blocks: the decree banner, realm chips, control dock,
//  ambient background and the confetti burst.
//

package com.app.queensgame.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.queensgame.core.Decree
import com.app.queensgame.core.GameSnapshot
import com.app.queensgame.core.roman
import kotlin.random.Random

// region Decree banner

@Composable
fun DecreeBanner(decree: Decree, rotating: Boolean) {
    val q = LocalQColors.current
    AnimatedContent(
        targetState = decree,
        transitionSpec = { (slideInVertically { -it / 2 } + fadeIn()) togetherWith fadeOut() },
        label = "decree",
    ) { shown ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(q.accent.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                .border(1.dp, q.accent.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
                .padding(12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .background(q.accent.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
            ) {
                Icon(shown.icon, contentDescription = null, tint = q.accent)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (rotating) "DECREE · ROTATING" else "DECREE IN FORCE",
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = q.accent,
                )
                Text(shown.title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = q.ink)
                Text(shown.blurb, fontSize = 12.sp, color = q.muted)
            }
        }
    }
}

// endregion

// region Realm chips

@Composable
fun RealmChips(state: GameSnapshot) {
    val q = LocalQColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 2.dp),
    ) {
        for (realm in 0 until state.puzzle.size) {
            val done = state.analysis.regionDone[realm]
            val hidden = state.fogActive && !state.isRevealed(realm)
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(q.surface2, CircleShape)
                    .border(1.dp, q.line, CircleShape)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                Box(
                    Modifier
                        .size(13.dp)
                        .background(if (hidden) q.fog else q.region(state.puzzle.colorMap[realm]), RoundedCornerShape(4.dp))
                        .border(1.dp, Color.Black.copy(alpha = 0.12f), RoundedCornerShape(4.dp)),
                )
                Text(
                    if (hidden) "?" else roman(realm + 1),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = if (done) q.faint else q.ink,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                if (done) {
                    Icon(Icons.Filled.Check, contentDescription = "done", tint = q.ok, modifier = Modifier.size(11.dp))
                }
            }
        }
    }
}

// endregion

// region Control dock

fun formatTime(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

@Composable
fun ControlDock(
    state: GameSnapshot,
    hints: Int,
    onHint: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
) {
    val q = LocalQColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .card(q, 16.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Stat("TIME", formatTime(state.elapsedSeconds))
        Stat("MOVES", "${state.moves}")
        Spacer(Modifier.weight(1f))
        Box {
            DockButton(Icons.Filled.Lightbulb, "Hint, $hints left", !state.isSolved, onHint)
            Text(
                "$hints",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 5.dp, y = (-5).dp)
                    .defaultMinSize(minWidth = 17.dp)
                    .clip(CircleShape)
                    .background(if (hints > 0) q.accent else q.muted)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
                    .clearAndSetSemantics {},
                textAlign = TextAlign.Center,
            )
        }
        DockButton(Icons.AutoMirrored.Filled.Undo, "Undo", state.canUndo, onUndo)
        DockButton(Icons.AutoMirrored.Filled.Redo, "Redo", state.canRedo, onRedo)
        DockButton(Icons.Filled.Delete, "Clear board", true, onClear)
    }
}

@Composable
private fun Stat(title: String, value: String) {
    val q = LocalQColors.current
    Column(modifier = Modifier.defaultMinSize(minWidth = 52.dp)) {
        Text(title, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, color = q.muted)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = q.ink)
    }
}

@Composable
private fun DockButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val q = LocalQColors.current
    val shape = RoundedCornerShape(11.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .background(q.surface2)
            .border(1.dp, q.line, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Icon(icon, contentDescription = label, tint = if (enabled) q.ink else q.faint)
    }
}

// endregion

// region Ambient background

/** Two slow drifting colour washes over the base tint. Still under Reduce Motion. */
@Composable
fun AnimatedBackground(modifier: Modifier = Modifier) {
    val q = LocalQColors.current
    val reduceMotion = rememberReduceMotion()
    val drift = if (reduceMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "bg")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(14_000), RepeatMode.Reverse),
            label = "drift",
        )
        value
    }
    Canvas(modifier.fillMaxSize().background(q.bg)) {
        val d = density
        val c1 = Offset(size.width / 2 + (-70 - 60 * drift) * d, size.height / 2 + (-180 - 60 * drift) * d)
        val c2 = Offset(size.width / 2 + (90 + 60 * drift) * d, size.height / 2 + (360 - 60 * drift) * d)
        drawCircle(
            Brush.radialGradient(listOf(q.accent.copy(alpha = 0.16f), Color.Transparent), c1, 260 * d),
            radius = 260 * d, center = c1,
        )
        drawCircle(
            Brush.radialGradient(listOf(q.gold.copy(alpha = 0.12f), Color.Transparent), c2, 260 * d),
            radius = 260 * d, center = c2,
        )
    }
}

// endregion

// region Confetti

private class Particle(
    val x0: Float, val vx: Float, val vy: Float, val delay: Float,
    val spin: Float, val color: Int, val w: Float,
)

/** A self-contained confetti burst. Skipped when animations are off. */
@Composable
fun ConfettiOverlay(active: Boolean) {
    val q = LocalQColors.current
    val reduceMotion = rememberReduceMotion()
    val palette = listOf(q.accent, q.gold, q.region(0), q.region(2), q.region(3))
    val particles = remember {
        val rng = Random(0x5EED_C0FF)
        List(150) {
            Particle(
                x0 = rng.nextFloat(), vx = rng.nextFloat() * 90 - 45, vy = 130 + rng.nextFloat() * 210,
                delay = rng.nextFloat() * 0.45f, spin = rng.nextFloat() * 12 - 6,
                color = rng.nextInt(5), w = 5 + rng.nextFloat() * 4,
            )
        }
    }
    var t by remember { mutableFloatStateOf(-1f) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(active) {
        if (!active || reduceMotion) { running = false; return@LaunchedEffect }
        running = true
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            t = (now - start) / 1_000_000_000f
            if (t > CONFETTI_SECONDS) break
        }
        running = false
    }

    if (!running) return
    Canvas(Modifier.fillMaxSize()) {
        val d = density
        for (p in particles) {
            val lt = maxOf(0f, t - p.delay)
            val x = p.x0 * size.width + p.vx * lt * d
            val y = (-20 + p.vy * lt + 92 * lt * lt) * d
            if (y > size.height + 40 * d) continue
            val alpha = maxOf(0f, 1 - lt / (CONFETTI_SECONDS - 0.4f))
            val w = p.w * d
            translate(x, y) {
                rotate(Math.toDegrees((p.spin * lt).toDouble()).toFloat(), pivot = Offset.Zero) {
                    drawRect(palette[p.color].copy(alpha = alpha), Offset(-w / 2, -w * 0.7f), Size(w, w * 1.4f))
                }
            }
        }
    }
}

private const val CONFETTI_SECONDS = 3.6f

// endregion
