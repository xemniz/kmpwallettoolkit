# sample-compose

Kotlin Multiplatform Compose shared UI module for `wallet-core`.

Uses `org.jetbrains.kotlin.multiplatform`, `com.android.kotlin.multiplatform.library`,
and `org.jetbrains.kotlin.plugin.compose` to share Compose UI across Android and iOS.

## Structure

- `src/commonMain/` — Shared Compose UI (`WalletSampleApp`)
- `src/iosMain/` — iOS entry point (`MainViewController`)
- `:sample-app` — Android application host (`MainActivity`)

## What it does

- Single screen with one button.
- On tap, calls `Wallet.createWithTrustWalletCore()` and derives an Ethereum address.
- Shows mnemonic + address on success, or the error message on failure.
- Lists all registered chains from `ChainRegistry`.

## Run (Android)

```bash
./gradlew :sample-app:installDebug
```

## Run (iOS)

Open `iosApp/iosApp.xcodeproj` in Xcode, select a simulator, and press Run.
The Xcode build phase automatically calls Gradle to compile the `SampleCompose` framework.
