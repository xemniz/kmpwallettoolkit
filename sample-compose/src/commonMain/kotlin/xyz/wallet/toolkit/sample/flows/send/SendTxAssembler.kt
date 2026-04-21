package xyz.wallet.toolkit.sample.flows.send

import xyz.wallet.toolkit.core.SupportedChain
import xyz.wallet.toolkit.core.Wallet
import xyz.wallet.toolkit.evm.Eip1559Transaction
import xyz.wallet.toolkit.evm.signEip1559Transaction
import xyz.wallet.toolkit.rpc.RpcClient
import xyz.wallet.toolkit.rpc.RpcException
import xyz.wallet.toolkit.sample.format.EthFormat
import xyz.wallet.toolkit.sample.nav.Navigator
import xyz.wallet.toolkit.sample.nav.Route

/**
 * Nonce → build → sign → broadcast orchestrator for a single Send submission.
 *
 * Secrecy contract (CLAUDE.md §4.1):
 *  - Never logs (no `println` / `Log.*`) and never interpolates into
 *    exception messages: the mnemonic, the wallet address, the signing
 *    payload JSON, the signed `ByteArray`, the `0x`-prefixed raw hex, or
 *    the node's nonce response.
 *  - All `Failure.userMessage` strings are fixed literals from the spec
 *    (§Error handling). `RpcException.message` is never passthrough'd —
 *    it may echo our submitted raw hex.
 *
 * Nonce contract (CLAUDE.md §4.4):
 *  - Every `assembleAndBroadcast` call fetches a fresh nonce via
 *    `eth_getTransactionCount` with `blockTag = "latest"`. No caching,
 *    no PRNG fallback.
 */
class SendTxAssembler(
    private val wallet: Wallet,
    private val rpc: RpcClient,
    private val chain: SupportedChain,
    private val navigator: Navigator,
) {

    suspend fun assembleAndBroadcast(state: SendState): SendResult {
        // Defensive re-validate (UI gates already).
        val recipient = state.recipientNormalized
            ?: return SendResult.Failure(FailureKind.InvalidRecipient, "Recipient address is not valid.")
        if (validateRecipient(recipient) != ValidationResult.Valid) {
            return SendResult.Failure(FailureKind.InvalidRecipient, "Recipient address is not valid.")
        }

        val valueWei = try {
            EthFormat.ethDecimalToWei(state.amountEth)
        } catch (_: IllegalArgumentException) {
            return SendResult.Failure(FailureKind.InvalidAmount, "Amount is not valid.")
        }

        val maxFeeWei = try {
            EthFormat.gweiToWei(state.maxFeeGwei)
        } catch (_: IllegalArgumentException) {
            return SendResult.Failure(FailureKind.InvalidGas, "Gas values are not valid.")
        }

        val priorityWei = try {
            EthFormat.gweiToWei(state.maxPriorityGwei)
        } catch (_: IllegalArgumentException) {
            return SendResult.Failure(FailureKind.InvalidGas, "Gas values are not valid.")
        }

        val address = wallet.address(chain).lowercase()

        val nonce: Long = try {
            val nonceHex = rpc.getNonce(address, "latest")
            parseNonceHex(nonceHex)
                ?: return SendResult.Failure(FailureKind.NonceFetchFailed, "Could not fetch account nonce. Try again.")
        } catch (_: RpcException) {
            return SendResult.Failure(FailureKind.NonceFetchFailed, "Could not fetch account nonce. Try again.")
        } catch (_: Throwable) {
            return SendResult.Failure(FailureKind.NonceFetchFailed, "Could not fetch account nonce. Try again.")
        }

        val tx = Eip1559Transaction(
            chainId = chain.id,
            to = recipient,
            valueWei = valueWei,
            maxFeePerGasWei = maxFeeWei,
            maxPriorityFeePerGasWei = priorityWei,
            gasLimit = "21000",
            nonce = nonce,
            dataHex = null,
            accessList = emptyList(),
        )

        val signed: ByteArray = try {
            wallet.signEip1559Transaction(chain, tx)
        } catch (_: Throwable) {
            // Do NOT interpolate the underlying exception message — TWC may
            // echo the signing payload (CLAUDE.md §4.1).
            return SendResult.Failure(FailureKind.SigningFailed, "Signing failed.")
        }

        val rawHex = bytesToHex(signed)

        val txHash = try {
            rpc.sendRawTransaction(rawHex)
        } catch (e: RpcException) {
            return SendResult.Failure(
                FailureKind.BroadcastFailed(e.code),
                "Broadcast failed (code ${e.code}).",
            )
        } catch (_: Throwable) {
            return SendResult.Failure(FailureKind.UnexpectedError, "Something went wrong.")
        }

        navigator.replace(Route.TxStatus(txHash = txHash, chainId = chain.id))
        return SendResult.Success(txHash)
    }
}

/**
 * Parses a `0x`-prefixed hex nonce string from `eth_getTransactionCount`.
 * Returns `null` (rather than throwing) on malformed input so the caller can
 * map to `NonceFetchFailed` without ever interpolating the raw input into a
 * user-facing or log string.
 *
 * Accepts `"0x0"` → `0L`. Rejects `""`, `"0x"`, and non-hex characters.
 */
internal fun parseNonceHex(raw: String): Long? {
    if (raw.isEmpty()) return null
    val stripped = when {
        raw.startsWith("0x") || raw.startsWith("0X") -> raw.substring(2)
        else -> return null
    }
    if (stripped.isEmpty()) return null
    if (!stripped.all { it.isHexChar() }) return null
    return try {
        stripped.toLong(radix = 16)
    } catch (_: NumberFormatException) {
        null
    }
}

private fun Char.isHexChar(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

/**
 * Inline hex encoder. `wallet-utils` is pulled via `implementation(...)` in
 * `wallet-core` / `wallet-evm`, so `xyz.wallet.toolkit.utils.toHexString` is
 * not transitively visible from `sample-compose`. Per S5 scope, editing
 * `build.gradle.kts` is forbidden — this 6-line encoder is the documented
 * fallback (plan §7.1).
 */
private val HEX_CHARS = "0123456789abcdef"

private fun bytesToHex(bytes: ByteArray): String {
    val chars = CharArray(bytes.size * 2)
    for (i in bytes.indices) {
        val v = bytes[i].toInt() and 0xFF
        chars[i * 2] = HEX_CHARS[v ushr 4]
        chars[i * 2 + 1] = HEX_CHARS[v and 0x0F]
    }
    return "0x" + chars.concatToString()
}

sealed class FailureKind {
    object InvalidRecipient : FailureKind()
    object InvalidAmount : FailureKind()
    object InvalidGas : FailureKind()
    object NonceFetchFailed : FailureKind()
    object SigningFailed : FailureKind()
    data class BroadcastFailed(val rpcCode: Int) : FailureKind()
    object UnexpectedError : FailureKind()
}

sealed class SendResult {
    data class Success(val txHash: String) : SendResult()
    data class Failure(val kind: FailureKind, val userMessage: String) : SendResult()
}
