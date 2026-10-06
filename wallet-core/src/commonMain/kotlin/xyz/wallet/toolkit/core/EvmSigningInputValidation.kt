package xyz.wallet.toolkit.core

import xyz.wallet.toolkit.utils.hexToByteArray
import xyz.wallet.toolkit.utils.requireUnsignedDecimal

internal fun EvmTransactionData.requireValidSigningInput(chain: SupportedChain) {
    require(chainId == chain.id) { "Transaction chain ID does not match the signing chain" }
    require(nonce >= 0) { "Nonce must not be negative" }
    valueWei.requireUnsignedDecimal()
    gasPriceWei.requireUnsignedDecimal()
    gasLimit.requireUnsignedDecimal()
    dataHex?.hexToByteArray()
}
