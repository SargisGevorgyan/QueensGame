# Contributing

## Workflow

1. Branch from `main` (for example `feature/hint-button` or `fix/fog-reveal`).
2. Make the change, with tests where the logic is testable.
3. Run the test suite (see [Building and testing](building-and-testing.md)).
4. Open a pull request against `main` and describe what a player would notice.

`main` should always build and pass its tests.

## Where code goes

| Kind of change | Put it in |
| --- | --- |
| Rules, validation, generation, anything without UI | `QueensGame/Models/` as pure Swift (no SwiftUI import) |
| Game state, input handling, timers | `QueensGameViewModel` |
| Layout and visuals | `QueensGame/Views/` |
| Colours | `QColor` in `Support/Theme.swift`, never hard-coded in views |
| Haptics | `Haptics.shared` |

Views read state from the view model and call its methods; they don't mutate
`cells` or `puzzle` themselves.

## Conventions

- Swift 5, iOS 17 APIs, SwiftUI only. Don't add third-party dependencies
  without discussing it first.
- Match the existing style: a file header comment, `// MARK: -` sections,
  doc comments on non-obvious types and functions.
- Every colour needs a light and a dark value (`Color(hexLight:hexDark:)`).
  Check new UI in both themes.
- Respect Reduce Motion for decorative animation, as `AnimatedBackground` and
  `ConfettiOverlay` do.
- Keep generation deterministic for a seed; see
  [Puzzle generator](puzzle-generator.md#changing-the-generator).
- New `UserDefaults` keys use the `queens.` prefix and should be listed in
  [Architecture](architecture.md#persistence).
- When adding or renaming files, update `project.yml` if needed, run
  `xcodegen generate`, and commit the regenerated project.

## Adding a decree

1. Add a case to `Decree` with `title`, `blurb` and `systemImage`.
2. If it changes which placements are legal, add a flag to `RuleSet` and honour
   it in the generator and in `BoardRules.analyse`.
3. Wire its behaviour into `QueensGameViewModel` (`startNewGame`, input
   handlers, and `rotateDecree` if it should join Speed decrees).
4. Mention it in `HowToPlayView` and in [Game rules](game-rules.md).
5. Add it to the web version, or note in the PR that it is iOS-only for now.
6. Add tests for any new rule.

## Keeping iOS and web in sync

The two builds are meant to feel like one game: the same rules, decrees,
palette (the realm pastels in `Theme.swift` mirror the web CSS) and Auto-X
behaviour. When you change gameplay in one, either make the matching change in
the other or say in the PR that it is intentionally one-sided.
[Web version](web-version.md#differences-from-ios) lists the known differences.

## Pull request checklist

- [ ] Builds and all tests pass.
- [ ] New logic has unit tests.
- [ ] Checked in light and dark mode, and on a small iPhone.
- [ ] `project.yml` and the regenerated project are committed if files changed.
- [ ] Web version updated, or the PR says why not.
- [ ] Docs in `docs/` updated if behaviour or structure changed.
