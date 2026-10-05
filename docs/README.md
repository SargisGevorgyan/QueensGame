# QueensGame documentation

QueensGame is a "Queens" logic puzzle in two builds that share one design:

- **iOS app** (`QueensGame/`): SwiftUI + MVVM, iOS 17+. The only third-party
  dependency is Google Mobile Ads (rewarded hint videos).
- **Web version** (`queens/index.html`): a single self-contained HTML file,
  deployed to GitHub Pages at https://sargisgevorgyan.github.io/QueensGame/.

Both include the **Royal Decrees** mode, Daily and Random puzzles, Smart Auto-X,
and undo/redo.

## Pages

| Page | What it covers |
| --- | --- |
| [Game rules and modes](game-rules.md) | The puzzle, controls, Smart Auto-X, Royal Decrees, Daily puzzles, stats |
| [Architecture](architecture.md) | Code layout, data model, view model, data flow, persistence |
| [Puzzle generator](puzzle-generator.md) | How puzzles are built and proven unique, seeding, Daily keys |
| [Web version](web-version.md) | How `queens/index.html` is organised and where it differs from iOS |
| [Hints](hints.md) | Hint balance, rewarded AdMob videos, the hint pack, and AdMob / store setup |
| [Game Center](game-center.md) | iOS leaderboards, achievements, their ids, and App Store Connect setup |
| [Building and testing](building-and-testing.md) | XcodeGen, Xcode, `xcodebuild`, the test suite, Pages deploy |
| [Contributing](contributing.md) | Branching, conventions, keeping iOS and web in sync, PR checklist |

## In progress

These are being worked on in open pull requests or project threads and are not
on `main` yet, so the pages above do not describe them:

- iOS CI workflow that builds and runs the tests on every push.
- "Royal Pass" in-app purchase via StoreKit.
- An Android version.

Update the relevant page when one of them lands.
