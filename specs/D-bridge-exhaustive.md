# Spec D — Make TrustWalletCoreNativeBridge exhaustive over SupportedChain

## Goal
Fix a pre-existing `when` non-exhaustiveness in `wallet-core/src/androidMain/.../TrustWalletCoreNativeBridge.android.kt` that blocks Android compilation after specs E (Optimism) and F (BnbSmartChain) merged. Both of those specs extended the `SupportedChain` enum but did not update the Android bridge's `SupportedChain.toCoinType()` mapper, leaving it non-exhaustive.

This is a **correctness + compile-gate** task. The bug is dormant on commonMain and JVM (where the bridge has stub actuals), but any Android build — including every sample-app assembleDebug — fails. The S1–S6 sample-wallet spec set cannot begin until this merges.

## Module(s) touched
- `wallet-core` — only, and only the Android actual of the bridge.

## Files expected to change
- `wallet-core/src/androidMain/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreNativeBridge.android.kt` — add two branches to `SupportedChain.toCoinType()`.
- `wallet-core/src/commonTest/kotlin/xyz/wallet/toolkit/core/ChainCoinTypeMappingTest.kt` (new) — guards against this failure mode recurring when a new chain is added.

**Do not modify:**
- `Chain.kt` — already has all entries; no edits needed here (hot file per CLAUDE.md §7 — leave alone).
- `ChainRegistry.kt` — reads from `SupportedChain.entries` directly; no change needed (hot file §7).
- JVM / iOS actuals — their stubs throw `NotImplementedError` for every chain and don't need updating.
- Any wallet-evm, wallet-rpc, wallet-utils, sample-* file.

## Design
Add two branches to the existing `when`:

```kotlin
private fun SupportedChain.toCoinType(): CoinType = when (this) {
    SupportedChain.Ethereum -> CoinType.ETHEREUM
    SupportedChain.Base -> CoinType.ETHEREUM
    SupportedChain.Arbitrum -> CoinType.ETHEREUM
    SupportedChain.Polygon -> CoinType.POLYGON
    SupportedChain.Optimism -> CoinType.ETHEREUM
    SupportedChain.BnbSmartChain -> CoinType.SMARTCHAIN
}
```

Rationale:
- **Optimism** is an Ethereum L2 — derivation path is identical to Ethereum mainnet. Trust Wallet Core does not have a dedicated `CoinType.OPTIMISM` (verify in the planner); OP Stack chains use `CoinType.ETHEREUM`. This mirrors the existing Base and Arbitrum mappings.
- **BnbSmartChain (BSC)** is its own chain with its own TWC coin type. `CoinType.SMARTCHAIN` is the name in Trust Wallet Core's enum (note: not `BSC`).

The planner must **verify both CoinType constants exist** in the Trust Wallet Core JNI `CoinType` enum for the version this repo uses, before locking them into the plan. If either constant is named differently or missing, stop and raise.

### Recurrence-guard test
Add `ChainCoinTypeMappingTest.kt` under `wallet-core/src/commonTest/`. Since the bridge is `androidMain`-only and commonTest cannot call the Android-only `toCoinType()` extension directly, the test asserts at the **SupportedChain** level — iterating `SupportedChain.entries` and requiring that some allow-listed registry of expected mappings covers every entry:

```kotlin
class ChainCoinTypeMappingTest {
    // Expected coin-type name per chain. Source of truth for the bridge's when.
    private val expected = mapOf(
        SupportedChain.Ethereum to "ETHEREUM",
        SupportedChain.Base to "ETHEREUM",
        SupportedChain.Arbitrum to "ETHEREUM",
        SupportedChain.Polygon to "POLYGON",
        SupportedChain.Optimism to "ETHEREUM",
        SupportedChain.BnbSmartChain to "SMARTCHAIN",
    )

    @Test
    fun every_supported_chain_has_an_expected_coin_type() {
        val uncovered = SupportedChain.entries.filter { it !in expected }
        assertTrue(
            uncovered.isEmpty(),
            "SupportedChain has entries with no CoinType mapping: $uncovered — update both the bridge and this test.",
        )
    }
}
```

This test runs on commonTest (jvmTest, no JNI needed). If a future spec adds a new `SupportedChain` entry without updating the map, this test fails with a clear pointer to the bridge — exactly the failure S1 just hit.

## Acceptance criteria
A task is **not done** until every one of these passes.

1. `./gradlew :wallet-core:allTests` — green.
2. `./gradlew :wallet-core:compileKotlinJvm` — green.
3. `./gradlew :sample-app:assembleDebug` — green. This is the real signal; S1 needs it.
4. `ChainCoinTypeMappingTest.every_supported_chain_has_an_expected_coin_type` passes and covers all six current `SupportedChain` entries.
5. No changes to `Chain.kt`, `ChainRegistry.kt`, or any file outside `wallet-core/src/androidMain/` and `wallet-core/src/commonTest/`.
6. No new golden-vector test file added (this task does not sign anything).
7. The Android bridge's `when` is exhaustive — the compiler should no longer emit "`when` expression must be exhaustive".

## Non-goals
- Adding actual signing support (TWC integration) for Optimism or BnbSmartChain — the bridge's `toCoinType()` is only part of the story; full signing for BSC may require different gas/fee handling. That's a future spec.
- JVM or iOS actuals for these chains — still stubs.
- Updating `ChainRegistry` (unnecessary; it reads from `entries`).
- Rewriting the mapper to a table-driven form — trivial change here; over-engineering is out of scope.
- Any sample-app / sample-compose change.

## Why this is interesting for the experiment
- **Caught by S1's acceptance gate, not by E's or F's reviewers.** E and F both merged green because their test gate was `:wallet-core:allTests`, which doesn't traverse the Android bridge. The repo's reviewer checklist missed the cross-module compile implication. This spec is the forensics follow-up — and the `ChainCoinTypeMappingTest` is the tripwire that would have caught E/F at review time.
- **Hot-file-adjacent (wallet-core) but not hot itself.** The file it touches isn't in CLAUDE.md §7's hot list, but the whole `wallet-core/androidMain/` bridge is where E/F's omissions manifest. Good probe of whether the reviewer reads more than the spec's named files.
- **Planner must verify TWC enum names** — a natural KMP JNI hazard. If `CoinType.SMARTCHAIN` is spelled differently in the JNI, the implementer must stop and raise, not guess.
