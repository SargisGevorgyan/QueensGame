//
//  PremiumStore.swift
//  QueensGame
//
//  StoreKit 2 wrapper for the one-time "Royal Pass" unlock (non-consumable).
//  Zero dependencies: products, purchases, restores and refunds all go
//  through Apple's StoreKit. The last known entitlement is cached in
//  UserDefaults so the app starts unlocked offline, then re-verified.
//

import Foundation
import StoreKit

@MainActor
final class PremiumStore: ObservableObject {

    /// Must match the product configured in App Store Connect and in
    /// `QueensGame.storekit` (used for local testing).
    static let premiumProductID = "com.app.queensgame.premium"

    enum PurchaseState: Equatable {
        case idle
        case purchasing
        case pending          // Ask to Buy / SCA — finishes later via Transaction.updates
        case failed(String)
    }

    @Published private(set) var isPremium: Bool {
        didSet { defaults.set(isPremium, forKey: Self.cacheKey) }
    }
    @Published private(set) var product: Product?
    @Published private(set) var purchaseState: PurchaseState = .idle
    @Published var showPaywall = false

    private static let cacheKey = "queens.premium"
    private let defaults: UserDefaults
    /// Lives for the app's lifetime (the store is owned by the App struct).
    private var updatesTask: Task<Void, Never>?

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-UITestPremium") {
            self.isPremium = true
            return
        }
        #endif
        self.isPremium = defaults.bool(forKey: Self.cacheKey)

        updatesTask = Task { [weak self] in
            for await update in Transaction.updates {
                await self?.handle(update)
            }
        }
        Task {
            await loadProduct()
            await refreshEntitlements()
        }
    }

    /// Localized price for the paywall button, e.g. "$2.99".
    var displayPrice: String? { product?.displayPrice }

    /// Whether the given mode can be played right now.
    func canPlay(_ mode: GameMode) -> Bool { isPremium || !mode.requiresPremium }

    // MARK: - StoreKit

    func loadProduct() async {
        guard product == nil else { return }
        do {
            product = try await Product.products(for: [Self.premiumProductID]).first
        } catch {
            product = nil
        }
    }

    func purchase() async {
        if product == nil { await loadProduct() }
        guard let product else {
            purchaseState = .failed("The store is unavailable right now. Please try again later.")
            return
        }
        purchaseState = .purchasing
        do {
            switch try await product.purchase() {
            case .success(let verification):
                await handle(verification)
                purchaseState = .idle
                if isPremium { showPaywall = false }
            case .pending:
                purchaseState = .pending
            case .userCancelled:
                purchaseState = .idle
            @unknown default:
                purchaseState = .idle
            }
        } catch {
            purchaseState = .failed(error.localizedDescription)
        }
    }

    /// "Restore Purchases": forces a sync with the App Store, then re-reads
    /// entitlements. Required by App Review for non-consumables.
    func restore() async {
        purchaseState = .purchasing
        do {
            try await AppStore.sync()
        } catch {
            // The user may cancel the sign-in sheet; entitlements are still re-read.
        }
        await refreshEntitlements()
        purchaseState = isPremium ? .idle : .failed("No previous purchase was found for this Apple ID.")
        if isPremium { showPaywall = false }
    }

    func refreshEntitlements() async {
        var owned = false
        for await result in Transaction.currentEntitlements {
            if case .verified(let transaction) = result,
               transaction.productID == Self.premiumProductID,
               transaction.revocationDate == nil {
                owned = true
            }
        }
        isPremium = owned
    }

    private func handle(_ result: VerificationResult<Transaction>) async {
        guard case .verified(let transaction) = result else { return }
        if transaction.productID == Self.premiumProductID {
            isPremium = transaction.revocationDate == nil
        }
        await transaction.finish()
    }

    func clearError() {
        if case .failed = purchaseState { purchaseState = .idle }
    }
}
