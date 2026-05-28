# Trust Wallet Core Distribution

Goal: a KMP consumer app should install `io.github.xemniz:wallet-core` from Maven Central with only `google()` and `mavenCentral()` configured. iOS consumers also apply the `io.github.xemniz.wallet-toolkit.ios` Gradle plugin from Maven Central so Gradle can link the dynamic XCFrameworks. No Trust Wallet GitHub Packages credentials are required by app developers.

## Android

Android is handled by republishing the upstream Trust Wallet Core binaries under this project's Maven Central namespace:

- `io.github.xemniz:trustwallet-core-android:4.6.0`
- `io.github.xemniz:trustwallet-core-proto:4.6.0`
- `io.github.xemniz:trustwallet-core-ios:4.6.0`

`wallet-core` declares its Android dependency on `io.github.xemniz:trustwallet-core-android`, so published Gradle metadata and POM files no longer expose `com.trustwallet:wallet-core`.

The republished artifacts preserve the upstream binary contents. They must keep the Trust Wallet Core version number, not the toolkit version number.

## iOS

The iOS runtime uses Kotlin/Native cinterop against the republished Trust Wallet Core XCFrameworks. `wallet-core` publishes the cinterop klib, and the `io.github.xemniz.wallet-toolkit.ios` Gradle plugin resolves `io.github.xemniz:trustwallet-core-ios`, unzips the XCFramework slices in the consuming KMP build, and links `WalletCore.framework` plus `WalletCoreSwiftProtobuf.framework` for iOS device and simulator targets.

The iOS bridge calls the WalletCore C API directly for:

- mnemonic creation/import
- address derivation
- Ethereum legacy signing
- Ethereum EIP-1559 signing

`TrustWalletCoreIosAdapter` remains available as an escape hatch for consumers who want to provide their own backend.

Upstream release assets for `4.6.0`:

- `WalletCore.xcframework.zip`, SHA-256 `689935aff413004b18c7b32ee955716868ebcd38328c5159c69f0d5f5bcfddf0`
- `WalletCoreSwiftProtobuf.xcframework.zip`, SHA-256 `719b1ebc7ad174017e399cdd7fc60372b369d9712d646ebb8b4e264c4881d1d8`
