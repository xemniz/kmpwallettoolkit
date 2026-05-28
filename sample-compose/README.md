# sample-compose

Shared Compose Multiplatform starter wallet UI for `kmp-wallet-toolkit`.

This module is the reference consumer of the toolkit modules. It is not a production wallet by itself; it is the fastest way to see how a KMM wallet can be assembled from the library surface.

By default the sample depends on local project modules. To validate the released client setup, run the same sample code with `-PwalletToolkitDependencyMode=maven`; that switches `wallet-core`, `wallet-evm`, `wallet-rpc`, and the iOS Trust Wallet Core linker wiring to Maven Central artifacts. No Trust Wallet GitHub Packages credentials are required.

## Hosts

- Android: `:sample-app`
- iOS: `iosApp/iosApp.xcodeproj`

## Current Flow

- welcome screen
- create wallet flow with mnemonic confirmation
- import wallet flow with BIP-39 surface validation
- secure mnemonic persistence through host-provided storage
- home screen with active wallet address and portfolio hooks
- send flow with EIP-1559 transaction assembly/signing path
- transaction status screen backed by receipt polling
- swap flow with token search, quote, and transaction assembly work in progress

## Configuration

Copy `src/commonMain/kotlin/xyz/wallet/toolkit/sample/secrets/Secrets.kt.template` to `Secrets.kt` and fill in any provider keys or RPC URLs needed by the sample.

`Secrets.kt` is intentionally not committed. Keep API keys and RPC credentials out of source control.

## Run Android

```bash
./gradlew :sample-app:installDebug
```

Run Android using Maven Central toolkit artifacts:

```bash
./gradlew -PwalletToolkitDependencyMode=maven :sample-app:installDebug
```

## Run iOS

Open `iosApp/iosApp.xcodeproj` in Xcode, select a simulator, and press Run. The Xcode build phase compiles the shared `SampleCompose` framework.

## Verify

```bash
./gradlew :sample-compose:allTests :sample-app:assembleDebug
```

Verify the sample as a downstream Maven client:

```bash
./gradlew -PwalletToolkitDependencyMode=maven \
  :sample-compose:compileAndroidMain \
  :sample-compose:linkDebugFrameworkIosSimulatorArm64 \
  :sample-app:assembleDebug
```
