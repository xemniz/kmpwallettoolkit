package xyz.wallet.toolkit.evm

import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.core.Wallet

/**
 * Typed entry point for signing an EIP-1559 (type-2) transaction with the given
 * [chain]. The transaction is serialized via [Eip1559Transaction.toSigningPayload]
 * and forwarded to the wallet-core engine seam.
 *
 * @throws IllegalArgumentException if [tx].chainId does not match [chain].id.
 */
fun Wallet.signEip1559Transaction(
    chain: SupportedChain,
    tx: Eip1559Transaction,
): ByteArray {
    require(chain.id == tx.chainId) {
        "Chain mismatch: wallet chain ${chain.id} differs from transaction chain ${tx.chainId}"
    }
    return signEip1559Transaction(
        chain = chain,
        signingPayloadJson = tx.toSigningPayload(),
    )
}
