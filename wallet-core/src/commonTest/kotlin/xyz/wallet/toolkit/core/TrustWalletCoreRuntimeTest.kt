package xyz.wallet.toolkit.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class TrustWalletCoreRuntimeTest {
    @AfterTest
    fun tearDown() {
        TrustWalletCoreRuntime.clearIosAdapter()
    }

    @Test
    fun requiresInstalledIosAdapter() {
        assertFailsWith<NotImplementedError> {
            TrustWalletCoreRuntime.requireIosAdapter()
        }
    }

    @Test
    fun returnsInstalledIosAdapter() {
        val adapter = FakeIosAdapter()

        TrustWalletCoreRuntime.installIosAdapter(adapter)

        assertSame(adapter, TrustWalletCoreRuntime.requireIosAdapter())
    }
}

private class FakeIosAdapter : TrustWalletCoreIosAdapter {
    override fun createMnemonic(): String = "mnemonic"

    override fun deriveAddress(mnemonic: String, chain: SupportedChain): String = "0xabc"

    override fun signEvmTransaction(
        mnemonic: String,
        chain: SupportedChain,
        transaction: TrustWalletCoreEvmSigningRequest,
    ): String = "0x01"
}
