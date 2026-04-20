package xyz.wallet.toolkit.evm

import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.core.Wallet

fun Wallet.signEvmTransaction(
    chain: SupportedChain,
    transaction: EvmTransaction,
): ByteArray {
    require(chain.id == transaction.chainId) {
        "Chain mismatch: wallet chain ${chain.id} differs from transaction chain ${transaction.chainId}"
    }
    return signTransaction(chain = chain, transaction = transaction)
}

