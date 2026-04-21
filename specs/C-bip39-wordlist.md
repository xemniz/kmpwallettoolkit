# Spec C — BIP-39 English wordlist validation

## Goal
Add a pure-Kotlin BIP-39 English wordlist validator in `wallet-utils`. Given a candidate mnemonic phrase (space-separated words), return whether every word appears in the canonical BIP-39 English wordlist. This is **validation**, not generation; entropy and key derivation stay with Trust Wallet Core.

## Module(s) touched
- `wallet-utils` — only.

This is the "safe ballast" task for the Phase 4 parallel run. It does not touch any hot file, any expect/actual seam, or any other module.

## Files expected to change
- `wallet-utils/src/commonMain/kotlin/xyz/wallet/toolkit/utils/Bip39Wordlist.kt` (new — the canonical 2048-word list as an `internal val` array, plus a `Bip39Wordlist` object with validation methods)
- `wallet-utils/src/commonTest/kotlin/xyz/wallet/toolkit/utils/Bip39WordlistTest.kt` (new)

No other file. No test for `jvmMain`/`androidMain`/`iosMain` — the test is pure commonTest, runs on every platform.

**Hot files (§7):** none touched.

## Design

- `internal val BIP39_ENGLISH: List<String>` — exactly 2048 words, lowercase, sorted, taken from the canonical BIP-39 English wordlist (https://github.com/bitcoin/bips/blob/master/bip-0039/english.txt). Store as a single `List<String>` initialized from a `listOf(...)` literal; do **not** load from a resource file — keep it hermetic, commonMain has no resource loader.
- `object Bip39Wordlist`:
  - `fun contains(word: String): Boolean` — case-insensitive check (normalize to lowercase, then binary search on the sorted list).
  - `fun validatePhrase(phrase: String): PhraseValidation` — splits on whitespace, checks every word. Returns a sealed result:
    - `PhraseValidation.Valid` (object) when every word is in the list and the word count is one of 12/15/18/21/24 (the BIP-39 valid lengths).
    - `PhraseValidation.UnknownWords(indices: List<Int>)` — the 0-based positions of words not in the list.
    - `PhraseValidation.InvalidLength(count: Int)` — returned when every word is valid but the count is not in {12,15,18,21,24}.
  - Precedence: `UnknownWords` wins over `InvalidLength` when both apply.
- Normalization: trim the input phrase, collapse runs of whitespace to a single space, lowercase each word before lookup. Do **not** modify the returned values.

### Crypto hygiene — CLAUDE.md §4
This task handles mnemonic **input** (user-supplied). Every rule in §4 applies, with emphasis on:
- **Rule 1 — never toString mnemonic material.** The `PhraseValidation` types must not print the phrase or any word from it in `toString()`. `UnknownWords.indices` is positions only — acceptable. Override `PhraseValidation.toString()` on the outer sealed type to avoid the default data-class rendering (which for UnknownWords would be safe, but be explicit).
- **Rule 3 — constant-time comparisons for secrets.** Wordlist lookup is not a secret comparison (the wordlist is public), so `binarySearch` is fine. Document this in a one-line KDoc so a future maintainer doesn't "optimize" it into something leaky.

## Acceptance criteria

1. `./gradlew :wallet-utils:allTests` — green.
2. `./gradlew :wallet-utils:compileKotlinJvm` — green.
3. `./gradlew :wallet-utils:compileKotlinIosX64` — green (wallet-utils has iOS targets; rule out any iOS-hostile API).
4. `BIP39_ENGLISH.size == 2048`, first word is `"abandon"`, last word is `"zoo"`. Assert these in a test. (These are the real BIP-39 boundary words.)
5. The list is sorted — assert `BIP39_ENGLISH == BIP39_ENGLISH.sorted()`.
6. `contains("abandon")`, `contains("ABANDON")`, `contains("zoo")` all return `true`. `contains("")`, `contains("notaword")`, `contains("abandon ")` (trailing space) all return `false`.
7. `validatePhrase("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about")` returns `Valid`. (This is the official BIP-39 test vector for "all-zero entropy" — assert it.)
8. `validatePhrase` returns `UnknownWords([1, 3])` for a phrase with bad words at those positions, valid words elsewhere, and a valid length.
9. `validatePhrase` returns `InvalidLength(13)` for a 13-word phrase of valid words.
10. `UnknownWords` wins when both unknown-word and invalid-length conditions are present.
11. `PhraseValidation` types (or the sealed parent) do not reveal phrase content in `toString()` — assert with a test that `"abandon" !in validation.toString()` for an invalid phrase.

## Non-goals
- Mnemonic **generation** (needs entropy — out of scope; delegates to Trust Wallet Core).
- Checksum validation (requires computing the BIP-39 checksum over entropy — out of scope for a wordlist validator).
- Non-English wordlists (Japanese, Chinese simplified/traditional, French, Italian, Korean, Spanish, Czech, Portuguese). Possible future spec.
- UTF-8 NFKD normalization. Real BIP-39 requires NFKD for non-ASCII wordlists; the English list is ASCII so we skip it; re-open when adding non-English.
- Integration with `Wallet.fromMnemonic` — that's downstream.

## Why this is in Phase 4
Safe ballast. Zero hot files. Zero platform coupling. If two other Phase-4 agents fight over `Chain.kt`, this one runs undisturbed — the control case for "does the friction scale per task or per contended-file?"
