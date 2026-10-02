//
//  QueensApp.kt
//  QueensGame (Android)
//
//  The single screen: header, mode picker, decree banner, board, status,
//  realm chips and the control dock — plus the overlays and sheets.
//

package com.app.queensgame.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.app.queensgame.GameViewModel
import com.app.queensgame.core.AppTheme
import com.app.queensgame.core.GameEngine
import com.app.queensgame.core.GameEvent
import com.app.queensgame.core.GameMode

@Composable
fun QueensApp(vm: GameViewModel) {
    val state = vm.state
    QueensTheme(state.theme) {
        val q = LocalQColors.current
        val view = LocalView.current
        LaunchedEffect(vm) {
            vm.events.collect { performHaptic(view, it) }
        }

        Box(Modifier.fillMaxSize()) {
            AnimatedBackground()

            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val boardSide = min(min(maxWidth - 32.dp - 20.dp, maxHeight * 0.46f), 460.dp)
                val screenHeight = maxHeight

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .widthIn(max = 520.dp)
                            .fillMaxWidth()
                            .heightIn(min = screenHeight)
                            .padding(16.dp)
                            .animateContentSize(),
                    ) {
                        Header(vm)
                        ModePicker(state.mode, isPremium = vm.store.isPremium, onSelect = vm::selectMode)

                        state.currentDecree?.let { DecreeBanner(it, rotating = state.speedDecrees) }

                        BoardArea(
                            state = state,
                            side = boardSide,
                            onTap = vm::tap,
                            onLongPress = vm::placeQueen,
                            onDrag = vm::drag,
                            onDragEnd = vm::endDrag,
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${state.placedCount} / ${state.puzzle.size} queens placed", color = q.muted, fontSize = 15.sp)
                            if (state.analysis.conflicts.isNotEmpty()) {
                                Text("· ${state.analysis.conflicts.size} in conflict", color = q.danger, fontSize = 15.sp)
                            }
                        }

                        RealmChips(state)

                        ControlDock(state, onUndo = vm::undo, onRedo = vm::redo, onClear = vm::clearBoard)
                    }
                }
            }

            ConfettiOverlay(active = state.isSolved)

            AnimatedVisibility(
                visible = vm.showGameOver,
                enter = fadeIn() + scaleIn(initialScale = 0.92f),
                exit = fadeOut() + scaleOut(targetScale = 0.92f),
            ) {
                GameOverOverlay(
                    state = state,
                    onNewPuzzle = { vm.newGame(daily = false) },
                    onDismiss = vm::dismissGameOver,
                )
            }
            BackHandler(enabled = vm.showGameOver) { vm.dismissGameOver() }
        }

        if (vm.showHowToPlay) HowToPlayDialog(onDismiss = { vm.showHowToPlay = false })
        if (vm.store.showPaywall) PaywallDialog(vm.store, onDismiss = { vm.store.showPaywall = false })
    }
}

// region Header

@Composable
private fun Header(vm: GameViewModel) {
    val q = LocalQColors.current
    val state = vm.state
    var menuOpen by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(CrownIcon, contentDescription = null, tint = q.gold, modifier = Modifier.size(22.dp))
        Text(
            "Queens & Decrees",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            color = q.ink,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))

        AutoXToggle(state.autoXEnabled) { vm.setAutoX(!state.autoXEnabled) }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = q.ink)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                fun close(action: () -> Unit): () -> Unit = { menuOpen = false; action() }

                DropdownMenuItem(
                    text = { Text("New random puzzle") },
                    leadingIcon = { Icon(Icons.Filled.Casino, null) },
                    onClick = close { vm.newGame(daily = false) },
                )
                DropdownMenuItem(
                    text = { Text(state.dailyLabel + " puzzle") },
                    leadingIcon = { Icon(Icons.Filled.CalendarToday, null) },
                    onClick = close { vm.newGame(daily = true) },
                )
                HorizontalDivider()
                MenuLabel("Board size")
                for (size in GameEngine.SIZES) {
                    DropdownMenuItem(
                        text = { Text("$size×$size") },
                        trailingIcon = { if (state.size == size) Icon(Icons.Filled.Check, "selected") },
                        onClick = close { vm.setSize(size) },
                    )
                }
                HorizontalDivider()
                MenuLabel("Theme")
                for (theme in AppTheme.entries) {
                    DropdownMenuItem(
                        text = { Text(theme.label) },
                        trailingIcon = { if (state.theme == theme) Icon(Icons.Filled.Check, "selected") },
                        onClick = close { vm.setTheme(theme) },
                    )
                }
                if (state.mode == GameMode.DECREES) {
                    DropdownMenuItem(
                        text = { Text("Speed decrees (30s)") },
                        leadingIcon = { Icon(Icons.Filled.Bolt, null) },
                        trailingIcon = {
                            Switch(checked = state.speedDecrees, onCheckedChange = null, modifier = Modifier.padding(start = 8.dp))
                        },
                        onClick = close { vm.setSpeedDecrees(!state.speedDecrees) },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(if (vm.store.isPremium) "Royal Pass unlocked" else "Unlock Royal Pass") },
                    leadingIcon = {
                        if (vm.store.isPremium) Icon(Icons.Filled.Verified, null) else Icon(CrownIcon, null)
                    },
                    onClick = close { vm.store.showPaywall = true },
                )
                if (!vm.store.isPremium) {
                    DropdownMenuItem(
                        text = { Text("Restore purchases") },
                        leadingIcon = { Icon(Icons.Filled.Restore, null) },
                        onClick = close { vm.store.restore() },
                    )
                }
                DropdownMenuItem(
                    text = { Text("How to play") },
                    leadingIcon = { Icon(Icons.Filled.Info, null) },
                    onClick = close { vm.showHowToPlay = true },
                )
            }
        }
    }
}

@Composable
private fun MenuLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = LocalQColors.current.muted,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** The Auto-X master switch, right in the header. */
@Composable
private fun AutoXToggle(enabled: Boolean, onToggle: () -> Unit) {
    val q = LocalQColors.current
    val tint = if (enabled) q.accent else q.muted
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(if (enabled) q.accent.copy(alpha = 0.16f) else q.surface2)
            .border(1.dp, if (enabled) q.accent.copy(alpha = 0.5f) else q.line, CircleShape)
            .clickable(role = Role.Switch, onClick = onToggle)
            .semantics {
                contentDescription = "Auto-X"
                stateDescription = if (enabled) "On" else "Off"
                selected = enabled
            }
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Icon(
            if (enabled) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text("Auto-X", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = tint)
    }
}

// endregion

// region Mode picker

/** A segmented control like the iOS picker; locked modes show a padlock. */
@Composable
private fun ModePicker(mode: GameMode, isPremium: Boolean, onSelect: (GameMode) -> Unit) {
    val q = LocalQColors.current
    val outer = RoundedCornerShape(10.dp)
    val inner = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(outer)
            .background(q.surface2)
            .border(1.dp, q.line, outer)
            .padding(2.dp),
    ) {
        for (option in GameMode.entries) {
            val selected = option == mode
            val locked = option.requiresPremium && !isPremium
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .then(if (selected) Modifier.shadow(2.dp, inner) else Modifier)
                    .clip(inner)
                    .background(if (selected) q.surface else q.surface2)
                    .clickable(role = Role.Tab) { onSelect(option) }
                    .semantics { this.selected = selected }
                    .padding(vertical = 7.dp),
            ) {
                Text(
                    if (locked) "${option.title} 🔒" else option.title,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = q.ink,
                )
            }
        }
    }
}

// endregion

private fun performHaptic(view: View, event: GameEvent) {
    val constant = when (event) {
        is GameEvent.Tap -> if (event.intensity >= 0.7f) HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.CLOCK_TICK
        GameEvent.Warning -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        GameEvent.Success -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
        GameEvent.Solved -> return
    }
    view.performHapticFeedback(constant)
}
