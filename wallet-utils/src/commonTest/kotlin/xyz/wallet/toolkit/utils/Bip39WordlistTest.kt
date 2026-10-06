package xyz.wallet.toolkit.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Bip39WordlistTest {

    // --- Phase 1: wordlist boundary / structural tests. ----------------------------------
    // Source: https://github.com/bitcoin/bips/blob/master/bip-0039/english.txt
    // If any of these fail, re-paste the full wordlist from the canonical source — do not
    // patch individual lines (that's how subtle typos survive).

    @Test
    fun `wordlist has exactly 2048 entries`() {
        assertEquals(2048, BIP39_ENGLISH.size)
    }

    @Test
    fun `first word is abandon`() {
        assertEquals("abandon", BIP39_ENGLISH.first())
    }

    @Test
    fun `last word is zoo`() {
        assertEquals("zoo", BIP39_ENGLISH.last())
    }

    @Test
    fun `wordlist is sorted ascending`() {
        assertEquals(BIP39_ENGLISH.sorted(), BIP39_ENGLISH)
    }

    @Test
    fun `wordlist has no duplicates`() {
        assertEquals(2048, BIP39_ENGLISH.toSet().size)
    }

    @Test
    fun `every word is lowercase`() {
        assertTrue(BIP39_ENGLISH.all { it == it.lowercase() })
    }

    // --- Phase 2: contains() + validatePhrase() happy path. ------------------------------

    @Test
    fun `contains accepts known words including boundaries`() {
        assertTrue(Bip39Wordlist.contains("abandon"))
        assertTrue(Bip39Wordlist.contains("zoo"))
    }

    @Test
    fun `contains is case-insensitive`() {
        assertTrue(Bip39Wordlist.contains("ABANDON"))
        assertTrue(Bip39Wordlist.contains("Abandon"))
    }

    @Test
    fun `contains rejects empty string`() {
        assertFalse(Bip39Wordlist.contains(""))
    }

    @Test
    fun `contains rejects unknown word`() {
        assertFalse(Bip39Wordlist.contains("notaword"))
    }

    @Test
    fun `contains rejects trailing whitespace`() {
        // Spec acceptance §6: "abandon " (trailing space) must be false. contains() does
        // not trim — callers that want phrase semantics should use validatePhrase().
        assertFalse(Bip39Wordlist.contains("abandon "))
    }

    @Test
    fun `validatePhrase returns Valid for BIP-39 all-zero-entropy vector`() {
        // Golden vector (BIP-39 English, entropy = 00000000000000000000000000000000):
        //   abandon abandon abandon abandon abandon abandon abandon abandon abandon
        //   abandon abandon about
        // Source: https://github.com/trezor/python-mnemonic/blob/master/vectors.json
        val phrase = "abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon about"
        assertEquals(PhraseValidation.Valid, Bip39Wordlist.validatePhrase(phrase))
    }

    // --- Phase 3: edge cases, error paths, redaction. ------------------------------------

    @Test
    fun `validatePhrase reports UnknownWords with positions of bad tokens`() {
        // 12 tokens total; positions 1 and 3 are garbage, all others are in the wordlist.
        val phrase = "abandon xxx abandon yyy abandon abandon " +
            "abandon abandon abandon abandon abandon about"
        assertEquals(
            PhraseValidation.UnknownWords(listOf(1, 3)),
            Bip39Wordlist.validatePhrase(phrase),
        )
    }

    @Test
    fun `validatePhrase reports InvalidLength for 13 valid words`() {
        // 12 "abandon" + "about" = 13 valid words, not in {12,15,18,21,24}.
        val phrase = (List(12) { "abandon" } + "about").joinToString(" ")
        assertEquals(
            PhraseValidation.InvalidLength(13),
            Bip39Wordlist.validatePhrase(phrase),
        )
    }

    @Test
    fun `UnknownWords takes precedence over InvalidLength`() {
        // 13 tokens and one unknown — UnknownWords must win.
        val phrase = (List(11) { "abandon" } + "xxx" + "about").joinToString(" ")
        assertEquals(
            PhraseValidation.UnknownWords(listOf(11)),
            Bip39Wordlist.validatePhrase(phrase),
        )
    }

    @Test
    fun `validatePhrase does not reveal phrase content in toString`() {
        // Validation results must never render the submitted phrase.
        // Include both a wordlist token ("abandon") and a distinctive non-wordlist token
        // ("zzzsecretzzz") to catch leaks from either side.
        val phrase = "abandon zzzsecretzzz abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon about"
        val result = Bip39Wordlist.validatePhrase(phrase).toString()
        assertFalse("abandon" in result, "toString leaked wordlist token: $result")
        assertFalse("zzzsecretzzz" in result, "toString leaked input token: $result")
    }

    @Test
    fun `validatePhrase normalizes leading trailing and collapsed whitespace`() {
        val phrase = "   abandon   abandon \tabandon\nabandon abandon abandon " +
            "abandon abandon abandon abandon abandon about   "
        assertEquals(PhraseValidation.Valid, Bip39Wordlist.validatePhrase(phrase))
    }

    @Test
    fun `validatePhrase is case-insensitive`() {
        val phrase = "ABANDON Abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon ABOUT"
        assertEquals(PhraseValidation.Valid, Bip39Wordlist.validatePhrase(phrase))
    }

    @Test
    fun `validatePhrase reports InvalidLength 0 for empty input`() {
        assertEquals(PhraseValidation.InvalidLength(0), Bip39Wordlist.validatePhrase(""))
        assertEquals(PhraseValidation.InvalidLength(0), Bip39Wordlist.validatePhrase("    "))
    }
}
