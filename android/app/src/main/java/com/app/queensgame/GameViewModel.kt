//
//  GameViewModel.kt
//  QueensGame (Android)
//
//  Bridges the platform-free [GameEngine] to Compose: publishes snapshots,
//  runs the clock, owns the Royal Pass store and the overlay flags.
//

package com.app.queensgame

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.queensgame.billing.PremiumStore
import com.app.queensgame.core.AppTheme
import com.app.queensgame.core.GameEngine
import com.app.queensgame.core.GameEvent
import com.app.queensgame.core.GameMode
import com.app.queensgame.core.GameSnapshot
import com.app.queensgame.core.GridPos
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = GameEngine(PrefsStore(app))

    val store = PremiumStore(app, viewModelScope) { enforceEntitlement() }

    var state: GameSnapshot by mutableStateOf(engine.snapshot())
        private set
    var showGameOver by mutableStateOf(false)
    var showHowToPlay by mutableStateOf(false)

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 32)
    /** Haptic cues for the UI. */
    val events: SharedFlow<GameEvent> = _events

    private var gameOverJob: Job? = null

    init {
        engine.onEvent = { event ->
            _events.tryEmit(event)
            if (event == GameEvent.Solved) {
                gameOverJob?.cancel()
                gameOverJob = viewModelScope.launch {
                    delay(500)
                    showGameOver = true
                }
            }
        }
        enforceEntitlement()
        viewModelScope.launch {
            while (isActive) {
                delay(250)
                engine.tick()
                publish()
            }
        }
    }

    private fun publish() {
        state = engine.snapshot()
    }

    private inline fun act(block: GameEngine.() -> Unit) {
        engine.block()
        publish()
    }

    // region Board input

    fun tap(pos: GridPos) = act { tap(pos) }
    fun placeQueen(pos: GridPos) = act { placeQueen(pos) }
    fun drag(start: GridPos?, current: GridPos?) = act { handleDrag(start, current) }
    fun endDrag() = act { endDrag() }
    fun undo() = act {
        val wasSolved = isSolved
        undo()
        if (wasSolved) dismissGameOver()
    }
    fun redo() = act { redo() }
    fun clearBoard() = act { clearBoard() }

    // endregion

    // region Rounds + settings

    fun newGame(daily: Boolean) {
        dismissGameOver()
        act { startNewGame(daily) }
    }

    /** Locked modes open the paywall instead of switching. */
    fun selectMode(mode: GameMode) {
        if (store.canPlay(mode)) {
            dismissGameOver()
            act { this.mode = mode }
        } else {
            store.showPaywall = true
        }
    }

    fun setSize(size: Int) {
        dismissGameOver()
        act { this.size = size }
    }

    fun setAutoX(enabled: Boolean) = act { autoXEnabled = enabled }
    fun setSpeedDecrees(enabled: Boolean) = act { speedDecrees = enabled }
    fun setTheme(theme: AppTheme) = act { this.theme = theme }

    fun dismissGameOver() {
        gameOverJob?.cancel()
        showGameOver = false
    }

    /**
     * Drops back to Standard if a premium mode was saved but the unlock is
     * gone (refund, different Google account).
     */
    private fun enforceEntitlement() {
        if (!store.canPlay(engine.mode)) act { mode = GameMode.STANDARD }
    }

    // endregion

    // region Lifecycle

    fun onResume() {
        engine.resumeTimer()
        store.refresh()
        publish()
    }

    fun onPause() {
        engine.pauseTimer()
        publish()
    }

    override fun onCleared() {
        store.endConnection()
    }

    // endregion
}
