package xyz.wallet.toolkit.sample.flows.import

/**
 * Pure, stateless surface-level validation for pasted mnemonics.
 *
 * Intentionally NOT a dictionary or checksum check. BIP-39 correctness is the
 * toolkit's responsibility via [xyz.wallet.toolkit.core.Wallet.fromMnemonicWithTrustWalletCore].
 * This file exists only to keep the "Restore" CTA disabled until the user's
 * input has a plausible BIP-39 word count.
 *
 * CLAUDE.md §4.1: callers of [validate] MUST NOT log the returned
 * [ValidationResult.Valid.words] or the joined phrase derived from them.
 */
sealed interface ValidationResult {
    data object Empty : ValidationResult
    data class WrongWordCount(val count: Int) : ValidationResult
    data class Valid(val words: List<String>) : ValidationResult
}

private val WHITESPACE = Regex("\\s+")
private val VALID_WORD_COUNTS = setOf(12, 15, 18, 21, 24)

fun validate(input: String): ValidationResult {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return ValidationResult.Empty
    val words = trimmed.split(WHITESPACE).map { it.lowercase() }
    return if (words.size in VALID_WORD_COUNTS) {
        ValidationResult.Valid(words)
    } else {
        ValidationResult.WrongWordCount(words.size)
    }
}
