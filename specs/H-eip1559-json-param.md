# Spec H — Eliminate caller-Json bypass on `Eip1559Transaction.toSigningPayload`

## Goal
Reviewer A flagged a should-fix on the Phase 3 merge: `Eip1559Transaction.toSigningPayload(json: Json = Eip1559Json): ByteArray` uses the scoped `Eip1559Json` (with `encodeDefaults = true`) only when the default argument is taken. A caller that passes its own `Json` instance without `encodeDefaults = true` will silently drop `valueWei`, `dataHex`, and `accessList`, producing a signing payload that diverges from the golden vector.

**Remove the footgun.** The `toSigningPayload` extension produces a *canonical* signing payload — there is no legitimate reason for a caller to override the serializer. Drop the `json` parameter entirely; the extension always uses the scoped `Eip1559Json`.

## Module(s) touched
- `wallet-evm` — only.

## Files expected to change
- `wallet-evm/src/commonMain/kotlin/xyz/wallet/toolkit/evm/EvmSigningPayload.kt` — drop the `json` parameter on `Eip1559Transaction.toSigningPayload`. Keep the `Eip1559Json` private constant as-is. Keep the legacy `EvmTransaction.toSigningPayload(json: Json = Json)` **unchanged** — its canonical form happens to match kotlinx defaults; we'll revisit if/when we add EIP-155 concerns, not in this spec.

**Do not modify:**
- `EvmTransaction.kt`, `WalletEvmExtensions.kt`, any test file that already exists.
- `Eip1559GoldenVectorTest.kt` — the golden JSON string is unchanged because the default-argument path already used `Eip1559Json`. Verify by diff after the change.
- Any module other than `wallet-evm`.

**Hot files (§7):** none.

## Design
Before:
```kotlin
private val Eip1559Json = Json { encodeDefaults = true }

fun Eip1559Transaction.toSigningPayload(json: Json = Eip1559Json): ByteArray {
    return json.encodeToString(this).encodeToByteArray()
}
```

After:
```kotlin
private val Eip1559Json = Json { encodeDefaults = true }

fun Eip1559Transaction.toSigningPayload(): ByteArray {
    return Eip1559Json.encodeToString(this).encodeToByteArray()
}
```

One parameter removed. One call site internal.

## Acceptance criteria

1. `./gradlew :wallet-evm:allTests` — green. Specifically, `Eip1559GoldenVectorTest` must still pass with **byte-identical** expected JSON. If the golden string needs editing, stop and raise — the previous run froze a specific canonical form and this change must not drift it.
2. `./gradlew :wallet-evm:compileKotlinJvm` — green.
3. `Eip1559Transaction.toSigningPayload` is now zero-argument. Any test or caller that passes a `json` argument fails to compile — verify there are currently none in the touched tree (there shouldn't be).
4. `Eip1559Json` is still `private` to `EvmSigningPayload.kt`.
5. Legacy `EvmTransaction.toSigningPayload(json: Json = Json)` is unchanged byte-for-byte.
6. Add exactly one test in a **new test file** `Eip1559PayloadInvarianceTest.kt` that asserts calling `toSigningPayload()` on two different `Eip1559Transaction` instances (one minimal, one with everything populated including a 2-entry access list) produces JSON whose length is consistent with the canonical-form expectation and always contains `"valueWei"`, `"accessList"`, `"dataHex"` substrings — i.e. the emit-defaulted-fields invariant. One test, a few `assertTrue(payload.contains(...))` calls. Do **not** reproduce the golden vector in this test — that's the golden test's job.

## Non-goals
- Adding `@Deprecated` or migration aliases. Nothing in the repo passed a custom `json` to this extension, and no downstream depends on it yet. Clean breakage is fine at this stage.
- Refactoring `Eip1559Json` visibility or reuse with the legacy extension.
- Touching the legacy `EvmTransaction.toSigningPayload` signature.

## Crypto hygiene — CLAUDE.md §4
Rule 8 applies: this is serialization-of-transactions code. A change here is "security-relevant." The golden vector test (Phase-A artifact) is the drift guard and must remain byte-identical. The reviewer will diff it.

## Why this follow-up exists
Reviewer A's Phase-3 should-fix: defaults-in-public-crypto-API is a subtle trap that gets past planner and implementer. A caller passing a custom `Json` without `encodeDefaults = true` would silently produce a wrong signing input. Removing the parameter makes the trap unreachable.
