import SwiftUI
import SampleCompose

@main
struct iOSApp: App {
    init() {
        TrustWalletCoreRuntime.shared.installIosAdapter(adapter: RealTrustWalletCoreAdapter())
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

