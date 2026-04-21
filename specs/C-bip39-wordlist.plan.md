# Plan — Spec C: BIP-39 English wordlist validation (`wallet-utils`)

## 1. Summary
Add a pure-commonMain BIP-39 English wordlist validator to `wallet-utils`. The spec's approach (hard-coded `listOf(...)` literal, `object Bip39Wordlist` facade, sealed `PhraseValidation` result) is reasonable: no resource loader is needed in commonMain, and the validator intentionally stops at wordlist membership — entropy/checksum/key derivation remain with Trust Wallet Core. Scope stays inside a single module with iOS targets, so the only portability concern is "pure Kotlin, no `java.*` / `android.*`".

## 2. Files to create / modify

All paths absolute.

| Path | Source set | Action |
|------|------------|--------|
| `/Users/xemniz/AndroidStudioProjects/kmpwallettoolkit/wallet-utils/src/commonMain/kotlin/xyz/wallet/toolkit/utils/Bip39Wordlist.kt` | `commonMain` | Create. Contains `internal val BIP39_ENGLISH: List<String>` (exactly 2048 entries), sealed class `PhraseValidation` with `Valid`, `UnknownWords(indices)`, `InvalidLength(count)` subtypes, and `object Bip39Wordlist` with `contains` and `validatePhrase`. |
| `/Users/xemniz/AndroidStudioProjects/kmpwallettoolkit/wallet-utils/src/commonTest/kotlin/xyz/wallet/toolkit/utils/Bip39WordlistTest.kt` | `commonTest` | Create. All acceptance-criteria tests live here. |

**Hot files touched:** none. No change to `gradle/libs.versions.toml`, `settings.gradle.kts`, `build.gradle.kts`, `ChainRegistry.kt`, `Chain.kt`, `CLAUDE.md`, or `.claude/agents/*`.

**No expect/actual seam.** Everything is commonMain; `jvmMain`/`androidMain`/`iosMain` stay untouched.

## 3. Phases

Each phase ends green on:
- `./gradlew :wallet-utils:allTests`
- `./gradlew :wallet-utils:compileKotlinIosX64`

(`allTests` already covers JVM + iOS test targets for wallet-utils; the extra compile-only iOS check is cheap insurance that no `java.*` leaked in.)

### Phase 1 — Wordlist literal + boundary/size tests (first, per instructions)

Goal: catch typos in the 2048-word literal before any logic lands on top of it.

Steps:
1. Create `Bip39Wordlist.kt` with:
   - `internal val BIP39_ENGLISH: List<String> = listOf("abandon", "ability", ..., "zoo")` — full 2048 words, lowercase, sorted, copied verbatim from the canonical BIP-39 English wordlist (https://github.com/bitcoin/bips/blob/master/bip-0039/english.txt). The implementer must paste the file contents, not hand-type.
   - Empty `object Bip39Wordlist { }` stub (so the test file compiles; real methods come in Phase 2).
   - One-line KDoc on `BIP39_ENGLISH` stating: "The BIP-39 wordlist is public (2048 entries). Lookup is not secret-comparable; do not convert to a constant-time scan." (addresses CLAUDE.md §4.3 reviewer drift).
2. Create `Bip39WordlistTest.kt` with the boundary/size tests only:
   - `size == 2048`
   - `first() == "abandon"`
   - `last() == "zoo"`
   - `BIP39_ENGLISH == BIP39_ENGLISH.sorted()` (detects any misordered paste)
   - `BIP39_ENGLISH.toSet().size == 2048` (detects duplicate lines)
   - All-lowercase assertion: `BIP39_ENGLISH.all { it == it.lowercase() }`
3. Run the two gates. Fix any typo found by these tests before moving on.

### Phase 2 — Implementation + happy-path tests

Steps:
1. Fill in `object Bip39Wordlist`:
   - `fun contains(word: String): Boolean` — lowercase input, then `BIP39_ENGLISH.binarySearch(word) >= 0`. Return `false` on empty string or anything containing whitespace (so `contains("abandon ")` is `false` per acceptance §6).
   - `fun validatePhrase(phrase: String): PhraseValidation`:
     - trim, split on `"\\s+".toRegex()`, drop leading/trailing empty tokens.
     - For each token, lowercase and look up; collect 0-based indices of misses.
     - If `unknown.isNotEmpty()` → `PhraseValidation.UnknownWords(unknown)` (precedence rule).
     - Else if `count !in setOf(12, 15, 18, 21, 24)` → `PhraseValidation.InvalidLength(count)`.
     - Else → `PhraseValidation.Valid`.
2. Declare the sealed type:
   ```
   sealed class PhraseValidation {
       object Valid : PhraseValidation()
       data class UnknownWords(val indices: List<Int>) : PhraseValidation()
       data class InvalidLength(val count: Int) : PhraseValidation()
   }
   ```
   Override `toString()` on the sealed parent (or on each subtype) so that no word from the input phrase can ever be rendered. `UnknownWords(indices=[1, 3])` and `InvalidLength(count=13)` are safe — they contain positions/counts only — but add an explicit `override fun toString()` to each so a future refactor that adds a `words: List<String>` field cannot silently leak. CLAUDE.md §4.1 citation goes in a KDoc on the sealed class.
3. Add happy-path tests:
   - `contains("abandon")`, `contains("ABANDON")`, `contains("zoo")` → `true`.
   - `contains("")`, `contains("notaword")`, `contains("abandon ")` → `false`.
   - **BIP-39 golden vector**: `validatePhrase("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about")` → `PhraseValidation.Valid` (see §4 below).
4. Run the two gates.

### Phase 3 — Edge-case / error-path tests + the toString-redaction test

Steps:
1. Add tests:
   - `UnknownWords([1, 3])` for a 12-word phrase whose positions 1 and 3 are garbage (e.g. `"abandon xxx abandon yyy abandon abandon abandon abandon abandon abandon abandon about"`).
   - `InvalidLength(13)` for 13 valid words (repeat `"abandon"` 12 times + `"about"`).
   - **Precedence**: a phrase that is both 13-long and contains one unknown word returns `UnknownWords(...)`, never `InvalidLength(13)`.
   - **Redaction (acceptance §11)**: build an invalid phrase containing the literal word `"abandon"`, call `validatePhrase(...).toString()`, and assert `"abandon" !in result`. Repeat for a phrase with a distinctive non-wordlist token (e.g. `"zzzsecretzzz"`) and assert it is absent from `toString()`.
   - Whitespace normalization: `validatePhrase("  abandon   abandon ... about  ")` (extra spaces, leading/trailing) still returns `Valid`.
   - Mixed case: `validatePhrase("ABANDON abandon ... about")` returns `Valid` (case-insensitive lookup).
   - Empty input: `validatePhrase("")` → `InvalidLength(0)` (0 is not in {12,15,18,21,24}; no unknown words because there are no words).
2. Run the two gates.

### Phase 4 — Final portability sanity check

Steps:
1. Run `./gradlew :wallet-utils:compileKotlinIosX64` once more (already done per phase, but mark as the final gate).
2. Run `./gradlew :wallet-utils:allTests` once more.
3. Quick visual scan of `Bip39Wordlist.kt` for any `java.*` / `android.*` import, any `System.*`, `kotlin.random.Random`, or `Thread.*` usage. There should be none — the module is pure `kotlin.*`.

No phase 5 needed: there is no expect/actual surface and no downstream integration in scope.

## 4. Golden vectors

Source of truth: https://github.com/trezor/python-mnemonic/blob/master/vectors.json (referenced in the BIP-39 spec and reproduced by essentially every BIP-39 implementation).

Vector used (all-zero entropy, English):

| Field | Value |
|-------|-------|
| entropy | `00000000000000000000000000000000` |
| mnemonic (12 words) | `abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about` |
| expected `validatePhrase` result | `PhraseValidation.Valid` |

This is the only vector this task asserts as "golden" because the task validates **wordlist membership + length**, not checksum or seed derivation. Checksum/seed vectors from the same file are intentionally out of scope (see §6 of the spec and Non-goals below). No invented vectors.

## 5. Crypto hygiene — CLAUDE.md §4 applied

- **§4.1 Never log / toString sensitive material.** The input phrase is a user-supplied mnemonic. `PhraseValidation` subtypes carry only integer indices and counts by design. Each subtype gets an explicit `override fun toString()` returning a constant-shape string (`"UnknownWords(indices=[1, 3])"`, `"InvalidLength(count=13)"`, `"Valid"`) so any future field addition cannot leak words. Acceptance §11 enforces this with a test.
- **§4.2 SecureRandom, never Random.** N/A — this task performs no randomness. Do **not** introduce any PRNG.
- **§4.3 Constant-time comparisons for secrets.** N/A — the wordlist is public. `binarySearch` over a 2048-entry sorted list is correct. A KDoc on `BIP39_ENGLISH` spells this out so a future reviewer does not "harden" it into a leaky linear scan.
- **§4.4 No Random for nonces.** N/A.
- **§4.5 Golden vectors.** Task is validation, not signing. The one vector in §4 above is the applicable equivalent.
- **§4.6 Never weaken an existing assertion.** If a test fails during implementation, fix the code.
- **§4.7 Address casing.** N/A.
- **§4.8 Serialization of transactions.** N/A.

## 6. Risks / Open questions

1. **Pasting 2048 words by hand is error-prone.** The implementer should copy from the canonical source (`english.txt` from the bitcoin/bips repo) in one operation. The Phase 1 size/sort/dup/lowercase tests exist specifically to catch paste accidents. If any Phase 1 test fails, stop and re-paste — do not "fix" individual words.
2. **`contains("abandon ")` returning `false`.** Achieved by treating any whitespace in the candidate as a disqualifier (spec §6 wants a trailing-space example to be `false`). Documented in the `contains` KDoc to prevent a future "just trim it" change that would break acceptance §6.
3. **`validatePhrase("")` semantics.** Spec does not state this explicitly. This plan treats it as `InvalidLength(0)`. If the implementer would rather return `InvalidLength(0)` vs. a new `Empty` sentinel, keep the former — it costs no new API surface and matches the general rule "no unknown words + bad length = InvalidLength".
4. **Non-ASCII input handling.** English wordlist is ASCII; the plan does no NFKD normalization. A phrase containing non-ASCII characters will simply miss the binary search and show up as `UnknownWords`. Good enough for this task; re-open when non-English wordlists land.
5. **No checksum validation.** A phrase like `"abandon abandon ... abandon abandon"` (12 "abandon"s, no "about") will currently return `Valid` even though it has an invalid BIP-39 checksum. This is deliberate and matches spec §Non-goals — but it means `Valid` here does **not** mean "this is a usable mnemonic". Document this clearly in the `validatePhrase` KDoc so downstream callers don't over-trust the result.

## 7. What this plan does NOT do

- Does not implement mnemonic generation.
- Does not implement BIP-39 checksum validation (would require SHA-256 over entropy; out of scope).
- Does not support any wordlist other than English.
- Does not perform UTF-8 NFKD normalization.
- Does not touch `wallet-core`, `wallet-evm`, `wallet-rpc`.
- Does not integrate with `Wallet.fromMnemonic` or any other consumer.
- Does not add a new library to `gradle/libs.versions.toml` (hot file — none needed anyway; pure Kotlin stdlib).
- Does not change `build.gradle.kts` in `wallet-utils` (no new deps, no new source sets).
- Does not add sample-app or iosApp usage.
- Does not run `./gradlew build` or any multi-module task — only the two `:wallet-utils:*` gates listed above.
