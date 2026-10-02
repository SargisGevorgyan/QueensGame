# QueensGame

A clone of the "Queens" logic puzzle (LinkedIn-style) built with **pure SwiftUI +
MVVM** and **zero external dependencies**, plus a unique **Royal Decrees** mode
that bends the board's rules each round.

It's the native counterpart to the web build in [`queens/`](queens/) —
same puzzle engine, same palette, same feature set.

**▶ Play the web version:** https://sargisgevorgyan.github.io/QueensGame/

---

## Run it

```bash
cd ios/QueensGame
xcodegen generate          # produces QueensGame.xcodeproj (XcodeGen 2.4+)
open QueensGame.xcodeproj
```

Select the **QueensGame** scheme and an iOS 17+ Simulator, then ⌘R.

> No XcodeGen? `brew install xcodegen`.

Command line (this machine's `xcode-select` points at CommandLineTools, so export
the full Xcode first):

```bash
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
xcodebuild -project QueensGame.xcodeproj -scheme QueensGame \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test
```

18 tests (16 unit + 2 UI) cover the generator (uniqueness, contiguity, rule
compliance, deterministic Daily seeding), the validator (row/column/realm
duplicates, touching Queens, Assassin's Range), the Smart Auto-X ownership
system (fill, smart removal, manual-mark preservation, shared ownership,
promote-to-manual, toggle sweep), and the view model's tap / undo / redo /
clear / win flow.

---

## How it plays

| Gesture | Action |
| --- | --- |
| Tap | cycle a square: empty → × → 👑 → empty |
| Long-press | drop a Queen directly |
| Drag (≥16 pt) | sweep × marks across squares (drag from an × to erase) |

**Goal:** one Queen per rank, per file, and per coloured realm, with no two
Queens touching (orthogonally *or* diagonally). Rank/file numerals strike through
as they're satisfied, realm chips tick off, rule-breaking Queens flash red, and a
confetti victory card lands when the board is solved.

Quality-of-life: timer + move counter, unlimited undo/redo, Clear board,
**Daily** (date-seeded, reproducible) and Random puzzles at **6×6 – 9×9**,
light/dark/system theme, and a How-to-Play sheet.

### Smart Auto-X

Toggle from the **header** (on at any time). When a Queen is placed, every empty
square it rules out is auto-marked with a faint × — the **8 adjacent** squares,
the **whole rank & file**, and the **whole colour realm**.

Marks are ownership-tracked, not a boolean: each `Cell` keeps
`base` (`empty` / `manualX` / `queen`) and `autoOwners: Set<GridPos>` — the set of
Queens that auto-filled it (the spec's `manuallyMarked` and `generatedByQueens`).

- Removing a Queen strips **only its** id from every square; a square whose owner
  set empties *and* was never manually marked reverts to empty. Marks another
  Queen still rules out, or ones you've tapped, stay put.
- Tapping an auto-× **claims** it (`base → manualX`) so it survives its Queen's
  removal.
- Toggling Auto-X off sweeps every auto-only mark and keeps your manual ones;
  toggling back on re-derives them from the Queens on the board.

Covered by 8 unit tests in `QueensGameTests` plus the `autoXTargets` geometry test.

---

## Royal Decrees mode

Toggle **Royal Decrees** and each new round a herald proclaims one modifier:

| Decree | Effect |
| --- | --- |
| **The Fog of War** | Realms stay hidden until you touch a square inside them. |
| **The Royal Guard** | A fixed Guard holds its square like a permanent ×. |
| **Assassin's Range** | Queens also clash two squares away along a diagonal (baked into generation, so the puzzle stays solvable). |
| **Color Lock** | Placing a Queen auto-marks × on the rest of its realm. |

Enable **Speed decrees** (menu) to have the decree rotate every 30 seconds
mid-round.

---

## Architecture

```
QueensGame/
├── App/            QueensGameApp — @main, injects the single view model
├── Models/
│   ├── GameModels          value types (CellMark, GridPos, Puzzle, Decree, …)
│   ├── PuzzleGenerator     seeded generator: backtrack solution → grow regions
│   │                       → repair regions until the solution is unique
│   ├── BoardRules          pure validation + the Auto-X / Color Lock overlay
│   └── StatsStore          UserDefaults: total solves, best time, Daily set
├── ViewModels/
│   └── QueensGameViewModel  board state, input, undo/redo history, timer,
│                            decree rotation  (@MainActor ObservableObject)
├── Views/          RootView, BoardArea + CellView, DecreeBanner, RealmChips,
│                   ControlDock, GameOverView, HowToPlayView, AnimatedBackground,
│                   ConfettiOverlay
├── Support/        Theme (adaptive colour tokens), ShakeEffect
└── Services/       Haptics
```

The generator is the interesting part: a random region grow rarely yields a
unique puzzle, so `repairToUnique` finds an alternate solution, moves one of its
boundary cells into a neighbouring realm (checking contiguity), and repeats until
the intended solution is the only one — falling back to a fresh solution if it
stalls.
