# Building and testing

## Requirements

- macOS with Xcode 16 or later (the project targets iOS 17.0).
- [XcodeGen](https://github.com/yonaskolb/XcodeGen) 2.4+ if you change targets,
  files or build settings: `brew install xcodegen`.

No package manager or third-party dependencies are needed.

## The Xcode project

`project.yml` is the source of truth. `QueensGame.xcodeproj` is generated from
it and also committed, so you can open it without XcodeGen. After adding,
removing or renaming files, or changing settings, regenerate and commit both:

```bash
xcodegen generate
```

`project.yml` defines three targets and one shared scheme:

| Target | Type | Bundle id |
| --- | --- | --- |
| `QueensGame` | iOS app (iPhone + iPad) | `com.app.queensgame` |
| `QueensGameTests` | unit tests | `com.app.queensgame.tests` |
| `QueensGameUITests` | UI tests | `com.app.queensgame.uitests` |

`DEVELOPMENT_TEAM` is empty, which is fine for the Simulator. Set your team in
Xcode (Signing & Capabilities) to run on a device; don't commit it.

## Run

```bash
open QueensGame.xcodeproj
```

Choose the **QueensGame** scheme and an iOS 17+ Simulator, then ⌘R.

## Test

In Xcode: ⌘U. From the command line:

```bash
# Only needed if xcode-select points at the Command Line Tools
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer

xcodebuild -project QueensGame.xcodeproj -scheme QueensGame \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test
```

Use any Simulator you have installed (`xcrun simctl list devices available`).
To run only the unit tests, add `-only-testing:QueensGameTests`.

### What the tests cover

`QueensGameTests/QueensGameTests.swift` (16 tests):

| Area | Tests |
| --- | --- |
| Generator | puzzles are unique, contiguous and rule-compliant for 6–9; Daily is deterministic; Assassin's Range solutions avoid the diagonal-two clash |
| Validator | duplicate in a row, touching Queens, Assassin's Range clash are flagged; the full solution solves the board |
| Auto-X | target geometry; neighbour fill keeps manual marks; tapping an auto × promotes it; shared ownership between Queens; toggle off/on; rank, file and realm fill |
| View model | tap cycle with undo/redo; Clear board; auto-solve triggers the win |

View-model tests build `QueensGameViewModel(defaults:)` with a throwaway
`UserDefaults` suite so they don't touch real settings.

`QueensGameUITests/QueensGameUITests.swift` (2 tests) launches the app, checks
the mode switch and the Auto-X toggle exist, flips Auto-X, switches to Royal
Decrees and checks the board controls are still there. They find controls by
their labels (`Standard`, `Royal Decrees`, `Auto-X`, `Clear board`), so renaming
those labels breaks the UI tests.

## Web version

No build. Open `queens/index.html` directly or serve the folder (see
[Web version](web-version.md)). It has no automated tests, so check changes by
hand in a browser, in light and dark themes, at phone width.

## Continuous integration and deploys

- **GitHub Pages:** `.github/workflows/pages.yml` uploads `queens/` and deploys
  it on pushes to `main` that change `queens/**` or the workflow, and on manual
  dispatch.
- **iOS CI:** not on `main` yet; a workflow that builds and tests on a macOS
  runner is in progress.
