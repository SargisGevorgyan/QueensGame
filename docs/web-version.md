# Web version

`queens/index.html` is a complete, dependency-free web build of the game in a
single file: HTML markup, a `<style>` block with light and dark theme variables,
and one `<script>` block. It has no build step. Open the file in a browser, or
serve the folder:

```bash
cd queens && python3 -m http.server 8000   # then open http://localhost:8000
```

It is deployed to GitHub Pages at
https://sargisgevorgyan.github.io/QueensGame/ by
`.github/workflows/pages.yml` on every push to `main` that touches `queens/`.

## Script layout

The script is organised into commented sections, in this order:

| Section | Contents |
| --- | --- |
| RNG + helpers | `mulberry32` seeded RNG, `hashStr` (FNV-1a), `roman` |
| Generator | `buildSolution`, `growRegions`, `countSolutions`, `generate` |
| Decree metadata | `DECREES` names and descriptions |
| State | the global `S` object: puzzle, cells, history, timer, settings |
| Persistence | `localStorage` settings, theme and Daily completion |
| New puzzle | `newPuzzle({daily})`, `pickGuard`, `pickGuardLive` |
| Cell model | cells are `{ b: "empty" \| "manualX" \| "queen", o: [ownerKeys] }`, same model as iOS |
| History | `commit`, `undo`, `redo` over cell snapshots |
| Validation | `analyse` |
| Auto-X | `autoXTargets`, `applyAutoX`, `stripAutoX`, `recomputeAutoX` |
| Rendering | `buildBoard` (DOM), `paint` (update classes), realm walls |
| Input | pointer, double-click, context menu and keyboard handlers |
| Timer, speed decrees, win, confetti, modals, theme | the rest of the UI |

## Controls

On top of the iOS gestures (tap to cycle, long-press for a Queen, drag to mark):

| Input | Action |
| --- | --- |
| Double-click | Place or lift a Queen |
| Right-click | Toggle a manual × |
| Arrow keys | Move focus between squares |
| Enter / Space | Cycle the focused square |
| Ctrl/⌘+Z, Ctrl/⌘+Shift+Z or Ctrl/⌘+Y | Undo, redo |
| Escape | Close the open dialog |

## Differences from iOS

| Area | iOS | Web |
| --- | --- | --- |
| RNG | SplitMix64 (`SeededRNG`) | `mulberry32` |
| Uniqueness | grow realms, then **repair** them until unique | grow realms and **reject** until `countSolutions(...) === 1` (up to 80 × 45 tries) |
| Auto-X reach | always neighbours + rank + file + realm | two scopes: *Adjacent* (neighbours + rank + file) and *Aggressive* (adds the realm) |
| Daily | same date and size give the same board on iOS | same on web, but a different board from iOS, since the RNG differs |
| Pausing | on app background | on tab hidden, and while a dialog is open |
| Storage | `UserDefaults` | `localStorage` keys `queens.settings`, `queens.theme`, `queens.daily` |

Gameplay rules, decrees, speed-decree rotation, Guard placement and the cell
ownership model are the same.

## Generator

The web generator follows the same first two steps as iOS (backtracked
solution, flood-grown realms), then counts solutions and keeps the board only
when there is exactly one. See [Puzzle generator](puzzle-generator.md) for the
shared ideas.
