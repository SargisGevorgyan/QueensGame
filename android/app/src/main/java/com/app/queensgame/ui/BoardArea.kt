//
//  BoardArea.kt
//  QueensGame (Android)
//
//  The board itself plus the rank/file numerals down the edges.
//  Input model (same as iOS):
//    • tap            → cycle empty → × → Queen → empty
//    • long-press     → drop a Queen directly
//    • drag (≥16 dp)  → sweep × marks across squares (drag from an × erases)
//

package com.app.queensgame.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.queensgame.core.CellDisplay
import com.app.queensgame.core.GameSnapshot
import com.app.queensgame.core.GridPos
import com.app.queensgame.core.WallEdge
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

private val Gap = 2.dp
private val LabelGutter = 16.dp
private val BoardShape = RoundedCornerShape(13.dp)

@Composable
fun BoardArea(
    state: GameSnapshot,
    side: Dp,
    onTap: (GridPos) -> Unit,
    onLongPress: (GridPos) -> Unit,
    onDrag: (start: GridPos?, current: GridPos?) -> Unit,
    onDragEnd: () -> Unit,
) {
    val q = LocalQColors.current
    val n = state.puzzle.size
    val cell = (side - Gap * (n - 1)) / n

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Column numerals
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            Spacer(Modifier.width(LabelGutter + 4.dp - Gap))
            for (c in 0 until n) {
                Numeral(c + 1, state.analysis.colDone[c], Modifier.width(cell))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Row numerals
            Column(verticalArrangement = Arrangement.spacedBy(Gap), modifier = Modifier.width(LabelGutter)) {
                for (r in 0 until n) {
                    Box(Modifier.size(LabelGutter, cell), contentAlignment = Alignment.Center) {
                        Numeral(r + 1, state.analysis.rowDone[r])
                    }
                }
            }

            Board(state, side, cell, q, onTap, onLongPress, onDrag, onDragEnd)
        }
    }
}

@Composable
private fun Board(
    state: GameSnapshot,
    side: Dp,
    cell: Dp,
    q: QColors,
    onTap: (GridPos) -> Unit,
    onLongPress: (GridPos) -> Unit,
    onDrag: (GridPos?, GridPos?) -> Unit,
    onDragEnd: () -> Unit,
) {
    val n = state.puzzle.size
    val density = LocalDensity.current
    val cellPx = with(density) { cell.toPx() }
    val gapPx = with(density) { Gap.toPx() }
    val slopPx = with(density) { 16.dp.toPx() }

    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)

    // One shake per token bump (bad move / blocked square).
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state.shakeToken) {
        if (state.shakeToken == 0) return@LaunchedEffect
        shake.snapTo(0f)
        shake.animateTo(1f, tween(450, easing = LinearEasing))
    }
    val travelPx = with(density) { 7.dp.toPx() }

    fun posAt(offset: Offset): GridPos? {
        val step = cellPx + gapPx
        val col = floor(offset.x / step).toInt()
        val row = floor(offset.y / step).toInt()
        return if (row in 0 until n && col in 0 until n) GridPos(row, col) else null
    }

    Box(
        modifier = Modifier
            .graphicsLayer { translationX = travelPx * sin(shake.value * PI.toFloat() * 3f) }
            .size(side)
            .shadow(10.dp, BoardShape, ambientColor = Color.Black.copy(alpha = 0.10f))
            .clip(BoardShape)
            .background(q.wall.copy(alpha = 0.22f))
            .border(2.dp, q.wall, BoardShape)
            .pointerInput(n, state.isSolved, cellPx) {
                if (state.isSolved) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val start = posAt(down.position)
                    down.consume()

                    // Phase 1: a tap, a drag past the slop, or a long-press.
                    val outcome = withTimeoutOrNull(LONG_PRESS_MILLIS) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: return@withTimeoutOrNull Outcome.CANCEL
                            if (!change.pressed) {
                                change.consume()
                                return@withTimeoutOrNull Outcome.TAP
                            }
                            change.consume()
                            if ((change.position - down.position).getDistance() > slopPx) {
                                return@withTimeoutOrNull Outcome.DRAG
                            }
                        }
                        @Suppress("UNREACHABLE_CODE")
                        Outcome.CANCEL
                    }

                    when (outcome) {
                        Outcome.TAP -> start?.let(tap)
                        null -> {
                            start?.let(longPress)
                            // Swallow the rest of the press.
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                                if (event.changes.none { it.pressed }) break
                            }
                        }
                        Outcome.DRAG -> {
                            var last = down.position
                            drag(start, posAt(last))
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) break
                                last = change.position
                                drag(start, posAt(last))
                            }
                            dragEnd()
                        }
                        Outcome.CANCEL -> Unit
                    }
                }
            },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
            for (r in 0 until n) {
                Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    for (c in 0 until n) {
                        CellView(state, GridPos(r, c), cell, q)
                    }
                }
            }
        }
    }
}

private enum class Outcome { TAP, DRAG, CANCEL }

private const val LONG_PRESS_MILLIS = 400L

@Composable
private fun Numeral(value: Int, done: Boolean, modifier: Modifier = Modifier) {
    val q = LocalQColors.current
    Text(
        text = "$value",
        modifier = modifier.alpha(if (done) 0.5f else 1f),
        textAlign = TextAlign.Center,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
        color = if (done) q.faint else q.muted,
        textDecoration = if (done) TextDecoration.LineThrough else null,
    )
}

// region Single cell

@Composable
private fun CellView(state: GameSnapshot, pos: GridPos, side: Dp, q: QColors) {
    val region = state.puzzle.region(pos)
    val guarded = state.isGuard(pos)
    val fogged = state.fogActive && !state.isRevealed(region)
    val display = state.display(pos)
    val autoOnly = state.isAutoX(pos)
    val conflict = pos in state.analysis.conflicts
    val hinted = state.hintedCell == pos
    val walls = if (!guarded && !fogged) state.walls(pos) else emptySet()

    val target = when {
        guarded -> q.guardBg
        fogged -> q.fog
        else -> q.region(state.puzzle.colorMap[region])
    }
    val background by animateColorAsState(target, tween(250), label = "cell")
    val density = LocalDensity.current
    val wallPx = with(density) { 2.dp.toPx() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(side)
            .background(background)
            .drawBehind {
                val color = q.wall
                if (WallEdge.TOP in walls) drawRect(color, Offset.Zero, Size(size.width, wallPx))
                if (WallEdge.BOTTOM in walls) drawRect(color, Offset(0f, size.height - wallPx), Size(size.width, wallPx))
                if (WallEdge.LEADING in walls) drawRect(color, Offset.Zero, Size(wallPx, size.height))
                if (WallEdge.TRAILING in walls) drawRect(color, Offset(size.width - wallPx, 0f), Size(wallPx, size.height))
            }
            .then(
                when {
                    conflict -> Modifier.border(2.5.dp, q.danger)
                    hinted -> Modifier.border(3.dp, q.gold)
                    else -> Modifier
                },
            )
            .semantics { contentDescription = describe(pos, display, guarded) },
    ) {
        val symbolSize = side * 0.6f
        if (guarded) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = q.muted, modifier = Modifier.size(symbolSize))
        }
        AnimatedVisibility(
            visible = !guarded && display == CellDisplay.QUEEN,
            enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.4f) + fadeIn(),
            exit = scaleOut(targetScale = 0.4f) + fadeOut(),
        ) {
            Icon(
                CrownIcon,
                contentDescription = null,
                tint = if (conflict) q.danger else q.gold,
                modifier = Modifier.size(symbolSize),
            )
        }
        AnimatedVisibility(
            visible = !guarded && display == CellDisplay.X,
            enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.5f) + fadeIn(),
            exit = scaleOut(targetScale = 0.5f) + fadeOut(),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = null,
                tint = q.ink.copy(alpha = if (autoOnly) 0.24f else 0.55f),
                modifier = Modifier.size(symbolSize).padding(side * 0.04f),
            )
        }
    }
}

private fun describe(pos: GridPos, display: CellDisplay, guarded: Boolean): String {
    val what = when {
        guarded -> "guard"
        display == CellDisplay.QUEEN -> "queen"
        display == CellDisplay.X -> "marked"
        else -> "empty"
    }
    return "Row ${pos.row + 1}, column ${pos.col + 1}, $what"
}

// endregion
