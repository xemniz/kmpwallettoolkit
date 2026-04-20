package xyz.wallet.toolkit.sample

import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.core.TrustWalletCoreEvmSigningRequest
import xyz.wallet.toolkit.core.TrustWalletCoreIosAdapter

/**
 * Mock adapter for demo/testing on iOS when the real Trust Wallet Core
 * iOS SDK is not integrated. Returns deterministic test-vector data.
 */
class MockTrustWalletCoreAdapter : TrustWalletCoreIosAdapter {

    private var counter = 0

    private val testMnemonics = listOf(
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
        "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong",
        "letter advice cage absurd amount doctor acoustic avoid letter advice cage above",
    )

    private val testAddresses = listOf(
        "0x9858EfFD232B4033E47d90003D41EC34EcaEda94",
        "0xc2D3aE722B36E2E3eF7fCe28b6A083D0F894136E",
        "0x107797F6e4A8b5E02C5AEcC6341A4342F3bB16aF",
    )

    override fun createMnemonic(): String {
        return testMnemonics[counter++ % testMnemonics.size]
    }

    override fun deriveAddress(mnemonic: String, chain: SupportedChain): String {
        val index = testMnemonics.indexOf(mnemonic).coerceAtLeast(0)
        return testAddresses[index % testAddresses.size]
    }

    override fun signEvmTransaction(
        mnemonic: String,
        chain: SupportedChain,
        transaction: TrustWalletCoreEvmSigningRequest,
    ): String {
        return "0x" + "ab".repeat(65) // mock 65-byte signed payload
    }
}

