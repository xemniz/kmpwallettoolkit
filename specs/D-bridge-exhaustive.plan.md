# Plan — Spec D: Make TrustWalletCoreNativeBridge exhaustive over SupportedChain

## 1. Summary
The Android actual of `TrustWalletCoreNativeBridge` has a `when` over `SupportedChain` that predates the Optimism (spec E) and BnbSmartChain (spec F) entries, so every Android compile now fails. The spec's fix — add two branches and introduce a commonTest tripwire that iterates `SupportedChain.entries` — is correct and minimal. The approach (map Optimism to `CoinType.ETHEREUM` because TWC has no dedicated Optimism coin type, and BnbSmartChain to `CoinType.SMARTCHAIN`) matches upstream Trust Wallet Core conventions (verified below).

## 2. Verification of TWC enum constants (v4.6.0)
Dependency pinned in `gradle/libs.versions.toml`: `com.trustwallet:wallet-core:4.6.0`.

Confirmed via Trust Wallet Core upstream:
- `wallet.core.jni.CoinType.ETHEREUM` — exists (already used in the bridge).
- `wallet.core.jni.CoinType.POLYGON` — exists (already used).
- `wallet.core.jni.CoinType.SMARTCHAIN` — exists. The TWC Android test `TestBinanceSmartChainAddress.kt` uses `CoinType.SMARTCHAIN` for BNB Smart Chain. There is no `CoinType.BSC` or `CoinType.BNBSMARTCHAIN` alias.
- `wallet.core.jni.CoinType.OPTIMISM` — does **not** exist. OP Stack chains are served by `CoinType.ETHEREUM` in TWC (same derivation path, BIP44 coin_type 60). This mirrors the existing `Base` and `Arbitrum` branches.

No raise-to-user needed. The spec's proposed mapping is locked.

## 3. Files to create / modify

| Path | Source set | Action |
|------|------------|--------|
| `wallet-core/src/androidMain/kotlin/xyz/wallet/toolkit/core/TrustWalletCoreNativeBridge.android.kt` | `androidMain` | modify — add 2 `when` branches |
| `wallet-core/src/commonTest/kotlin/xyz/wallet/toolkit/core/ChainCoinTypeMappingTest.kt` | `commonTest` | create |

Neither path is a hot file per CLAUDE.md §7. `Chain.kt` and `ChainRegistry.kt` are hot and are **not** touched.

## 4. Exact changes

### 4.1 Bridge `when` (final form)
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
Only the last two lines are additions. No other change to the file.

### 4.2 New test file (exact contents)
Path: `wallet-core/src/commonTest/kotlin/xyz/wallet/toolkit/core/ChainCoinTypeMappingTest.kt`

```kotlin
package xyz.wallet.toolkit.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tripwire for the Android `TrustWalletCoreNativeBridge.toCoinType()` `when` expression.
 *
 * The bridge lives in androidMain and cannot be invoked from commonTest, so this test
 * asserts at the SupportedChain level: every enum entry must appear in [expected]. When a
 * new chain is added, this test fails and the failure message points the implementer at
 * the bridge — preventing a recurrence of the spec E/F oversight that broke S1.
 */
class ChainCoinTypeMappingTest {

    // Source of truth for what the Android bridge's `when` must map each chain to.
    // Keep in sync with TrustWalletCoreNativeBridge.android.kt::toCoinType().
    private val expected: Map<SupportedChain, String> = mapOf(
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
            "SupportedChain has entries with no CoinType mapping: $uncovered — " +
                "update both TrustWalletCoreNativeBridge.android.kt::toCoinType() and this test.",
        )
    }

    @Test
    fun expected_map_does_not_reference_removed_chains() {
        val known = SupportedChain.entries.toSet()
        val stale = expected.keys.filter { it !in known }
        assertTrue(
            stale.isEmpty(),
            "ChainCoinTypeMappingTest.expected references chains not in SupportedChain: $stale",
        )
    }

    @Test
    fun expected_map_covers_all_six_current_entries() {
        assertEquals(6, SupportedChain.entries.size, "Spec D was written for 6 chains; update this test.")
        assertEquals(6, expected.size)
    }
}
```

Assertion-shape rationale: the first test is the tripwire. The second catches the inverse (someone removes a chain but leaves the test stale). The third is a belt-and-suspenders count guard so a future addition that also edits the map is still surfaced in review.

## 5. Phases

### Phase 1 — add failing-by-design test
- Create `ChainCoinTypeMappingTest.kt` as specified.
- Do **not** modify the bridge yet.
- Gate: `./gradlew :wallet-core:allTests`.
  - Expected: `jvmTest` passes (the test covers all six entries and is green against the current enum). `compileKotlinAndroid` / Android unit-test compile will still fail with "`when` expression must be exhaustive" — that is the bug we are about to fix. If the failure comes from a different source, stop and raise.

### Phase 2 — make the bridge exhaustive
- Edit `TrustWalletCoreNativeBridge.android.kt`: add the two new `when` branches exactly as in §4.1.
- No other edits (no imports, no helper changes — `CoinType.SMARTCHAIN` is reachable via the existing `import wallet.core.jni.CoinType`).
- Gate: `./gradlew :wallet-core:allTests` — green.

### Phase 3 — cross-platform compile check
- `./gradlew :wallet-core:compileKotlinJvm :wallet-core:compileKotlinIosX64` — green. (Per CLAUDE.md §3, mandatory after any wallet-core change.)
- The iOS and JVM actuals use stubs; this confirms Phase 2 did not inadvertently leak a JNI import into shared code.

### Phase 4 — acceptance gate on the real downstream signal
- `./gradlew :sample-app:assembleDebug` — green. This is the acceptance criterion that spec D exists to unblock (§6 / spec acceptance item 3).
- If this fails with anything other than "task `:sample-app:assembleDebug` not found" (worktree missing the sample module), investigate before declaring done.

## 6. Acceptance gates (final done list)
All four must pass:
1. `./gradlew :wallet-core:allTests`
2. `./gradlew :wallet-core:compileKotlinJvm`
3. `./gradlew :wallet-core:compileKotlinIosX64`
4. `./gradlew :sample-app:assembleDebug`

No ktlint/detekt — per CLAUDE.md §3, those tasks do not exist in this repo.

## 7. Crypto hygiene checklist (CLAUDE.md §4)
This task does not derive keys, sign bytes, compare secrets, or add serialization. Rule-by-rule:
- §4.1 (no logging secrets) — N/A, no new logging.
- §4.2 (SecureRandom) — N/A, no randomness introduced.
- §4.3 (constant-time compare) — N/A, no secret comparison.
- §4.4 (no `Random` for nonces) — N/A.
- §4.5 (golden vectors for signing) — **N/A here**: this change is a routing mapper, not a signing change. No existing golden vector is altered; the Optimism and BnbSmartChain signing paths already exist (or are stubbed) and their correctness is the concern of specs E/F, not D.
- §4.6 (never weaken an assertion) — the new test only adds assertions.
- §4.7 (address case) — N/A.
- §4.8 (serialization is security-relevant) — **Pay attention**: `CoinType.SMARTCHAIN` implies TWC may emit a different signing protobuf shape than `CoinType.ETHEREUM`. The bridge's `signEvmTransaction` currently hard-codes `AnySigner.sign(input, CoinType.ETHEREUM, ...)` at line 71, regardless of `chain`. That is a pre-existing bug and **out of scope for spec D** (spec non-goal: "Adding actual signing support (TWC integration) for Optimism or BnbSmartChain"). Flagged in §9 below.

## 8. Golden vectors
None required. This task does not change a signing path, a serialization format, or a key derivation. Spec explicitly says no golden-vector test is needed (acceptance item 6).

## 9. Risks / open questions
- **Pre-existing latent bug at line 71 of the bridge.** `AnySigner.sign(input, CoinType.ETHEREUM, ...)` hard-codes Ethereum regardless of the `chain` argument. After spec D, `deriveAddress` will correctly route BnbSmartChain to `CoinType.SMARTCHAIN` (different derivation path, different address), but `signTransaction` will still sign as Ethereum. This means a BSC address derived via the bridge cannot produce a valid BSC signature through this bridge yet. Per the spec's non-goals, this is deferred to a future spec — but it should be surfaced to the user now so it isn't discovered at S1 signing time.
- **`:sample-app:assembleDebug` may not be present in this worktree.** The repo has `sample-app` / `sample-compose` / `iosApp` but parallel-agent worktrees can differ. If the task fails at Phase 4 with "project `:sample-app` not found", raise rather than skip — the spec treats this gate as load-bearing.
- **Test file lives in commonTest but the bug lives in androidMain.** The tripwire cannot directly call the bridge. It guards the contract indirectly (by mirroring the map). Accept this limitation — calling into JNI from commonTest is not feasible and the spec explicitly chose this shape.
- **TWC version upgrade risk.** If `trustWalletCore` in `libs.versions.toml` is later bumped and the `SMARTCHAIN` constant is renamed upstream, the bridge will fail to compile — not silently misbehave. That is the acceptable failure mode.

## 10. What this plan does NOT do
- Does not modify `Chain.kt`, `ChainRegistry.kt`, or anything in `commonMain` of wallet-core.
- Does not add or change JVM / iOS actuals of the bridge.
- Does not fix the hard-coded `CoinType.ETHEREUM` inside `signEvmTransaction` (line 71) — out of scope per spec non-goals. Flagged in §9.
- Does not add any new dependency, and does not touch `gradle/libs.versions.toml` or `settings.gradle.kts` (hot files per §7).
- Does not add golden signing vectors for Optimism or BnbSmartChain.
- Does not modify any file under `sample-app/`, `sample-compose/`, `iosApp/`, `wallet-evm/`, `wallet-rpc/`, or `wallet-utils/`.
- Does not introduce a table-driven refactor of `toCoinType()` — spec explicitly rejects this.
- Does not add ktlint/detekt tasks.
