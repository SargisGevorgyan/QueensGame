//
//  PremiumStore.swift
//  QueensGame
//
//  StoreKit 2 wrapper for the one-time "Royal Pass" unlock (non-consumable)
//  and the consumable hint pack. Products, purchases, restores and refunds
//  all go through Apple's StoreKit. The last known entitlement is cached in
//  UserDefaults so the app starts unlocked offline, then re-verified.
//  Bought hints are credited to the `HintWallet` before the transaction is
//  finished, so an interrupted purchase is redelivered, never lost.
//

import Foundation
import StoreKit

@MainActor
final class PremiumStore: ObservableObject {

    /// Must match the product configured in App Store Connect and in
    /// `QueensGame.storekit` (used for local testing).
    static let premiumProductID = "com.app.queensgame.premium"
    /// Consumable: adds `hintPackSize` hints.
    static let hintPackProductID = "com.app.queensgame.hints10"
    static let hintPackSize = 10

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
    @Published private(set) var hintPack: Product?
    @Published private(set) var purchaseState: PurchaseState = .idle
    @Published private(set) var hintPurchaseState: PurchaseState = .idle
    @Published var showPaywall = false

    private static let cacheKey = "queens.premium"
    private let defaults: UserDefaults
    private let hints: HintWallet
    /// Lives for the app's lifetime (the store is owned by the App struct).
    private var updatesTask: Task<Void, Never>?

    init(hints: HintWallet, defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.hints = hints
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
        guard product == nil || hintPack == nil else { return }
        do {
            let products = try await Product.products(for: [Self.premiumProductID, Self.hintPackProductID])
            product = products.first { $0.id == Self.premiumProductID }
            hintPack = products.first { $0.id == Self.hintPackProductID }
        } catch {
            product = nil
            hintPack = nil
        }
    }

    /// Localized hint pack price, e.g. "$0.99".
    var hintPackPrice: String? { hintPack?.displayPrice }

    /// Buys the consumable hint pack; the hints land in the wallet.
    func purchaseHintPack() async {
        if hintPack == nil { await loadProduct() }
        guard let hintPack else {
            hintPurchaseState = .failed("The store is unavailable right now. Please try again later.")
            return
        }
        hintPurchaseState = .purchasing
        do {
            switch try await hintPack.purchase() {
            case .success(let verification):
                await handle(verification)
                hintPurchaseState = .idle
                hints.showOffer = false
            case .pending:
                hintPurchaseState = .pending
            case .userCancelled:
                hintPurchaseState = .idle
            @unknown default:
                hintPurchaseState = .idle
            }
        } catch {
            hintPurchaseState = .failed(error.localizedDescription)
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
        switch transaction.productID {
        case Self.premiumProductID:
            isPremium = transaction.revocationDate == nil
        case Self.hintPackProductID:
            if transaction.revocationDate == nil {
                hints.credit(Self.hintPackSize * max(1, transaction.purchasedQuantity))
                if hintPurchaseState == .pending { hintPurchaseState = .idle }
            }
        default:
            break
        }
        await transaction.finish()
    }

    func clearError() {
        if case .failed = purchaseState { purchaseState = .idle }
        if case .failed = hintPurchaseState { hintPurchaseState = .idle }
    }
}
