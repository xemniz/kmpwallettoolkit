package xyz.wallet.toolkit.utils

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
