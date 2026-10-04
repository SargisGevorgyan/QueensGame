# Hints, rewarded ads and the hint pack

Both the iOS and Android apps have a **Hint** button (lightbulb) in the control
dock, with the remaining balance on a badge.

## How a hint works

A hint spends one step of the puzzle's known solution:

1. If any Queen is on a wrong square, the first one is lifted.
2. Otherwise the next row's missing Queen is placed on its correct square.

The changed square gets a gold ring until the next move. A hint counts as a
move and can be undone. Nothing is spent when the board is already solved.

Logic: `QueensGameViewModel.applyHint()` (iOS) and `GameEngine.applyHint()`
(Android `core`), both unit tested.

## The balance

`HintWallet` (iOS `Services/HintWallet.swift`, Android `core/HintWallet.kt`)
stores the balance locally:

- New players start with **3** free hints.
- Each new calendar day adds **1** free hint, up to 3. Bought hints above 3 are
  kept.

## Out of hints

Tapping Hint with a balance of 0 opens the offer sheet:

| Option | Gives | Implementation |
| --- | --- | --- |
| Watch a video | +1 hint | Google AdMob rewarded ad |
| Buy hint pack | +10 hints | Consumable `com.app.queensgame.hints10` (StoreKit 2 / Play Billing) |

### Rewarded ads

AdMob sits behind a small interface so the rest of the app never imports the
SDK:

- iOS: `RewardedAdService` in `Services/RewardedAds.swift`; the AdMob code is
  in `Services/AdMobRewardedAds.swift`. The SDK comes from Swift Package
  Manager (`googleads/swift-package-manager-google-mobile-ads`, 12.x).
- Android: `RewardedAdService` in `ads/RewardedAds.kt`; the AdMob code is in
  `ads/AdMobRewardedAds.kt` (`play-services-ads`).

The SDK starts only when a player first taps **Watch a video**. On iOS the App
Tracking Transparency prompt is shown right before that, never at launch.

The repo ships with **Google's public test ids**, which always serve test ads:

| | iOS | Android |
| --- | --- | --- |
| App id | `ca-app-pub-3940256099942544~1458002511` (`GADApplicationIdentifier` in `Info.plist`) | `ca-app-pub-3940256099942544~3347511713` (`admobAppId` in `app/build.gradle.kts`) |
| Rewarded unit | `ca-app-pub-3940256099942544/1712485313` (`AdMobRewardedAds.adUnitID`) | `ca-app-pub-3940256099942544/5224354917` (`AdMobRewardedAds.AD_UNIT_ID`) |

### Hint pack purchase

- iOS: `PremiumStore.purchaseHintPack()`. Hints are credited to the wallet
  before the transaction is finished, so an interrupted purchase is
  redelivered through `Transaction.updates`. The product is in
  `QueensGame.storekit` for local testing.
- Android: `PremiumStore.purchaseHintPack()`. The purchase is consumed first
  and hints are credited only when Play confirms; unconsumed packs are picked
  up on the next refresh.

## Before release

1. **AdMob account**: create an AdMob app for iOS and one for Android, and a
   *Rewarded* ad unit in each. Replace the four test ids above.
2. **Privacy & messaging** in AdMob: set up a GDPR consent message (and US
   state message if needed). The app does not yet show Google's UMP consent
   form; add it before serving real ads in the EEA/UK.
3. **app-ads.txt**: publish it on the developer website listed in both stores.
4. **iOS `SKAdNetworkItems`**: `Info.plist` lists Google's own id only. Paste
   the full list from Google's "SKAdNetwork" docs page before release.
5. **App Store Connect**: create the consumable in-app purchase
   `com.app.queensgame.hints10` ("10 Hints", suggested $0.99), and update the
   App Privacy answers (Identifiers, Usage Data, used for third-party
   advertising; tracking if ATT is allowed).
6. **Play Console**: create the in-app product `com.app.queensgame.hints10`,
   declare "Contains ads", and update the Data safety form (advertising ID,
   app interactions, device ids shared with Google).
