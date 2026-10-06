import SwiftUI
import SampleCompose

@main
struct iOSApp: App {
    init() {
        SecureWalletStorageRuntime.shared.install(storage: KeychainSecureWalletStorage())
        KoinInitKt.doInitKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

