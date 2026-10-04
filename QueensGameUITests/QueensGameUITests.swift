//
//  QueensGameUITests.swift
//  QueensGameUITests
//

import XCTest

final class QueensGameUITests: XCTestCase {

    override func setUp() {
        continueAfterFailure = false
    }

    /// Game Center stays off so its sign-in sheet never covers the board.
    private func freshApp() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += ["-DisableGameCenter"]
        return app
    }

    /// Launches with the Royal Pass unlocked (DEBUG-only launch argument).
    private func premiumApp() -> XCUIApplication {
        let app = freshApp()
        app.launchArguments += ["-UITestPremium"]
        return app
    }

    func testLaunchShowsBoardModeToggleAndAutoX() {
        let app = premiumApp()
        app.launch()

        XCTAssertTrue(app.buttons["Standard"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Royal Decrees"].exists)
        XCTAssertTrue(app.buttons["Auto-X"].exists)          // header toggle
    }

    func testAutoXToggleAndDecreesModeKeepBoardInteractive() {
        let app = premiumApp()
        app.launch()

        let autoX = app.buttons["Auto-X"]
        XCTAssertTrue(autoX.waitForExistence(timeout: 5))
        autoX.tap()                                          // flip it — must not crash

        app.buttons["Royal Decrees"].tap()
        XCTAssertTrue(app.buttons["Clear board"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Auto-X"].exists)
    }

    func testLockedDecreesOpensPaywall() {
        let app = freshApp()
        app.launch()

        let locked = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Royal Decrees'")).firstMatch
        XCTAssertTrue(locked.waitForExistence(timeout: 5))
        locked.tap()

        XCTAssertTrue(app.buttons["paywall.restore"].waitForExistence(timeout: 5))
        app.buttons["Close"].tap()
        XCTAssertTrue(app.buttons["Clear board"].waitForExistence(timeout: 5))
    }

    func testHintButtonPlacesAQueen() {
        let app = freshApp()
        app.launchArguments += ["-UITestHintBalance", "3"]
        app.launch()

        let hint = app.buttons["dock.hint"]
        XCTAssertTrue(hint.waitForExistence(timeout: 5))
        hint.tap()
        XCTAssertTrue(app.staticTexts["1 / \(boardSize(app)) queens placed"].waitForExistence(timeout: 5))
    }

    /// With no hints left the offer sheet opens; a (stubbed) video earns one.
    func testOutOfHintsOffersVideoAndPack() {
        let app = freshApp()
        app.launchArguments += ["-UITestHintBalance", "0", "-UITestInstantAds"]
        app.launch()

        let hint = app.buttons["dock.hint"]
        XCTAssertTrue(hint.waitForExistence(timeout: 5))
        hint.tap()

        let watch = app.buttons["hints.watchAd"]
        XCTAssertTrue(watch.waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["hints.buyPack"].exists)
        watch.tap()

        let closed = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: watch)
        XCTAssertEqual(XCTWaiter().wait(for: [closed], timeout: 5), .completed)
        hint.tap()
        XCTAssertTrue(app.staticTexts["1 / \(boardSize(app)) queens placed"].waitForExistence(timeout: 5))
    }

    /// Reads N from the "0 / N queens placed" status line.
    private func boardSize(_ app: XCUIApplication) -> Int {
        let status = app.staticTexts.matching(NSPredicate(format: "label CONTAINS 'queens placed'")).firstMatch
        _ = status.waitForExistence(timeout: 5)
        let parts = status.label.split(separator: " ")
        return parts.count > 2 ? Int(parts[2]) ?? 8 : 8
    }
}
