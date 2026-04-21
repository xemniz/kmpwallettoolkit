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
}
