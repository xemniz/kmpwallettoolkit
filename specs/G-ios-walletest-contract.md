# Spec G — Align iOS Trust Wallet Core error contract with WalletTest

## Goal
Fix two iOS test failures in `wallet-core/commonTest/WalletTest.kt` that were surfaced during Phase 4 (specs E and F) as pre-existing and out-of-scope. They are:

1. `trustWalletCoreConstructorUsesNativeBridgeSeam` — expects `NotImplementedError` with message containing `"Trust Wallet Core native bridge"`. iOS adapter error says `"Trust Wallet Core iOS bridge is not configured"` → substring miss.
2. `trustWalletCoreSignTransactionFallsBackUntilRuntimeIsAvailable` — expects `NotImplementedError` with message containing `"signTransaction"`. iOS's `require(transaction is EvmTransactionData)` fires first when a non-EVM `Transaction` is passed → `IllegalArgumentException` is thrown instead.

The root cause is platform-drift: the JVM stub established an implicit error contract (`NotImplementedError`, message contains "Trust Wallet Core native bridge" + method name) that the iOS actual does not honor. The tests are correct; the iOS actual is out of contract.

**Do not modify `WalletTest.kt`.** This is explicitly *not* a test-weakening fix. If you feel like you need to change the tests to make this pass, stop and raise. The fix lives in the iOS source tree.

## Module(s) touched
- `wallet-core` — only.
- Specifically: `commonMain/TrustWalletCoreIosBridge.kt` (houses `TrustWalletCoreRuntime`) and `iosMain/TrustWalletCoreNativeBridge.ios.kt`.

## Files expected to change
- `wallet-core/src/commonMain/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreIosBridge.kt` — change `TrustWalletCoreRuntime.requireIosAdapter(...)` to include `"Trust Wallet Core native bridge"` in its thrown-error message and accept an optional `method: String` parameter that gets included (same pattern as the JVM stub's `jvmNotSupported(method: String)`).
- `wallet-core/src/iosMain/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreNativeBridge.ios.kt` — reorder `signTransaction` so `requireIosAdapter(method = "signTransaction")` is called **before** the `require(transaction is EvmTransactionData)` type-check; pass the method name from `createMnemonic`/`deriveAddress`/`signTransaction` callers.

**Do not modify:**
- `WalletTest.kt` (load-bearing — the tests are the contract).
- `TrustWalletCoreNativeBridge.jvm.kt` (already honors the contract).
- `TrustWalletCoreNativeBridge.android.kt` (out of scope; has its own contract).
- The `TrustWalletCoreIosAdapter` interface or `TrustWalletCoreEvmSigningRequest` data class (public API; out of scope).
- Any other module.

**Hot files (§7):** none touched.

## Design

### Change 1 — `TrustWalletCoreRuntime.requireIosAdapter`
Current:
```kotlin
internal fun requireIosAdapter(): TrustWalletCoreIosAdapter {
    return iosAdapter ?: throw NotImplementedError(
        "Trust Wallet Core iOS bridge is not configured. " +
            "Install an adapter via TrustWalletCoreRuntime.installIosAdapter(...) " +
            "from your iOS host before using TrustWalletCoreWalletEngine.",
    )
}
```

Target (error message must contain both `"Trust Wallet Core native bridge"` and the method name):
```kotlin
internal fun requireIosAdapter(method: String? = null): TrustWalletCoreIosAdapter {
    return iosAdapter ?: throw NotImplementedError(
        buildString {
            append("Trust Wallet Core native bridge is not available on iOS: ")
            append("install an adapter via TrustWalletCoreRuntime.installIosAdapter(...) ")
            append("from your iOS host before using TrustWalletCoreWalletEngine.")
            if (method != null) append(" (method=$method)")
        },
    )
}
```

Keep `method` optional (default `null`) so any future caller that doesn't have a method name still works.

### Change 2 — `TrustWalletCoreNativeBridge.ios.kt::signTransaction`
Current:
```kotlin
actual fun signTransaction(...): ByteArray {
    require(transaction is EvmTransactionData) { ... }   // throws IAE first
    val hex = TrustWalletCoreRuntime.requireIosAdapter().signEvmTransaction(...)
    return hex.hexToByteArray()
}
```

Target (adapter check first, then type-check, passing method name):
```kotlin
actual fun signTransaction(...): ByteArray {
    val adapter = TrustWalletCoreRuntime.requireIosAdapter(method = "signTransaction")
    require(transaction is EvmTransactionData) {
        "Only EvmTransactionData transactions are currently supported for signing. " +
            "Received: ${transaction::class.simpleName}"
    }
    val hex = adapter.signEvmTransaction(
        mnemonic = mnemonic,
        chain = chain,
        transaction = TrustWalletCoreEvmSigningRequest.from(transaction),
    )
    return hex.hexToByteArray()
}
```

Also pass `method = "createMnemonic"` and `method = "deriveAddress"` from those respective functions for contract consistency.

Do not change the `require(...)` message or `hexToByteArray()` call.

## Acceptance criteria

1. `./gradlew :wallet-core:allTests` — **fully green on both JVM and iOS targets.** The 2 previously-failing tests now pass. No previously-passing test now fails.
2. `./gradlew :wallet-core:compileKotlinJvm :wallet-core:compileKotlinIosX64` — green.
3. `WalletTest.kt` diff against `main` is **zero bytes**. Verify with `git diff main -- wallet-core/src/commonTest/kotlin/xyz/wallet/toolkit/core/WalletTest.kt`.
4. No changes to `TrustWalletCoreNativeBridge.jvm.kt`, `TrustWalletCoreNativeBridge.android.kt`, the `TrustWalletCoreIosAdapter` interface, or `TrustWalletCoreEvmSigningRequest`.
5. `TrustWalletCoreRuntime.requireIosAdapter()` (zero-arg call) still works — the `method` parameter is optional.
6. New unit test is **not** required (the existing `WalletTest` tests are the gate). If you feel a new test is useful, you may add one asserting that `requireIosAdapter("foo")` includes `(method=foo)` in the thrown message — but this is optional and must live in a new test file, not `WalletTest.kt`.

## Non-goals
- Fixing the absence of an actual Trust Wallet Core iOS integration. The tests assert the "not configured" behavior; shipping a real adapter is a different, much larger task.
- Renaming `TrustWalletCoreIosAdapter` or its members. Public API.
- Changing `TrustWalletCoreEvmSigningRequest`.
- Changing the Android or JVM actuals.
- Adding `method` parameters to the JVM stub's signatures — they already include it internally via `jvmNotSupported(method)`; the interface `actual fun signTransaction(...)` does not and should not surface `method` as a public parameter on any platform.

## Crypto hygiene — CLAUDE.md §4
No signing / entropy / comparison surface is touched. Nothing sensitive goes into `toString`. §4 is satisfied trivially.

## Why this follow-up exists
Parallel-CEF surfaced these failures. Both implementers (E and F) did the right thing by not silently masking them. Fixing them now lets the `:wallet-core:allTests` gate be fully green on iOS, which matters for any future wallet-core task that would otherwise inherit these broken tests as "pre-existing." Removes the trust-me-it's-pre-existing footnote from future reviews.
