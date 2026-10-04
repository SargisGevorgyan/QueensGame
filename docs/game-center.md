# Game Center (iOS)

The iOS app signs the player in to Game Center on launch, submits Daily times to
per-size leaderboards, and reports achievements. All of it is optional: when the
player is not signed in, or the app is not registered for Game Center yet, every
call is a silent no-op and the game plays the same.

Code:

- `QueensGame/Services/GameCenterCatalog.swift`: every leaderboard and
  achievement id, plus the pure rules that turn local stats into achievement
  progress. Change ids here only.
- `QueensGame/Services/GameCenterManager.swift`: GameKit sign-in, score and
  achievement reporting, and opening Game Center's own screens.
- `QueensGame/QueensGame.entitlements`: the Game Center entitlement
  (`CODE_SIGN_ENTITLEMENTS` in `project.yml`).
- The **⋯** menu shows **Leaderboards** and **Achievements** once signed in.

## Behaviour

- **Leaderboards** take only the *first* solve of each day's Daily for that
  board size, so replaying a board you already know never counts. Random and
  Royal Decrees rounds do not post scores.
- **Achievements** are reported only when their progress moves forward, so each
  completion banner shows once.
- On sign-in the app catches up: it re-submits the best stored Daily time per
  size and reports any achievement progress earned while signed out.
- Until App Store Connect is set up, sign-in fails with `GKError` code 15
  ("game unrecognized"). That is expected; the app logs it and carries on.
- UI tests pass `-DisableGameCenter`, and unit-test hosts skip sign-in, so a
  Game Center sheet never covers the board in tests.

## Leaderboards

Classic leaderboards. Score format **Elapsed Time – To the Second**, sort
**Low to High**, score range 1 to 86400. Submitted value is whole seconds.

| Leaderboard ID | Suggested name |
| --- | --- |
| `queens.daily.best.6x6` | Daily 6×6 – Best Time |
| `queens.daily.best.7x7` | Daily 7×7 – Best Time |
| `queens.daily.best.8x8` | Daily 8×8 – Best Time |
| `queens.daily.best.9x9` | Daily 9×9 – Best Time |

## Achievements

| Achievement ID | Title | Description | Progress |
| --- | --- | --- | --- |
| `queens.ach.first_solve` | Long Live the Queen | Solve your first puzzle. | one-shot |
| `queens.ach.solves_25` | Court Regular | Solve 25 puzzles. | incremental |
| `queens.ach.solves_100` | Monarch of Logic | Solve 100 puzzles. | incremental |
| `queens.ach.first_daily` | Daily Audience | Solve a Daily puzzle. | one-shot |
| `queens.ach.daily_streak_3` | Three-Day Reign | Solve a Daily puzzle 3 days in a row. | incremental |
| `queens.ach.daily_streak_7` | Week on the Throne | Solve a Daily puzzle 7 days in a row. | incremental |
| `queens.ach.first_decree` | By Royal Decree | Win a Royal Decrees round. | one-shot |
| `queens.ach.decrees_10` | Herald's Favourite | Win 10 Royal Decrees rounds. | incremental |
| `queens.ach.nine_under_3min` | Swift Sovereign | Solve a 9×9 puzzle in under 3 minutes. | one-shot |

Achievements also need point values (up to 100 each, 1000 total), a "hidden"
choice, and a 512×512 or 1024×1024 image in App Store Connect.

## App Store Connect setup

1. Pick the final bundle id and set it (and `DEVELOPMENT_TEAM`) in `project.yml`.
2. In the Apple Developer portal, enable the **Game Center** capability on that
   App ID (it is on by default for new App IDs).
3. In App Store Connect, open the app → **Features → Game Center**, and enable
   Game Center for the version you are shipping.
4. Add the four leaderboards and nine achievements above with exactly these ids.
5. On the app version page, under **Game Center**, add the leaderboards and
   achievements to the version so they go live with it.
6. Test with a Sandbox or TestFlight account signed in under
   Settings → Game Center. Error 15 should be gone once steps 3–4 are done.
