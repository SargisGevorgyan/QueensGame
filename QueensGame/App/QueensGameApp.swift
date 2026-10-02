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
    @StateObject private var store = PremiumStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(game)
                .environmentObject(store)
                .tint(QColor.accent)
        }
    }
}
