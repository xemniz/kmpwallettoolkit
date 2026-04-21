# Plan — Spec H: Drop `json` parameter from `Eip1559Transaction.toSigningPayload`

## Summary
Reviewer A flagged a footgun: `Eip1559Transaction.toSigningPayload(json: Json = Eip1559Json)` accepts a caller-supplied `Json`, so a caller who passes a vanilla `Json` (without `encodeDefaults = true`) would silently drop `valueWei`, `dataHex`, and `accessList` from the canonical signing payload — a silent deviation from the golden vector. The spec's fix is to remove the parameter entirely; the canonical form has no legitimate caller-override use case. The approach is reasonable and minimal.

## Files to create / modify

| Path | Source set | Change |
|------|------------|--------|
| `wallet-evm/src/commonMain/kotlin/xyz/wallet/toolkit/evm/EvmSigningPayload.kt` | commonMain | Drop `json: Json = Eip1559Json` parameter from `Eip1559Transaction.toSigningPayload`; call `Eip1559Json.encodeToString(this)` internally. Keep `Eip1559Json` private. Legacy `EvmTransaction.toSigningPayload(json: Json = Json)` unchanged. |
| `wallet-evm/src/commonTest/kotlin/xyz/wallet/toolkit/evm/Eip1559PayloadInvarianceTest.kt` | commonTest | **New file.** One test asserting the emit-defaulted-fields invariant on a minimal tx and a fully-populated-with-2-entry-access-list tx. |

**Hot files (CLAUDE.md §7):** none touched.

**Must not modify:**
- `EvmTransaction.kt`, `WalletEvmExtensions.kt`
- `Eip1559GoldenVectorTest.kt` — the expected JSON string is unchanged from the Phase-A commit (the default-argument path already used `Eip1559Json`, so removing the parameter yields the same bytes).
- `Eip1559SigningPayloadTest.kt` — all call sites already use the default argument (zero-arg form), so they keep compiling unchanged.
- Any module other than `wallet-evm`.

## Phases

### Phase 1 — Remove the parameter
**Goal:** Drop the `json` parameter, route through the private `Eip1559Json`. Golden vector stays byte-identical.

Steps:
1. Edit `EvmSigningPayload.kt`:
   - Change `fun Eip1559Transaction.toSigningPayload(json: Json = Eip1559Json): ByteArray` to `fun Eip1559Transaction.toSigningPayload(): ByteArray`.
   - Change body from `json.encodeToString(this).encodeToByteArray()` to `Eip1559Json.encodeToString(this).encodeToByteArray()`.
   - Keep `private val Eip1559Json = Json { encodeDefaults = true }` as-is.
   - Keep the legacy `EvmTransaction.toSigningPayload(json: Json = Json)` **byte-for-byte unchanged** — do not touch it.
2. Verify no caller in the tree passes a `Json` argument to `Eip1559Transaction.toSigningPayload`. Grep expectations:
   - `Eip1559SigningPayloadTest.kt` uses `tx.toSigningPayload()` (zero-arg) — OK.
   - `Eip1559GoldenVectorTest.kt` uses `tx.toSigningPayload()` (zero-arg) — OK.
   - No other callers exist for `Eip1559Transaction.toSigningPayload` in `wallet-evm` or downstream modules.

**Gate:**
```
./gradlew :wallet-evm:allTests
```
Must pass. `Eip1559GoldenVectorTest.goldenJsonDoesNotDrift` must pass with the **identical** expected string — no edits to that file.

### Phase 2 — Add invariance test
**Goal:** Lock in the emit-defaulted-fields behavior with a dedicated test that's independent of the golden vector.

Create `wallet-evm/src/commonTest/kotlin/xyz/wallet/toolkit/evm/Eip1559PayloadInvarianceTest.kt`:

- Single test `toSigningPayloadAlwaysEmitsDefaultedFields` (or similar).
- Build two `Eip1559Transaction` instances:
  - **Minimal:** only required fields (chainId, to, maxFeePerGasWei, maxPriorityFeePerGasWei, gasLimit, nonce); `valueWei`, `dataHex`, `accessList` take their defaults.
  - **Populated:** all fields set, `dataHex = "0xdeadbeef"`, `accessList` with exactly 2 entries (mirroring the structure used in `Eip1559SigningPayloadTest.roundTripsAllFields` so the shape is familiar, but this test does NOT assert hex output).
- Call `toSigningPayload().decodeToString()` on each.
- For each resulting JSON string, assert with `assertTrue(...contains("\"valueWei\"")...)`, `assertTrue(...contains("\"accessList\"")...)`, `assertTrue(...contains("\"dataHex\"")...)`.
- **Do NOT** reproduce or partial-match the golden vector JSON string — that's `Eip1559GoldenVectorTest`'s job (CLAUDE.md §4 rule 5 and spec §Acceptance criterion 6).

**Gate:**
```
./gradlew :wallet-evm:allTests
```
All tests (including the new one) must pass.

### Phase 3 — Final verification
**Goal:** Confirm the spec's acceptance criteria.

Run and confirm:
1. `./gradlew :wallet-evm:allTests` — green.
2. `./gradlew :wallet-evm:compileKotlinJvm` — green.
3. Diff `Eip1559GoldenVectorTest.kt` vs. HEAD~1 (before Phase 1) — zero changes.
4. Diff legacy `EvmTransaction.toSigningPayload` signature and body — zero changes.
5. `Eip1559Json` remains `private val` in `EvmSigningPayload.kt`.

No code changes in this phase; it's a checklist.

## Golden vectors
This task changes only a function signature (removes a parameter with a default), not the serialization logic. The expected JSON string in `Eip1559GoldenVectorTest.kt` is unchanged from the Phase-A commit — the default-argument path already routed through `Eip1559Json`, which is the exact same serializer instance the post-change code uses. No new golden vector is needed; the existing one is the drift guard, and it must remain byte-identical.

If Phase 1 causes `goldenJsonDoesNotDrift` to fail, **stop**: that means the implementer accidentally changed the serializer configuration or field ordering, and that's a code bug, not a test bug (CLAUDE.md §4 rule 6 — never weaken an existing assertion).

## Crypto hygiene — CLAUDE.md §4 rules applied
- **Rule 5 (golden vectors):** `Eip1559GoldenVectorTest` is the signing-input drift guard. It must pass with byte-identical expected JSON. The new invariance test does **not** reproduce the golden string — it only asserts the field-emission invariant.
- **Rule 6 (never weaken assertions):** the golden vector string is untouched. If it breaks, the implementer stops and raises.
- **Rule 8 (serialization is security-relevant):** this change is security-relevant by definition — it closes a silent-drift hole in the signing-payload API. The reviewer will diff `EvmSigningPayload.kt` and `Eip1559GoldenVectorTest.kt`.

Rules 1–4 and 7 are not triggered by this change (no new logging, no randomness, no comparisons, no address handling).

## Risks / Open questions
1. **Binary compatibility:** removing the `json` parameter is a source- and ABI-breaking change. The spec's Non-goals explicitly state no `@Deprecated` bridge is wanted ("Clean breakage is fine at this stage"). No risk given the repo stage; downstream consumers don't yet exist.
2. **Other callers outside `wallet-evm`:** spec says "Module(s) touched: wallet-evm only." A grep for `Eip1559Transaction.*toSigningPayload` across the repo should return only the two test files listed. If it finds another caller, flag it to the user before proceeding — that would contradict the spec's module scope.
3. **Import cleanup:** after the change, `kotlinx.serialization.json.Json` is still imported in the file (the legacy `EvmTransaction` extension uses it). `kotlinx.serialization.encodeToString` is still used. No import removal needed.

## What this plan does NOT do
- Does not touch the legacy `EvmTransaction.toSigningPayload(json: Json = Json)` extension — signature and body remain byte-for-byte identical.
- Does not add `@Deprecated` shims, migration helpers, or a zero-arg overload that delegates to a parameterized one.
- Does not change `Eip1559Json`'s visibility, name, or config.
- Does not modify `Eip1559GoldenVectorTest.kt`, `Eip1559SigningPayloadTest.kt`, `EvmTransaction.kt`, `WalletEvmExtensions.kt`.
- Does not touch any module other than `wallet-evm`.
- Does not run `./gradlew build` (CLAUDE.md §3 — per-module tests only).
- Does not add sample-app or downstream integration usage.
