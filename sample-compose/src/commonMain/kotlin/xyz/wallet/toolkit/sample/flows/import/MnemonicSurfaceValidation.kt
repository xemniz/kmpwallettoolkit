package xyz.wallet.toolkit.sample.flows.import

/** Word-count validation for the form; native wallet import validates BIP-39 correctness. */
sealed interface ValidationResult {
    data object Empty : ValidationResult
    data class WrongWordCount(val count: Int) : ValidationResult
    data class Valid(val words: List<String>) : ValidationResult {
        override fun toString(): String = "Valid(redacted)"
    }
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
