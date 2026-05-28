package xyz.wallet.toolkit.core

class Wallet private constructor(
    private val mnemonic: String,
    private val engine: WalletEngine,
) {
    fun address(chain: SupportedChain): String = engine.deriveAddress(mnemonic, chain)

    /**
     * Returns the BIP-39 mnemonic for host-side secure storage or an explicit
     * user backup flow. Never log or interpolate this value into errors.
     */
    fun exportMnemonic(): String = mnemonic

    fun signTransaction(chain: SupportedChain, transaction: Transaction): ByteArray {
        return engine.signTransaction(mnemonic = mnemonic, chain = chain, transaction = transaction)
    }

    /**
     * Sign an EIP-1559 (type-2) transaction already serialized via
     * `wallet-evm`'s `Eip1559Transaction.toSigningPayload()`. The extension
     * `Wallet.signEip1559Transaction(chain, tx)` in wallet-evm is the typed
     * entry point; this method is the module-boundary seam it delegates to.
     */
    fun signEip1559Transaction(chain: SupportedChain, signingPayloadJson: ByteArray): ByteArray {
        return engine.signEip1559(
            mnemonic = mnemonic,
            chain = chain,
            signingPayloadJson = signingPayloadJson,
        )
    }

    override fun toString(): String = "Wallet(redacted)"

    companion object {
        fun create(engine: WalletEngine = UnsupportedWalletEngine()): Wallet {
            val mnemonic = engine.createMnemonic()
            return Wallet(mnemonic = mnemonic, engine = engine)
        }

        fun createWithTrustWalletCore(): Wallet {
            return create(engine = TrustWalletCoreWalletEngine())
        }

        fun fromMnemonic(
            mnemonic: String,
            engine: WalletEngine = UnsupportedWalletEngine(),
        ): Wallet {
            return Wallet(mnemonic = mnemonic, engine = engine)
        }

        fun fromMnemonicWithTrustWalletCore(mnemonic: String): Wallet {
            return fromMnemonic(mnemonic = mnemonic, engine = TrustWalletCoreWalletEngine())
        }
    }
}
