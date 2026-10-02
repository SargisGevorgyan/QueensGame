//
//  QueensGameApp.swift
//  QueensGame
//
//  A Queens logic puzzle built with pure SwiftUI (MVVM), zero dependencies.
//  Includes the unique "Royal Decrees" mode with per-round rule modifiers.
//

import SwiftUI

@main
struct QueensGameApp: App {
    @StateObject private var game = QueensGameViewModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(game)
                .tint(QColor.accent)
        }
    }
}
