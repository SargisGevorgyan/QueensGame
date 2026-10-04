//
//  Overlays.kt
//  QueensGame (Android)
//
//  The victory card, the How-to-play sheet, the Royal Pass paywall and the
//  "out of hints" offer (rewarded video or hint pack).
//

package com.app.queensgame.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.app.queensgame.GameViewModel
import com.app.queensgame.billing.PremiumStore
import com.app.queensgame.billing.PurchaseState
import com.app.queensgame.core.Decree
import com.app.queensgame.core.GameSnapshot
import com.app.queensgame.core.HintWallet

// region Game over

@Composable
fun GameOverOverlay(state: GameSnapshot, onNewPuzzle: () -> Unit, onDismiss: () -> Unit) {
    val q = LocalQColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(28.dp)
                .widthIn(max = 360.dp)
                .card(q, 22.dp)
                // Swallow taps on the card itself.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(24.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(66.dp)
                    .shadow(12.dp, CircleShape, spotColor = q.gold)
                    .background(Brush.verticalGradient(listOf(q.gold.copy(alpha = 0.85f), q.gold)), CircleShape),
            ) {
                Icon(CrownIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Long live the realm",
                    fontFamily = FontFamily.Serif,
                    fontSize = 28.sp,
                    color = q.ink,
                    textAlign = TextAlign.Center,
                )
                Text(state.roundTag, fontSize = 15.sp, color = q.muted)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ResultTile("Time", formatTime(state.elapsedSeconds), Modifier.weight(1f))
                ResultTile("Moves", "${state.moves}", Modifier.weight(1f))
                ResultTile("Board", "${state.puzzle.size}×${state.puzzle.size}", Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onNewPuzzle, modifier = Modifier.weight(1f)) { Text("New puzzle") }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Review") }
            }
        }
    }
}

@Composable
private fun ResultTile(title: String, value: String, modifier: Modifier = Modifier) {
    val q = LocalQColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .background(q.surface2, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp),
    ) {
        Text(title.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = q.muted)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = q.ink)
    }
}

// endregion

// region Full-screen sheet scaffold

@Composable
private fun Sheet(
    title: String,
    actionLabel: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val q = LocalQColors.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(q.bg)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Spacer(Modifier.width(64.dp))
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = q.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss, modifier = Modifier.width(64.dp)) { Text(actionLabel) }
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(20.dp)) { content() }
            }
        }
    }
}

// endregion

// region How to play

@Composable
fun HowToPlayDialog(onDismiss: () -> Unit) {
    val q = LocalQColors.current
    Sheet(title = "How to play", actionLabel = "Done", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Text(
                "Crown the board so every rank, file and coloured realm holds exactly one Queen.",
                fontSize = 16.sp, color = q.muted,
            )
            Rule("I", "One Queen per rank, file & realm",
                "Each row, each column and each colour region gets exactly one Queen — no more, no fewer.")
            Rule("II", "Queens can’t touch",
                "No two Queens may sit in adjacent squares — horizontally, vertically or diagonally.")
            Rule("III", "Controls",
                "Tap a square to cycle empty → × → Queen → empty. Long-press to drop a Queen at once. " +
                    "Drag across squares to sweep × marks (drag from an × to erase).")
            Rule("IV", "Helpers",
                "Rank & file numbers strike through when solved. Rule-breaking Queens flash red. " +
                    "Auto-X marks every square a Queen rules out. Undo, redo, a move counter and a timer track your run.")

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Royal Decrees mode", fontFamily = FontFamily.Serif, fontSize = 20.sp, color = q.ink)
                Text(
                    "Switch on Royal Decrees and each new round a herald proclaims one rule-bending decree. " +
                        "Turn on Speed decrees to have it change every 30 seconds.",
                    fontSize = 13.sp, color = q.muted,
                )
                for (decree in Decree.entries.filter { it != Decree.TRUCE }) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(q.surface2, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                    ) {
                        Icon(decree.icon, contentDescription = null, tint = q.accent, modifier = Modifier.size(22.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(decree.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = q.ink)
                            Text(decree.blurb, fontSize = 12.sp, color = q.muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Rule(numeral: String, title: String, body: String) {
    val q = LocalQColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            numeral,
            fontFamily = FontFamily.Serif,
            fontSize = 20.sp,
            color = q.accent,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(26.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = q.ink)
            Text(body, fontSize = 15.sp, color = q.muted)
        }
    }
}

// endregion

// region Royal Pass paywall

@Composable
fun PaywallDialog(store: PremiumStore, onDismiss: () -> Unit) {
    val q = LocalQColors.current
    val activity = LocalContext.current.findActivity()
    val busy = store.purchaseState == PurchaseState.Purchasing

    DisposableEffect(Unit) {
        store.refresh()
        onDispose { store.clearError() }
    }

    Sheet(title = "", actionLabel = "Close", onDismiss = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(CrownIcon, contentDescription = null, tint = q.gold, modifier = Modifier.size(58.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Royal Pass", fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, color = q.ink)
                Text("One purchase. Yours forever.", color = q.muted)
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .card(q, 16.dp)
                    .padding(16.dp),
            ) {
                Feature(Icons.Filled.Gavel, "Royal Decrees mode", "Every round the herald bends the rules.")
                Feature(Icons.Filled.Cloud, "All four decrees", "Fog of War, Royal Guard, Assassin's Range and Color Lock.")
                Feature(Icons.Filled.Bolt, "Speed decrees", "A new decree every 30 seconds.")
                Feature(Icons.Filled.Favorite, "Support an indie puzzle", "Keeps new puzzles and decrees coming.")
            }

            when (val s = store.purchaseState) {
                is PurchaseState.Failed -> Text(s.message, fontSize = 13.sp, color = q.danger, textAlign = TextAlign.Center)
                PurchaseState.Pending -> Text(
                    "Purchase pending approval. It unlocks automatically once approved.",
                    fontSize = 13.sp, color = q.muted, textAlign = TextAlign.Center,
                )
                else -> Unit
            }

            if (store.isPremium) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Verified, contentDescription = null, tint = q.ok)
                    Text("Royal Pass unlocked", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = q.ok)
                }
            } else {
                Button(
                    onClick = { activity?.let(store::purchase) },
                    enabled = !busy && activity != null,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = q.accent, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        store.displayPrice?.let { "Unlock for $it" } ?: "Unlock Royal Pass",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                TextButton(onClick = store::restore, enabled = !busy) { Text("Restore purchases") }
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, detail: String) {
    val q = LocalQColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = q.accent, modifier = Modifier.size(26.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = q.ink)
            Text(detail, fontSize = 15.sp, color = q.muted)
        }
    }
}

// endregion

// region Out of hints

@Composable
fun HintOfferDialog(vm: GameViewModel, onDismiss: () -> Unit) {
    val q = LocalQColors.current
    val activity = LocalContext.current.findActivity()
    val store = vm.store
    val buying = store.hintPurchaseState == PurchaseState.Purchasing
    val busy = buying || vm.adLoading

    DisposableEffect(Unit) {
        store.refresh()
        activity?.let(vm::preloadAd)
        onDispose { }
    }

    Sheet(title = "", actionLabel = "Close", onDismiss = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = q.gold, modifier = Modifier.size(54.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Out of hints", fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, color = q.ink)
                Text(
                    "A hint reveals one correct Queen, or lifts a misplaced one.",
                    color = q.muted,
                    textAlign = TextAlign.Center,
                )
            }

            Button(
                onClick = { activity?.let(vm::watchAdForHint) },
                enabled = !busy && activity != null,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = q.accent, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OfferRow(Icons.Filled.PlayCircle, "Watch a video", "+${HintWallet.AD_REWARD} hint", vm.adLoading, Color.White)
            }

            OutlinedButton(
                onClick = { activity?.let(store::purchaseHintPack) },
                enabled = !busy && activity != null,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OfferRow(
                    Icons.Filled.ShoppingCart,
                    "${PremiumStore.HINT_PACK_SIZE} hints",
                    store.hintPackPrice ?: "Buy",
                    buying,
                    q.accent,
                )
            }

            vm.adError?.let { Text(it, fontSize = 13.sp, color = q.danger, textAlign = TextAlign.Center) }
            when (val s = store.hintPurchaseState) {
                is PurchaseState.Failed -> Text(s.message, fontSize = 13.sp, color = q.danger, textAlign = TextAlign.Center)
                PurchaseState.Pending -> Text(
                    "Purchase pending approval. Your hints arrive once it's approved.",
                    fontSize = 13.sp, color = q.muted, textAlign = TextAlign.Center,
                )
                else -> Unit
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.CardGiftcard, contentDescription = null, tint = q.muted, modifier = Modifier.size(16.dp))
                Text("You get a free hint every day.", fontSize = 13.sp, color = q.muted)
            }
        }
    }
}

@Composable
private fun OfferRow(icon: ImageVector, title: String, detail: String, spinning: Boolean, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, fontWeight = FontWeight.SemiBold, color = tint, modifier = Modifier.weight(1f))
        if (spinning) {
            CircularProgressIndicator(color = tint, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            Text(detail, fontWeight = FontWeight.SemiBold, color = tint)
        }
    }
}

// endregion
