package xyz.wallet.toolkit.core

/**
 * High-level entry point for applications that want a small, explicit wallet
 * surface before dropping down to module-specific helpers such as wallet-evm
 * transaction builders or wallet-rpc network calls.
 */
class WalletKit private constructor(
    private val engine: WalletEngine,
    val supportedChains: List<SupportedChain>,
) {
    fun createWallet(): Wallet {
        return Wallet.create(engine = engine)
    }

    fun importWallet(mnemonic: String): Wallet {
        require(mnemonic.isNotBlank()) { "Mnemonic must not be blank" }
        return Wallet.fromMnemonic(mnemonic = mnemonic, engine = engine)
    }

    fun address(wallet: Wallet, chain: SupportedChain): String {
        require(chain in supportedChains) {
            "Unsupported chain ${chain.displayName} (${chain.id})"
        }
        return wallet.address(chain)
    }

    fun signTransaction(
        wallet: Wallet,
        chain: SupportedChain,
        transaction: Transaction,
    ): ByteArray {
        require(chain in supportedChains) {
            "Unsupported chain ${chain.displayName} (${chain.id})"
        }
        return wallet.signTransaction(chain = chain, transaction = transaction)
    }

    fun signEip1559Payload(
        wallet: Wallet,
        chain: SupportedChain,
        signingPayloadJson: ByteArray,
    ): ByteArray {
        require(chain in supportedChains) {
            "Unsupported chain ${chain.displayName} (${chain.id})"
        }
        return wallet.signEip1559Transaction(
            chain = chain,
            signingPayloadJson = signingPayloadJson,
        )
    }

    companion object {
        fun withEngine(
            engine: WalletEngine,
            supportedChains: List<SupportedChain> = ChainRegistry.all(),
        ): WalletKit {
            require(supportedChains.isNotEmpty()) { "WalletKit requires at least one supported chain" }
            return WalletKit(
                engine = engine,
                supportedChains = supportedChains.distinctBy { it.id },
            )
        }

        fun trustWalletCore(
            supportedChains: List<SupportedChain> = ChainRegistry.all(),
        ): WalletKit {
            return withEngine(
                engine = TrustWalletCoreWalletEngine(),
                supportedChains = supportedChains,
            )
        }
    }
}
