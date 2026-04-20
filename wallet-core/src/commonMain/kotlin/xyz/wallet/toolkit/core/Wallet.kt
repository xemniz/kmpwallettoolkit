package xyz.wallet.toolkit.core

class Wallet private constructor(
    val mnemonic: String,
    private val engine: WalletEngine,
) {
    fun address(chain: SupportedChain): String = engine.deriveAddress(mnemonic, chain)

    fun signTransaction(chain: SupportedChain, transaction: Transaction): ByteArray {
        return engine.signTransaction(mnemonic = mnemonic, chain = chain, transaction = transaction)
    }

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

