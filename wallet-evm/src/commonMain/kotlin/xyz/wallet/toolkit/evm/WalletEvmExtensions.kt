package xyz.wallet.toolkit.evm

import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.core.Wallet
import xyz.wallet.toolkit.utils.hexToByteArray

fun Wallet.signEvmTransaction(
    chain: SupportedChain,
    transaction: EvmTransaction,
): ByteArray {
    require(chain.id == transaction.chainId) {
        "Chain mismatch: wallet chain ${chain.id} differs from transaction chain ${transaction.chainId}"
    }
    transaction.requireValidQuantities()
    transaction.dataHex?.hexToByteArray()
    return signTransaction(chain = chain, transaction = transaction)
}

