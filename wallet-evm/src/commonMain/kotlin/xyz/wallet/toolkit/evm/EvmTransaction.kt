package xyz.wallet.toolkit.evm

import kotlinx.serialization.Serializable
import xyz.wallet.toolkit.core.EvmTransactionData

@Serializable
data class EvmTransaction(
    override val chainId: Long,
    override val to: String,
    override val valueWei: String = "0",
    override val gasPriceWei: String,
    override val gasLimit: String,
    override val nonce: Long,
    override val dataHex: String? = null,
) : EvmTransactionData

