# Game rules and modes

## The puzzle

The board is an N×N grid (6×6 to 9×9) split into N coloured **realms**. Place
exactly N Queens so that:

1. every **rank** (row) holds exactly one Queen,
2. every **file** (column) holds exactly one Queen,
3. every **realm** holds exactly one Queen,
4. no two Queens **touch**, orthogonally or diagonally.

Every generated puzzle has exactly one solution (see
[Puzzle generator](puzzle-generator.md)), so it can always be solved by logic
alone.

### Feedback while you play

- Rank and file numerals along the edges are struck through once that line holds
  a single, non-conflicting Queen.
- Realm chips (Roman numerals I–IX) under the board tick off the same way.
- Queens that break a rule are highlighted in red and the board shakes (with a
  warning haptic) whenever the number of conflicts goes up.
- The status line shows `placed / N queens placed` and the conflict count.
- Solving the board stops the timer, plays confetti and shows the victory card
  with time and moves.

## Controls (iOS)

| Gesture | Action |
| --- | --- |
| Tap | Cycle the square: empty → × → 👑 → empty |
| Long-press | Place (or lift) a Queen directly |
| Drag (≥16 pt) | Paint × marks across squares; starting the drag on a manual × erases instead |

The control dock under the board has **Undo**, **Redo** and **Clear board**.
The header has the **Auto-X** toggle and a menu with *New random puzzle*,
*Daily puzzle*, *Board size*, *Theme* (System / Light / Dark), *Speed decrees*
(Royal Decrees mode only) and *How to play*.

The timer pauses when the app goes to the background and resumes when it comes
back.

The web version adds mouse and keyboard shortcuts; see
[Web version](web-version.md#controls).

## Smart Auto-X

Auto-X is a helper you can switch on or off at any time from the header. When a
Queen is placed, every **empty** square it rules out gets a faint, automatic ×:

- the 8 neighbouring squares,
- the rest of its rank and file,
- the rest of its colour realm.

Auto marks are tracked by **ownership**, not as a plain flag. Each square
remembers which Queens put an × on it, so:

- Lifting a Queen removes only *its* claims. A square another Queen still rules
  out keeps its ×.
- Squares you marked yourself are never touched by Auto-X.
- Tapping an auto × turns it into a manual × (it then stays even if its Queen is
  removed). Tapping it again makes it a Queen, as usual.
- Turning Auto-X off removes every auto-only × and keeps your own. Turning it
  back on recomputes the marks from the Queens on the board.
- Undo, redo and Clear also recompute the marks from the board, so they always
  match the current settings.

The implementation is described in [Architecture](architecture.md#smart-auto-x).

## Royal Decrees mode

Pick **Royal Decrees** in the mode switch and every new random round opens with
one decree, chosen at random and shown in a banner above the board:

| Decree | Effect |
| --- | --- |
| **The Fog of War** | Realm colours and walls stay hidden until you touch a square inside that realm. |
| **The Royal Guard** | One square (never part of the solution) holds a Guard. It acts like a permanent × and can't be tapped or marked. |
| **Assassin's Range** | Queens also clash when exactly two squares apart on a diagonal. The puzzle is generated with this rule, so it is still uniquely solvable. |
| **Color Lock** | Placing a Queen auto-marks × on the rest of its realm, even with Auto-X switched off. |

### Speed decrees

With Royal Decrees on, the menu offers **Speed decrees (30s)**. A round then
starts under **Royal Truce** (no decree), and every 30 seconds the herald
rotates to the next decree in the cycle Fog of War → Royal Guard → Color Lock →
Royal Truce. Assassin's Range is left out of the rotation because it changes
which solution is valid, so it can only apply to a whole round.

When the rotation switches to Fog of War, only realms you have already marked
stay revealed. When it switches to Royal Guard, the Guard lands on an empty
square that isn't part of the solution.

## Daily puzzle

*Daily puzzle* builds the same board for everyone on a given local date and
board size, so it can be shared and compared. Daily puzzles always use standard
rules (no decree), even in Royal Decrees mode. A completed Daily shows as
**Daily ✓** in the menu.

The iOS and web builds use different random number generators, so the Daily
board for a date differs between them.

## Stats

The iOS app stores, in `UserDefaults`:

- total puzzles solved,
- best time per board size,
- the set of completed Daily puzzles.

It also remembers your mode, board size, Auto-X setting, speed decrees and theme
between launches.
