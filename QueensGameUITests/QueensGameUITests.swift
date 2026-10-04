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
}
