# Architecture

The iOS app is plain SwiftUI with a single view model (MVVM). Its one third-party
dependency, Google Mobile Ads, is isolated behind `RewardedAdService`
(`Services/RewardedAds.swift`); only `AdMobRewardedAds.swift` imports it. Game logic lives in pure Swift types that don't import SwiftUI, so
it can be unit tested directly.

## Code layout

```
QueensGame/
├── App/
│   └── QueensGameApp.swift       @main; owns the one QueensGameViewModel and
│                                 injects it as an environment object
├── Models/                       pure logic, no SwiftUI
│   ├── GameModels.swift          value types: GridPos, Cell, CellBase, Puzzle,
│   │                             RuleSet, Decree, GameMode, AppTheme, BoardAnalysis
│   ├── PuzzleGenerator.swift     seeded RNG + puzzle generation, Daily seeding,
│   │                             Royal Guard placement, solution counter
│   ├── BoardRules.swift          validation (conflicts, completion) and the
│   │                             Auto-X / Color Lock target squares
│   └── StatsStore.swift          UserDefaults: solves, best times, Daily done
├── ViewModels/
│   └── QueensGameViewModel.swift board state, input, history, timer, Auto-X,
│                                 decree rotation (@MainActor ObservableObject)
├── Views/
│   ├── RootView.swift            screen layout, header + menu, mode picker,
│   │                             theme, pause/resume on scene phase changes
│   ├── BoardArea.swift           the grid, rank/file numerals, CellView,
│   │                             tap / long-press / drag gestures
│   ├── DecreeBanner.swift        current decree banner
│   ├── RealmChips.swift          per-realm completion chips
│   ├── ControlDock.swift         timer, moves, undo / redo / clear
│   ├── GameOverView.swift        victory card
│   ├── HowToPlayView.swift       rules sheet
│   ├── AnimatedBackground.swift  background (respects Reduce Motion)
│   └── ConfettiOverlay.swift     win confetti (respects Reduce Motion)
├── Support/
│   ├── Theme.swift               QColor adaptive light/dark tokens, the 9 realm
│   │                             pastels, .card() modifier
│   └── ShakeEffect.swift         board shake animation
├── Services/
│   └── Haptics.swift             UIFeedbackGenerator wrapper
└── Assets.xcassets, Info.plist

QueensGameTests/                  XCTest unit tests (generator, rules, Auto-X, VM)
QueensGameUITests/                XCUITest smoke tests
queens/index.html                 the web version (see web-version.md)
project.yml                       XcodeGen spec; QueensGame.xcodeproj is generated from it
```

## Data model (`GameModels.swift`)

- **`GridPos`** `(row, col)`: a square. It also identifies a Queen for Auto-X
  ownership, since a square holds at most one Queen.
- **`Cell`**: one square, made of
  - `base: CellBase`: what the player put there, `.empty`, `.manualX` or `.queen`;
  - `autoOwners: Set<GridPos>`: the Queens that auto-marked an × here.

  `display` derives what to draw: a Queen, an × (manual, or empty with owners),
  or empty. `isAutoX` is true for an × that is purely automatic, which the view
  draws faintly.
- **`Puzzle`**: fixed for one round: `size`, `regions[row][col]` (realm index),
  `solution[row]` (column of that row's Queen), `ruleSet`, `colorMap` (realm →
  one of 9 palette slots), plus the round's `decree` and `guardPos`.
- **`RuleSet`**: rule switches honoured by both the generator and the validator.
  Today it has one, `diagonalTwo`, for Assassin's Range.
- **`Decree`**: the five decrees (`fogOfWar`, `royalGuard`, `assassinsRange`,
  `colorLock`, `truce`) with title, blurb and SF Symbol.
- **`BoardAnalysis`**: the result of validation: Queens on the board, the set of
  conflicting Queens, done flags per row / column / realm, and `solved`.

## Rules engine (`BoardRules.swift`)

Stateless functions:

- `analyse(cells:puzzle:)` finds every Queen, flags pairs that touch (or sit
  two apart diagonally when `diagonalTwo` is on), flags Queens sharing a row,
  column or realm, then marks a row/column/realm done when it has exactly one
  non-conflicting Queen. `solved` means N Queens and no conflicts.
- `autoXTargets(for:puzzle:)` returns the squares one Queen rules out:
  neighbours, rank, file and realm. Duplicates are harmless; the caller only
  writes to empty squares.
- `realmTargets(for:puzzle:)` returns only the realm, used by Color Lock when
  Auto-X is off.

## View model (`QueensGameViewModel.swift`)

One `@MainActor ObservableObject`, created in `QueensGameApp` and read by every
view through `@EnvironmentObject`. Views never mutate game state directly; they
call view-model methods.

**Published state:** `puzzle`, `cells`, `analysis`, `revealedRegions` (Fog of
War), `moves`, `elapsed`, `isSolved`, `isDaily`, plus animation triggers
(`shakeToken`, `decreePulse`, `lastPlaced`) and sheet flags.

**Settings** (`mode`, `size`, `autoXEnabled`, `speedDecrees`, `theme`) are
`@Published` properties that save to `UserDefaults` in `didSet`. Changing mode,
size or speed decrees starts a new round; toggling Auto-X recomputes the marks.
The initializer takes a `UserDefaults` so tests can pass an isolated suite.

### Data flow for one move

```
gesture in BoardArea
  → vm.tap(pos) / vm.placeQueen(pos) / vm.handleDrag + vm.endDrag
      → mutate cells[pos] (base), apply or strip Auto-X owners
      → reveal(pos)              (Fog of War)
      → moves += 1
      → pushHistory()            (snapshot of cells)
      → recompute()
           → BoardRules.analyse → analysis
           → more conflicts than before? shake + warning haptic
           → solved? handleSolve(): stop timers, record stats, show victory
  → SwiftUI re-renders from the published state
```

### Round lifecycle

`startNewGame(daily:)`:

1. Picks a seed: `PuzzleGenerator.dailySeed` for Daily, otherwise random.
2. In Royal Decrees mode (not Daily) picks the decree: `.truce` when speed
   decrees are on, otherwise a seeded pick from Fog / Guard / Assassin / Lock.
   Assassin's Range sets `rules.diagonalTwo`.
3. Generates the puzzle, attaches the decree and, for Royal Guard, a Guard
   square from `PuzzleGenerator.pickGuard`.
4. Resets the board, history, moves, timer and revealed realms, then starts the
   timer and, for speed decrees, the 30-second rotation timer.

### History

`history` is an array of full `cells` snapshots with `historyIndex` pointing at
the current one. A new move after an undo drops the redo tail. Undo and redo
restore a snapshot, then recompute Auto-X owners and Fog reveals from it, so the
board always matches the live settings. Undoing out of a solved state hides the
victory card and resumes the timer.

### Smart Auto-X

- `applyAutoX(owner:)` inserts the Queen's position into `autoOwners` of each
  empty target square. It runs when Auto-X is on (full targets) or when the
  Color Lock decree is active (realm only).
- `stripAutoX(owner:)` removes that position from every square. A square whose
  owner set becomes empty and whose base is `.empty` displays as empty again.
- `recomputeAutoX()` clears every owner set and re-applies from the Queens on
  the board. It runs after toggling, undo/redo and Clear.

### Timers

A 0.25 s `Timer.publish` updates `elapsed`. Pausing folds the running time into
`frozen`; `RootView` pauses on background and resumes on active. Speed decrees
use a separate 30 s timer that calls `rotateDecree()`.

## Persistence

All `UserDefaults`, no files or network:

| Key | Owner | Value |
| --- | --- | --- |
| `queens.mode`, `queens.size`, `queens.autoX`, `queens.speed`, `queens.theme` | view model | settings |
| `queens.solved` | `StatsStore` | total solves |
| `queens.best.<size>` | `StatsStore` | best time in seconds for that size |
| `queens.daily.done` | `StatsStore` | array of completed Daily keys (`yyyy-MM-dd|size`) |

## Debug helpers

In DEBUG builds `QueensGameViewModel.autoSolveForTesting()` places the full
solution. Unit tests use it to exercise the win flow.
