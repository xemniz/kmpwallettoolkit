package xyz.wallet.toolkit.sample.flows.import

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MnemonicSurfaceValidationTest {
    @Test
    fun mnemonicValidationDoesNotExposeWordsInItsStringRepresentation() {
        val result = validate("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("Valid(redacted)", result.toString())
    }
}
