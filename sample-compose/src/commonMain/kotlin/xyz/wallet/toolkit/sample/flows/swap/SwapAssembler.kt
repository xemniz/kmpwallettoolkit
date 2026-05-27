package xyz.wallet.toolkit.sample.flows.swap

import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.core.Wallet
import xyz.wallet.toolkit.evm.Eip1559Transaction
import xyz.wallet.toolkit.evm.signEip1559Transaction
import xyz.wallet.toolkit.rpc.RpcCall
import xyz.wallet.toolkit.rpc.RpcClient
import xyz.wallet.toolkit.rpc.RpcException

/**
 * Orchestrates the execution side of a swap:
 *
 *  1. If the sell token is an ERC-20, call `allowance(owner, spender)` via
 *     `eth_call`. If current allowance < required sell amount, build and
 *     broadcast an `approve(spender, MAX_UINT256)` transaction first, then
 *     continue.
 *  2. Sign and broadcast the swap transaction returned by 0x.
 *
 * Secrecy contract (CLAUDE.md §4.1): never logs or interpolates the
 * mnemonic, signing payload, raw signed bytes, or node error messages into
 * user-facing strings. All failure messages are fixed literals.
 *
 * Nonce contract (CLAUDE.md §4.4): fetches a fresh `eth_getTransactionCount`
 * before each broadcast. If an approve is needed, the swap nonce is the
 * approve nonce + 1 — broadcast order matters, and we rely on the RPC
 * mempool to accept the swap after the approve lands. We do not wait for
 * the approve receipt; nodes accept pending-nonce txs.
 */
class SwapAssembler(
    private val wallet: Wallet,
    private val rpc: RpcClient,
    private val chain: SupportedChain,
) {

    suspend fun execute(quote: SwapQuote): SwapResult {
        val owner = wallet.address(chain).lowercase()

        var nonce: Long = try {
            parseHexLong(rpc.getNonce(owner, "pending"))
                ?: return SwapResult.Failure(SwapFailure.NonceFetchFailed, "Could not fetch account nonce.")
        } catch (_: RpcException) {
            return SwapResult.Failure(SwapFailure.NonceFetchFailed, "Could not fetch account nonce.")
        } catch (_: Throwable) {
            return SwapResult.Failure(SwapFailure.NonceFetchFailed, "Could not fetch account nonce.")
        }

        // Step 1: ERC-20 approval if needed.
        if (!quote.sell.isNative) {
            val sellAddress = quote.sell.address
                ?: return SwapResult.Failure(SwapFailure.InvalidQuote, "Invalid token.")
            val spender = quote.allowanceIssue?.spender ?: quote.transaction.to

            val currentAllowance = try {
                readAllowance(sellAddress, owner, spender)
            } catch (_: Throwable) {
                return SwapResult.Failure(SwapFailure.AllowanceReadFailed, "Could not read allowance.")
            }

            if (compareDecimal(currentAllowance, quote.sellAmountRaw) < 0) {
                val approveTx = Eip1559Transaction(
                    chainId = chain.id,
                    to = sellAddress,
                    valueWei = "0",
                    maxFeePerGasWei = defaultMaxFeeFor(chain),
                    maxPriorityFeePerGasWei = defaultPriorityFor(chain),
                    gasLimit = "80000",
                    nonce = nonce,
                    dataHex = Erc20.approveCallData(spender, Erc20.MAX_UINT256_DECIMAL),
                    accessList = emptyList(),
                )
                val signed = try {
                    wallet.signEip1559Transaction(chain, approveTx)
                } catch (_: Throwable) {
                    return SwapResult.Failure(SwapFailure.SigningFailed, "Signing approve failed.")
                }
                try {
                    rpc.sendRawTransaction(bytesToHex0x(signed))
                } catch (e: RpcException) {
                    return SwapResult.Failure(SwapFailure.BroadcastFailed(e.code), "Approve broadcast failed (code ${e.code}).")
                } catch (_: Throwable) {
                    return SwapResult.Failure(SwapFailure.UnexpectedError, "Something went wrong.")
                }
                nonce += 1
            }
        }

        // Step 2: the swap transaction.
        val swapTx = Eip1559Transaction(
            chainId = chain.id,
            to = quote.transaction.to.lowercase(),
            valueWei = quote.transaction.valueWei,
            maxFeePerGasWei = defaultMaxFeeFor(chain),
            maxPriorityFeePerGasWei = defaultPriorityFor(chain),
            gasLimit = quote.transaction.gasLimit,
            nonce = nonce,
            dataHex = quote.transaction.dataHex,
            accessList = emptyList(),
        )

        val signed = try {
            wallet.signEip1559Transaction(chain, swapTx)
        } catch (_: Throwable) {
            return SwapResult.Failure(SwapFailure.SigningFailed, "Signing swap failed.")
        }

        val txHash = try {
            rpc.sendRawTransaction(bytesToHex0x(signed))
        } catch (e: RpcException) {
            return SwapResult.Failure(SwapFailure.BroadcastFailed(e.code), "Broadcast failed (code ${e.code}).")
        } catch (_: Throwable) {
            return SwapResult.Failure(SwapFailure.UnexpectedError, "Something went wrong.")
        }

        return SwapResult.Success(txHash)
    }

    private suspend fun readAllowance(token: String, owner: String, spender: String): String {
        val data = Erc20.allowanceCallData(owner, spender)
        val hex = rpc.ethCall(RpcCall(to = token, data = data), "latest")
        return Erc20.decodeUint256Decimal(hex) ?: "0"
    }
}

/**
 * Parse a `0x`-prefixed hex integer as a Long. Returns null on malformed
 * input so callers can surface a fixed user message without echoing the
 * raw node response.
 */
internal fun parseHexLong(raw: String): Long? {
    if (raw.isEmpty()) return null
    val stripped = when {
        raw.startsWith("0x") || raw.startsWith("0X") -> raw.substring(2)
        else -> return null
    }
    if (stripped.isEmpty()) return null
    return try {
        stripped.toLong(radix = 16)
    } catch (_: NumberFormatException) {
        null
    }
}

private val HEX_CHARS = "0123456789abcdef"

private fun bytesToHex0x(bytes: ByteArray): String {
    val chars = CharArray(bytes.size * 2)
    for (i in bytes.indices) {
        val v = bytes[i].toInt() and 0xFF
        chars[i * 2] = HEX_CHARS[v ushr 4]
        chars[i * 2 + 1] = HEX_CHARS[v and 0x0F]
    }
    return "0x" + chars.concatToString()
}

// Rough, per-chain defaults. Base is cheap; Ethereum mainnet is not. These
// are showcase-grade — a production app would pull from eth_feeHistory.
private fun defaultMaxFeeFor(chain: SupportedChain): String = when (chain) {
    SupportedChain.Base -> "200000000"         // 0.2 gwei
    else -> "30000000000"                      // 30 gwei
}

private fun defaultPriorityFor(chain: SupportedChain): String = when (chain) {
    SupportedChain.Base -> "100000000"         // 0.1 gwei
    else -> "1500000000"                       // 1.5 gwei
}

sealed class SwapFailure {
    object InvalidQuote : SwapFailure()
    object NonceFetchFailed : SwapFailure()
    object AllowanceReadFailed : SwapFailure()
    object SigningFailed : SwapFailure()
    data class BroadcastFailed(val rpcCode: Int) : SwapFailure()
    object UnexpectedError : SwapFailure()
}

sealed class SwapResult {
    data class Success(val txHash: String) : SwapResult()
    data class Failure(val kind: SwapFailure, val userMessage: String) : SwapResult()
}
