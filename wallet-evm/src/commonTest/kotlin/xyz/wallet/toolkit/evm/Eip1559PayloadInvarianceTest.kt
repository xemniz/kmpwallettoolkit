package xyz.wallet.toolkit.evm

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Locks in the emit-defaulted-fields invariant for Eip1559Transaction.toSigningPayload.
 *
 * The canonical signing payload must always include `valueWei`, `accessList`, and
 * `dataHex` keys regardless of whether the caller populated them, because those
 * fields are inputs to the signed hash. If they were omitted when left at their
 * defaults, a constructed transaction and a fully-populated transaction with the
 * same defaults would hash differently across signer adapters.
 *
 * This is an invariance check, not a byte-for-byte drift guard — the latter is
 * `Eip1559GoldenVectorTest.goldenJsonDoesNotDrift`.
 */
class Eip1559PayloadInvarianceTest {
    @Test
    fun toSigningPayloadAlwaysEmitsDefaultedFields() {
        val minimal = Eip1559Transaction(
            chainId = 1,
            to = "0x3535353535353535353535353535353535353535",
            maxFeePerGasWei = "50000000000",
            maxPriorityFeePerGasWei = "1000000000",
            gasLimit = "21000",
            nonce = 42,
        )

        val populated = Eip1559Transaction(
            chainId = 1,
            to = "0x3535353535353535353535353535353535353535",
            valueWei = "1000000000000000000",
            maxFeePerGasWei = "50000000000",
            maxPriorityFeePerGasWei = "1000000000",
            gasLimit = "21000",
            nonce = 42,
            dataHex = "0xdeadbeef",
            accessList = listOf(
                AccessListEntry(
                    address = "0x1111111111111111111111111111111111111111",
                    storageKeys = listOf(
                        "0x0000000000000000000000000000000000000000000000000000000000000001",
                        "0x0000000000000000000000000000000000000000000000000000000000000002",
                    ),
                ),
                AccessListEntry(
                    address = "0x2222222222222222222222222222222222222222",
                    storageKeys = listOf(
                        "0x0000000000000000000000000000000000000000000000000000000000000003",
                    ),
                ),
            ),
        )

        val minimalJson = minimal.toSigningPayload().decodeToString()
        val populatedJson = populated.toSigningPayload().decodeToString()

        assertTrue(minimalJson.contains("\"valueWei\""), "minimal payload must emit valueWei: $minimalJson")
        assertTrue(minimalJson.contains("\"accessList\""), "minimal payload must emit accessList: $minimalJson")
        assertTrue(minimalJson.contains("\"dataHex\""), "minimal payload must emit dataHex: $minimalJson")

        assertTrue(populatedJson.contains("\"valueWei\""), "populated payload must emit valueWei: $populatedJson")
        assertTrue(populatedJson.contains("\"accessList\""), "populated payload must emit accessList: $populatedJson")
        assertTrue(populatedJson.contains("\"dataHex\""), "populated payload must emit dataHex: $populatedJson")
    }
}
