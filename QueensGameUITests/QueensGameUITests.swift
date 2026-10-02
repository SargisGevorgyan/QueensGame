//
//  QueensGameUITests.swift
//  QueensGameUITests
//

import XCTest

final class QueensGameUITests: XCTestCase {

    override func setUp() {
        continueAfterFailure = false
    }

    func testLaunchShowsBoardModeToggleAndAutoX() {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.buttons["Standard"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Royal Decrees"].exists)
        XCTAssertTrue(app.buttons["Auto-X"].exists)          // header toggle
    }

    func testAutoXToggleAndDecreesModeKeepBoardInteractive() {
        let app = XCUIApplication()
        app.launch()

        let autoX = app.buttons["Auto-X"]
        XCTAssertTrue(autoX.waitForExistence(timeout: 5))
        autoX.tap()                                          // flip it — must not crash

        app.buttons["Royal Decrees"].tap()
        XCTAssertTrue(app.buttons["Clear board"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Auto-X"].exists)
    }
}
