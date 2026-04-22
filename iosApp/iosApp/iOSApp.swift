import SwiftUI
import SampleCompose

@main
struct iOSApp: App {
    init() {
        TrustWalletCoreRuntime.shared.installIosAdapter(adapter: RealTrustWalletCoreAdapter())
        SecureWalletStorageRuntime.shared.install(storage: KeychainSecureWalletStorage())
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

