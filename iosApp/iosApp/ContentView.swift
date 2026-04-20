import UIKit
import SwiftUI
import SampleCompose

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all) // Required for correct touch mapping
            .frame(maxWidth: .infinity, maxHeight: .infinity) // Forces expansion
    }
}

